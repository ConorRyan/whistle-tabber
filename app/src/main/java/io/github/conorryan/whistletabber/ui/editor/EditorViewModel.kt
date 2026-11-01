package io.github.conorryan.whistletabber.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.conorryan.whistletabber.data.tunes.TuneRepository
import io.github.conorryan.whistletabber.music.AbcBook
import io.github.conorryan.whistletabber.music.AbcProblem
import io.github.conorryan.whistletabber.music.WhistleKey
import io.github.conorryan.whistletabber.music.problems
import io.github.conorryan.whistletabber.render.TunePage
import io.github.conorryan.whistletabber.render.TuneRenderer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditorUiState(
    /** Null while an existing tune is loading. */
    val text: String? = null,
    /** Why the text can't be saved yet; empty when it can. */
    val problems: List<AbcProblem> = emptyList(),
    /** The text differs from what was loaded (or the template). */
    val dirty: Boolean = false,
    val saving: Boolean = false,
    /** The tune being edited no longer exists. */
    val missing: Boolean = false,
    /** The tune's own `%whistle_tab_key` directive, if the text has one. */
    val explicitWhistle: WhistleKey? = null,
    /** The whistle the text's key suggests, or null when there is no clear choice. */
    val suggestedWhistle: WhistleKey? = null,
) {
    /** The whistle the tune is written for: its directive, else its key's suggestion. */
    val whistle: WhistleKey get() = explicitWhistle ?: suggestedWhistle ?: WhistleKey.DEFAULT

    val canSave: Boolean get() = text != null && problems.isEmpty() && !saving
}

sealed interface EditorEffect {
    data class Saved(val id: Long) : EditorEffect
}

/**
 * Edits one tune's ABC ([id] null: a new tune from [initialText], else
 * [TEMPLATE]; a new tune from [initialText] starts unsaved). The draft
 * lives in [SavedStateHandle], so it survives process death; the preview is
 * rendered by the [TuneRenderer] this ViewModel owns, [debounceMs] after
 * typing stops. The screen attaches its WebView with [bindPage].
 */
class EditorViewModel(
    private val id: Long?,
    private val savedState: SavedStateHandle,
    private val repository: TuneRepository,
    private val initialText: String? = null,
    debounceMs: Long = 400,
) : ViewModel() {

    private val renderer = TuneRenderer(viewModelScope, debounceMs)

    /** Parse warnings from the last preview render. */
    val renderState: StateFlow<TuneRenderer.RendererState> = renderer.state

    fun bindPage(page: TunePage) = renderer.attach(page)

    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    private val _effects = Channel<EditorEffect>(Channel.BUFFERED)
    val effects: Flow<EditorEffect> = _effects.receiveAsFlow()

    private var original: String? = null

    init {
        viewModelScope.launch {
            original = if (id == null) TEMPLATE else repository.get(id)?.abc
            if (original == null) {
                _state.update { it.copy(missing = true) }
                return@launch
            }
            setText(savedState.get<String>(KEY_DRAFT) ?: initialText?.takeIf { id == null } ?: original!!)
        }
    }

    fun setText(text: String) {
        savedState[KEY_DRAFT] = text
        val headers = AbcBook.headers(text)
        _state.update {
            it.copy(
                text = text,
                problems = headers.problems(),
                dirty = text != original,
                explicitWhistle = headers.whistle,
                suggestedWhistle = AbcBook.suggestWhistle(headers.key),
            )
        }
        renderer.show(TuneRenderer.RenderRequest(text))
    }

    /** Picks the whistle the tune is written for: [whistle] null removes the directive. */
    fun setWhistle(whistle: WhistleKey?) {
        val text = _state.value.text ?: return
        setText(AbcBook.withWhistle(text, whistle))
    }

    fun save() {
        val s = _state.value
        val text = s.text ?: return
        if (!s.canSave) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val savedId = repository.save(text, id)
            original = text
            savedState.remove<String>(KEY_DRAFT)
            _state.update { it.copy(saving = false, dirty = false) }
            _effects.send(EditorEffect.Saved(savedId))
        }
    }

    companion object {
        private const val KEY_DRAFT = "draft"

        const val TEMPLATE = "X:1\nT:\nR:reel\nM:4/4\nL:1/8\nK:D\n|:DEFG ABcd|edcB A2FA:|\n"

        fun problemsOf(text: String): List<AbcProblem> = AbcBook.headers(text).problems()
    }
}
