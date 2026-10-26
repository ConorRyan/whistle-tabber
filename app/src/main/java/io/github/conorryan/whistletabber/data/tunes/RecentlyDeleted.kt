package io.github.conorryan.whistletabber.data.tunes

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Delete with undo. [delete] removes tunes and offers them for Undo as one
 * batch; the library shows the offer (after the deleting screen has closed,
 * when that was the tune screen) and answers with [undo] or [dismiss]. One
 * batch is on offer at a time: a newer deletion replaces it, and an answer
 * about an older batch does nothing. Restores run on [scope], so leaving the
 * screen doesn't cancel them.
 */
class RecentlyDeleted(
    private val repository: TuneRepository,
    private val scope: CoroutineScope,
) {
    private val _offer = MutableStateFlow<List<Tune>?>(null)

    /** The tunes waiting for an Undo answer, if any; never an empty list. */
    val offer: StateFlow<List<Tune>?> = _offer.asStateFlow()

    /** Deletes the tune and offers it for Undo. False when it was already gone. */
    suspend fun delete(id: Long): Boolean = delete(listOf(id)) > 0

    /** Deletes the tunes and offers them for Undo; returns how many were still there. */
    suspend fun delete(ids: Collection<Long>): Int {
        val tunes = repository.delete(ids)
        if (tunes.isNotEmpty()) _offer.value = tunes
        return tunes.size
    }

    /** Puts [tunes] back with their ids, stars and dates, if they are still on offer. */
    fun undo(tunes: List<Tune>) {
        if (_offer.compareAndSet(tunes, null)) scope.launch { repository.restore(tunes) }
    }

    /** The Undo offer for [tunes] was declined. */
    fun dismiss(tunes: List<Tune>) {
        _offer.compareAndSet(tunes, null)
    }
}
