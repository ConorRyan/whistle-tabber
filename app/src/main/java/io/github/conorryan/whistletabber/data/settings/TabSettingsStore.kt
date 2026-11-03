package io.github.conorryan.whistletabber.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.conorryan.whistletabber.data.enumByName
import io.github.conorryan.whistletabber.music.FingeringChoices
import io.github.conorryan.whistletabber.music.NoteNames
import io.github.conorryan.whistletabber.music.TabSettings
import io.github.conorryan.whistletabber.music.TransposeMode
import io.github.conorryan.whistletabber.music.WhistleKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val WHISTLE_KEY = stringPreferencesKey("whistle")
/** Before per-note choices only C natural had one; read when [FINGERINGS_KEY] is absent. */
private val C_NATURAL_KEY = stringPreferencesKey("cNatural")
private val FINGERINGS_KEY = stringPreferencesKey("fingerings")
private val FOLD_KEY = booleanPreferencesKey("foldOutOfRange")
private val MODE_KEY = stringPreferencesKey("transposeMode")
private val NOTE_NAMES_KEY = stringPreferencesKey("noteNames")

/**
 * How tabs are drawn: chosen whistle, fingering choices, octave folding,
 * transpose mode and note names. Enums are stored by name; an unknown name (from a newer or
 * older version) falls back to the default.
 */
class TabSettingsStore(private val dataStore: DataStore<Preferences>) {

    val settings: Flow<TabSettings> = dataStore.data.map { prefs ->
        val defaults = TabSettings()
        TabSettings(
            whistle = enumByName<WhistleKey>(prefs[WHISTLE_KEY]) ?: defaults.whistle,
            fingerings = fingeringsOf(prefs),
            foldOutOfRange = prefs[FOLD_KEY] ?: defaults.foldOutOfRange,
            mode = enumByName<TransposeMode>(prefs[MODE_KEY]) ?: defaults.mode,
            noteNames = enumByName<NoteNames>(prefs[NOTE_NAMES_KEY]) ?: defaults.noteNames,
        )
    }

    suspend fun setWhistle(whistle: WhistleKey) {
        dataStore.edit { it[WHISTLE_KEY] = whistle.name }
    }

    /** Plays the note [offset] semitones above the bottom note with its option [index] (0 = default). */
    suspend fun setFingering(offset: Int, index: Int) {
        dataStore.edit { it[FINGERINGS_KEY] = fingeringsOf(it).with(offset, index).encode() }
    }

    suspend fun setFoldOutOfRange(fold: Boolean) {
        dataStore.edit { it[FOLD_KEY] = fold }
    }

    suspend fun setMode(mode: TransposeMode) {
        dataStore.edit { it[MODE_KEY] = mode.name }
    }

    suspend fun setNoteNames(noteNames: NoteNames) {
        dataStore.edit { it[NOTE_NAMES_KEY] = noteNames.name }
    }

    companion object {
        /** The old `cNatural` setting's enum names and shapes, for settings saved before per-note choices. */
        private val LEGACY_C_NATURAL: Map<String, String> = mapOf(
            "OXX_OOO" to "OXX OOO",
            "HALF_TOP" to "½OO OOO",
            "OXX_OXX" to "OXX OXX",
            "OXX_XOX" to "OXX XOX",
            "OXX_XXX" to "OXX XXX",
            "OXX_OOX" to "OXX OOX",
        )

        private fun fingeringsOf(prefs: Preferences): FingeringChoices {
            prefs[FINGERINGS_KEY]?.let { return FingeringChoices.decode(it) }
            val c = LEGACY_C_NATURAL[prefs[C_NATURAL_KEY]] ?: return FingeringChoices.DEFAULT
            return FingeringChoices.decode("10=$c;22=$c+")
        }

        fun create(context: Context): TabSettingsStore = TabSettingsStore(SettingsDataStores.tab(context))
    }
}
