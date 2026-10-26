package io.github.conorryan.whistletabber.data.tunes

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Delete with undo. [delete] removes the tune and offers it for Undo; the
 * library shows the offer after the deleting screen has closed and answers
 * with [undo] or [dismiss]. One tune is on offer at a time: a newer deletion
 * replaces it, and an answer about an older tune does nothing. Restores run
 * on [scope], so leaving the screen doesn't cancel them.
 */
class RecentlyDeleted(
    private val repository: TuneRepository,
    private val scope: CoroutineScope,
) {
    private val _offer = MutableStateFlow<Tune?>(null)

    /** The tune waiting for an Undo answer, if any. */
    val offer: StateFlow<Tune?> = _offer.asStateFlow()

    /** Deletes the tune and offers it for Undo. False when it was already gone. */
    suspend fun delete(id: Long): Boolean {
        val tune = repository.delete(id) ?: return false
        _offer.value = tune
        return true
    }

    /** Puts [tune] back with its id, star and dates, if it is still on offer. */
    fun undo(tune: Tune) {
        if (_offer.compareAndSet(tune, null)) scope.launch { repository.restore(tune) }
    }

    /** The Undo offer for [tune] was declined. */
    fun dismiss(tune: Tune) {
        _offer.compareAndSet(tune, null)
    }
}
