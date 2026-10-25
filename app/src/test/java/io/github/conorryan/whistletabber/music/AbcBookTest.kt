package io.github.conorryan.whistletabber.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AbcBookTest {

    private fun fixture(name: String): String =
        requireNotNull(javaClass.getResource("/abc/$name")) { name }.readText()

    @Test
    fun `split a book with a file header`() {
        val tunes = AbcBook.split(fixture("book-with-header.abc"))
        assertEquals(3, tunes.size)
        assertEquals(listOf("First Reel", "Second Jig", "Third, With: Colons"), tunes.map { AbcBook.headers(it).title })
        // %% directives go before X:, inherited fields right after it.
        assertTrue(tunes[0].startsWith("%%titlefont Times 18\nX:1\nL:1/8\nR:reel\nT:First Reel"))
        // A tune's own field wins over the file header's.
        assertEquals("jig", AbcBook.headers(tunes[1]).rhythm)
        assertEquals("1/16", AbcBook.headers(tunes[1]).unitLength)
        assertFalse(tunes[1].contains("L:1/8"))
        // Text after a blank line is not part of the tune.
        assertFalse(tunes[1].contains("between tunes"))
        assertEquals("reel", AbcBook.headers(tunes[2]).rhythm)
    }

    @Test
    fun `split a book without a file header keeps the tunes verbatim`() {
        val text = fixture("book-no-header.abc")
        val tunes = AbcBook.split(text)
        assertEquals(2, tunes.size)
        assertEquals(text.trimEnd(), tunes.joinToString("\n\n"))
    }

    @Test
    fun `split a lone tune without X`() {
        assertEquals(listOf("T:Lone\nK:D\nDEF|"), AbcBook.split("\n\nT:Lone\nK:D\nDEF|\n"))
        assertEquals(emptyList<String>(), AbcBook.split("just some words\n"))
        assertEquals(emptyList<String>(), AbcBook.split(""))
    }

    @Test
    fun `split only starts tunes at X at the start of a line`() {
        val tunes = AbcBook.split("X:1\nT:A\nN: see X:2 below\nK:D\nD|\n")
        assertEquals(1, tunes.size)
    }

    @Test
    fun `split strips a BOM and CRLF`() {
        val tunes = AbcBook.split(fixture("crlf-bom.abc"))
        assertEquals(1, tunes.size)
        assertTrue(tunes[0].startsWith("X: 7\nT:"))
        assertFalse(tunes[0].contains('\r'))
    }

    @Test
    fun `headers of an odd CRLF tune`() {
        val h = AbcBook.headers(fixture("crlf-bom.abc"))
        assertEquals("7", h.x)
        assertEquals(listOf("Spaced   Title"), h.titles) // empty T: skipped, body T: not a header
        assertEquals("Hand Written", h.composer)
        assertEquals("Nowhere", h.origin)
        assertEquals("No Book", h.book)
        assertEquals(listOf("First Person", "Second Person"), h.transcribers)
        assertEquals("hornpipe", h.rhythm)
        assertEquals("C|", h.meter)
        assertEquals("1/8", h.unitLength)
        assertEquals("D", h.key)
        assertEquals(WhistleKey.C, h.whistle)
    }

    @Test
    fun `headers keep colons and alternate titles`() {
        val h = AbcBook.headers("X:1\nT:Tune: The Sequel\nT:Other\nK:G\nG|")
        assertEquals("Tune: The Sequel", h.title)
        assertEquals(listOf("Other"), h.alternateTitles)
    }

    @Test
    fun `headers keep escaped percent signs`() {
        assertEquals("100\\% Reel", AbcBook.headers("X:1\nT:100\\% Reel % comment\nK:D").title)
    }

    @Test
    fun `headers of an empty tune`() {
        assertEquals(AbcHeaders(), AbcBook.headers(""))
    }

    @Test
    fun `whistle directive spellings`() {
        fun w(line: String) = AbcBook.whistleOf("X:1\n$line\nK:D\n")
        assertEquals(WhistleKey.D, w("%whistle_tab_key d"))
        assertEquals(WhistleKey.D, w("%whistle_tab_key   D   "))
        assertEquals(WhistleKey.LOW_D, w("%whistle_tab_key d low"))
        assertEquals(WhistleKey.E_FLAT, w("%whistle_tab_key Eb"))
        assertEquals(WhistleKey.E_FLAT, w("%whistle_tab_key d#"))
        assertEquals(WhistleKey.B_FLAT, w("%whistle_tab_key a#"))
        assertEquals(WhistleKey.HIGH_G, w("%whistle_tab_key g"))
        assertEquals(WhistleKey.LOW_G, w("%whistle_tab_key G LOW"))
        assertNull(w("%whistle_tab_key f#")) // no F# whistle
        assertNull(w("%whistle_tab_keyd"))
        assertNull(w(" %whistle_tab_key d")) // must start the line
        assertNull(w("%whistle_tab_octave 1"))
    }

    @Test
    fun `only the first whistle directive counts`() {
        assertEquals(WhistleKey.C, AbcBook.whistleOf("%whistle_tab_key c\n%whistle_tab_key d\n"))
    }

    @Test
    fun `withWhistle adds a directive before K`() {
        val abc = "X:1\nT:A\nK:D\nDEF|\n"
        assertEquals("X:1\nT:A\n%whistle_tab_key c\nK:D\nDEF|\n", AbcBook.withWhistle(abc, WhistleKey.C))
    }

    @Test
    fun `withWhistle replaces in place and keeps the rest`() {
        val abc = "X:1\n%whistle_tab_key d\n%whistle_tab_octave 1\nT:A\nK:D\nDEF|"
        assertEquals(
            "X:1\n%whistle_tab_key d low\n%whistle_tab_octave 1\nT:A\nK:D\nDEF|",
            AbcBook.withWhistle(abc, WhistleKey.LOW_D),
        )
    }

    @Test
    fun `withWhistle removes`() {
        assertEquals("X:1\nK:D\nD|", AbcBook.withWhistle("X:1\n%whistle_tab_key d\nK:D\nD|", null))
        assertEquals("X:1\nK:D\nD|", AbcBook.withWhistle("X:1\nK:D\nD|", null))
    }

    @Test
    fun `withWhistle keeps CRLF`() {
        val abc = "X:1\r\nK:D\r\nD|\r\n"
        assertEquals("X:1\r\n%whistle_tab_key eb\r\nK:D\r\nD|\r\n", AbcBook.withWhistle(abc, WhistleKey.E_FLAT))
    }

    @Test
    fun `every whistle round-trips through its directive`() {
        for (whistle in WhistleKey.entries) {
            val abc = AbcBook.withWhistle("X:1\nK:D\n", whistle)
            assertEquals(whistle, AbcBook.whistleOf(abc))
            assertEquals("X:1\nK:D\n", AbcBook.withWhistle(abc, null))
        }
    }

    @Test
    fun `join renumbers X and separates with blank lines`() {
        val joined = AbcBook.join(listOf("X:5\nT:A\nK:D\nD|\n\n", "T:B\nK:G\nG|", "%%scale 0.8\nX:5\nT:C\nK:A\nA|"))
        assertEquals(
            "X:1\nT:A\nK:D\nD|\n\nX:2\nT:B\nK:G\nG|\n\n%%scale 0.8\nX:3\nT:C\nK:A\nA|\n",
            joined,
        )
    }

    @Test
    fun `join then split round-trips`() {
        val tunes = AbcBook.split(fixture("book-with-header.abc"))
        val again = AbcBook.split(AbcBook.join(tunes))
        assertEquals(tunes.size, again.size)
        assertEquals(tunes.map { AbcBook.headers(it).copy(x = null) }, again.map { AbcBook.headers(it).copy(x = null) })
        assertEquals(again, AbcBook.split(AbcBook.join(again)))
    }

    @Test
    fun `suggested whistle by key`() {
        for (k in listOf("D", "Dmaj", "D major", "G", "Em", "E minor", "Emin", "Bm", "Am", "Ador", "A dorian", "Amix", "Edor", "Dmix")) {
            assertEquals(k, WhistleKey.D, AbcBook.suggestWhistle(k))
        }
        for (k in listOf("F", "Dm", "D minor")) {
            assertEquals(k, WhistleKey.C, AbcBook.suggestWhistle(k))
        }
        for (k in listOf("C", "Bb", "F#m", "none", "HP", "", null)) {
            assertNull("$k", AbcBook.suggestWhistle(k))
        }
        assertEquals(WhistleKey.D, AbcBook.suggestWhistle("D clef=treble"))
    }
}
