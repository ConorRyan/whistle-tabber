package io.github.conorryan.whistletabber.data.tunes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentlyDeletedTest {

    private fun tune(id: Long) = Tune(id, "X:1", "T$id", emptyList(), null, null, null, null, false, "user", 0, 0)

    @Test
    fun `a tune is taken once`() {
        val bin = RecentlyDeleted()
        val t = tune(1)
        bin.put(t)
        assertEquals(t, bin.tune.value)
        assertTrue(bin.take(t))
        assertFalse(bin.take(t))
        assertNull(bin.tune.value)
    }

    @Test
    fun `a newer deletion replaces the older one`() {
        val bin = RecentlyDeleted()
        bin.put(tune(1))
        bin.put(tune(2))
        assertFalse(bin.take(tune(1)))
        assertTrue(bin.take(tune(2)))
    }
}
