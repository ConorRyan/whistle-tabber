package io.github.conorryan.whistletabber.ui.tune

import io.github.conorryan.whistletabber.data.tunes.Tune
import io.github.conorryan.whistletabber.music.AbcBook
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The key-change preview state machine, without a ViewModel or a page. */
@OptIn(ExperimentalCoroutinesApi::class)
class KeyChangePreviewTest {

    private fun tune(key: String = "D") = Tune(
        id = 1,
        abc = "X:1\nT:Test Tune\nK:$key\nGAc|",
        title = "Test Tune",
        alternateTitles = emptyList(),
        rhythm = null,
        key = key,
        meter = null,
        whistle = null,
        starred = false,
        source = "user",
        createdAt = 0,
        updatedAt = 0,
    )

    private fun scope() = CoroutineScope(UnconfinedTestDispatcher())

    @Test
    fun `a reachable tonic is previewed and filled by the rewrite`() = runTest {
        val p = KeyChangePreview(scope())
        var asked: Pair<String, Int>? = null
        p.start(tune(), "G") { abc, steps -> asked = abc to steps; "X:1\nT:Test Tune\nK:G\ncdf|" }
        assertEquals("X:1\nT:Test Tune\nK:D\nGAc|" to 5, asked)
        assertEquals(KeyChangePreview.Preview("G", "G", 5, "X:1\nT:Test Tune\nK:G\ncdf|"), p.preview.value)
    }

    @Test
    fun `a minor key keeps its mode in the name`() = runTest {
        val p = KeyChangePreview(scope())
        p.start(tune("Am"), "C") { _, _ -> "rewritten" }
        assertEquals(KeyChangePreview.Preview("C", "Cm", 3, "rewritten"), p.preview.value)
    }

    @Test
    fun `picking the tune's own key cancels the preview`() = runTest {
        val p = KeyChangePreview(scope())
        p.start(tune(), "G") { _, _ -> "rewritten" }
        p.start(tune(), "D") { _, _ -> error("unreachable") }
        assertNull(p.preview.value)
    }

    @Test
    fun `a key that can't be reached does nothing`() = runTest {
        val p = KeyChangePreview(scope())
        p.start(tune(), "H") { _, _ -> error("unreachable") }
        assertNull(p.preview.value)
    }

    @Test
    fun `a newer preview is not filled by an older rewrite`() = runTest {
        val p = KeyChangePreview(scope())
        val gate = CompletableDeferred<Unit>()
        p.start(tune(), "G") { _, _ -> gate.await(); "in G" }
        p.start(tune(), "A") { _, _ -> "in A" }
        gate.complete(Unit)
        assertEquals(KeyChangePreview.Preview("A", "A", -5, "in A"), p.preview.value)
    }

    @Test
    fun `a failed rewrite leaves the preview pending`() = runTest {
        val p = KeyChangePreview(scope())
        p.start(tune(), "G") { _, _ -> null }
        assertEquals(KeyChangePreview.Preview("G", "G", 5), p.preview.value)
    }

    @Test
    fun `the copy is the rewrite retitled, and pending until the rewrite arrives`() = runTest {
        val p = KeyChangePreview(scope())
        p.start(tune(), "G") { _, _ -> null }
        assertNull(p.copyAbc(tune(), p.preview.value!!))
        p.start(tune(), "G") { _, _ -> "X:1\nT:Test Tune\nK:G\ncdf|" }
        assertEquals("Test Tune (G)", AbcBook.headers(p.copyAbc(tune(), p.preview.value!!)!!).title)
    }

    @Test
    fun `cancel clears the preview`() = runTest {
        val p = KeyChangePreview(scope())
        p.start(tune(), "G") { _, _ -> "rewritten" }
        p.cancel()
        assertNull(p.preview.value)
    }
}
