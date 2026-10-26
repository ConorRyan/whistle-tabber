package io.github.conorryan.whistletabber.music

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteNamesTest {

    @Test
    fun `every whistle has a letter and a solfege name`() {
        val expected = mapOf(
            WhistleKey.HIGH_G to ("G" to "Sol"),
            WhistleKey.F to ("F" to "Fa"),
            WhistleKey.E_FLAT to ("E♭" to "Mi♭"),
            WhistleKey.D to ("D" to "Ré"),
            WhistleKey.C to ("C" to "Do"),
            WhistleKey.B_FLAT to ("B♭" to "Si♭"),
            WhistleKey.A to ("A" to "La"),
            WhistleKey.LOW_G to ("G" to "Sol"),
            WhistleKey.LOW_F to ("F" to "Fa"),
            WhistleKey.LOW_D to ("D" to "Ré"),
            WhistleKey.LOW_C to ("C" to "Do"),
        )
        assertEquals(WhistleKey.entries.toSet(), expected.keys)
        expected.forEach { (key, names) ->
            assertEquals(names.first, key.noteName(NoteNames.LETTERS))
            assertEquals(names.second, key.noteName(NoteNames.SOLFEGE))
        }
    }

    @Test
    fun `registers name the high and low whistles`() {
        assertEquals(listOf(WhistleKey.HIGH_G), WhistleKey.entries.filter { it.register == WhistleKey.Register.HIGH })
        assertEquals(
            listOf(WhistleKey.LOW_G, WhistleKey.LOW_F, WhistleKey.LOW_D, WhistleKey.LOW_C),
            WhistleKey.entries.filter { it.register == WhistleKey.Register.LOW },
        )
    }

    @Test
    fun `letters are the default`() {
        assertEquals(NoteNames.LETTERS, TabSettings().noteNames)
    }
}
