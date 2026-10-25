package io.github.conorryan.whistletabber.data.tunes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TuneIndexTest {

    @Test
    fun `fold drops case, accents and extra space`() {
        assertEquals("o raghallaigh's reel", TuneIndex.fold("  Ó  Raghallaigh's\tREEL "))
        assertEquals("cailin deas", TuneIndex.fold("Cailín Deas"))
    }

    @Test
    fun `sort key ignores a leading the`() {
        assertEquals("banks of the ilen", TuneIndex.sortKey("the Banks of the Ilen"))
        assertEquals("theodore's", TuneIndex.sortKey("Theodore's"))
    }

    @Test
    fun `same music ignores X, line endings and trailing space`() {
        assertTrue(TuneIndex.sameMusic("X:1\nT:A\nK:D\nDEF|\n", "X:9\r\nT:A  \r\nK:D\r\nDEF|"))
        assertFalse(TuneIndex.sameMusic("X:1\nT:A\nK:D\nDEF|", "X:1\nT:A\nK:D\nDEG|"))
    }

    @Test
    fun `like pattern escapes wildcards`() {
        assertEquals("%100\\% re\\_el%", TuneIndex.likePattern("100% Re_el"))
        assertEquals("%a\\\\b%", TuneIndex.likePattern("a\\b"))
        assertEquals("%%", TuneIndex.likePattern(""))
    }
}
