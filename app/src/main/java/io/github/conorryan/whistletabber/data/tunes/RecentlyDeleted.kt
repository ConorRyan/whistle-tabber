package io.github.conorryan.whistletabber.data.tunes

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Hands a tune deleted on the tune screen to the library, which offers Undo
 * after the tune screen has closed. Holds one tune until someone takes it.
 */
class RecentlyDeleted {
    private val _tune = MutableStateFlow<Tune?>(null)
    val tune: StateFlow<Tune?> = _tune.asStateFlow()

    fun put(tune: Tune) {
        _tune.value = tune
    }

    /** Returns the waiting tune, if it is still [tune], and clears it. */
    fun take(tune: Tune): Boolean = _tune.compareAndSet(tune, null)
}
