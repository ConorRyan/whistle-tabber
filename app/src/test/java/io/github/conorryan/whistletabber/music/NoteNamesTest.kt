package io.github.conorryan.whistletabber.music

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteNamesTest {

    @Test
    fun `every whistle has a letter and a solfege name`() {
        val expected = mapOf(
            WhistleKey.HIGH_G to ("High G" to "Sol aigu"),
            WhistleKey.F to ("F" to "Fa"),
            WhistleKey.E_FLAT to ("Eb" to "Mi♭"),
            WhistleKey.D to ("D" to "Ré"),
            WhistleKey.C to ("C" to "Do"),
            WhistleKey.B_FLAT to ("Bb" to "Si♭"),
            WhistleKey.A to ("A" to "La"),
            WhistleKey.LOW_G to ("Low G" to "Sol grave"),
            WhistleKey.LOW_F to ("Low F" to "Fa grave"),
            WhistleKey.LOW_D to ("Low D" to "Ré grave"),
            WhistleKey.LOW_C to ("Low C" to "Do grave"),
        )
        assertEquals(WhistleKey.entries.toSet(), expected.keys)
        expected.forEach { (key, names) ->
            assertEquals(names.first, key.label(NoteNames.LETTERS))
            assertEquals(names.second, key.label(NoteNames.SOLFEGE))
        }
    }

    @Test
    fun `note names leave out the register`() {
        assertEquals("D", WhistleKey.LOW_D.noteName(NoteNames.LETTERS))
        assertEquals("Ré", WhistleKey.LOW_D.noteName(NoteNames.SOLFEGE))
        assertEquals("Mi♭", WhistleKey.E_FLAT.noteName(NoteNames.SOLFEGE))
    }

    @Test
    fun `auto is the default and resolves by language`() {
        assertEquals(NoteNames.AUTO, TabSettings().noteNames)
        assertEquals(NoteNames.SOLFEGE, NoteNames.AUTO.resolve(languageUsesSolfege = true))
        assertEquals(NoteNames.LETTERS, NoteNames.AUTO.resolve(languageUsesSolfege = false))
    }

    @Test
    fun `fixed schemes ignore the language`() {
        assertEquals(NoteNames.LETTERS, NoteNames.LETTERS.resolve(languageUsesSolfege = true))
        assertEquals(NoteNames.SOLFEGE, NoteNames.SOLFEGE.resolve(languageUsesSolfege = false))
    }

    @Test
    fun `an unresolved auto reads as letters`() {
        assertEquals("Low D", WhistleKey.LOW_D.label(NoteNames.AUTO))
    }
}
