package io.github.conorryan.whistletabber.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AbcNotesTest {

    @Test
    fun `notes are spelled in the key`() {
        assertEquals("D", AbcNotes.spell(62, "D"))
        assertEquals("F", AbcNotes.spell(66, "D")) // F sharp is in D major
        assertEquals("=F", AbcNotes.spell(65, "D"))
        assertEquals("=c", AbcNotes.spell(72, "D"))
        assertEquals("^G", AbcNotes.spell(68, "D"))
        assertEquals("_E", AbcNotes.spell(63, "F"))
        assertEquals("B", AbcNotes.spell(70, "Bb")) // B flat is in B flat major
        assertEquals("=B", AbcNotes.spell(71, "Bb"))
        assertEquals("d'", AbcNotes.spell(86, "D"))
        assertEquals("C,", AbcNotes.spell(48, "C"))
        assertEquals("C", AbcNotes.spell(61, "D")) // C sharp, low octave
        assertEquals("^F", AbcNotes.spell(66, "C"))
    }

    @Test
    fun `a letter altered earlier in the bar is written with an accidental every time`() {
        assertEquals(listOf("=c", "^c", "B", "^c"), AbcNotes.spellBar(listOf(72, 73, 71, 73), "D"))
        // Another octave of the same letter too: abcjs's transposer reads by letter alone.
        assertEquals(listOf("=c", "^C"), AbcNotes.spellBar(listOf(72, 61), "D"))
        assertEquals(listOf("D", "z", "^A", "=A"), AbcNotes.spellBar(listOf(62, null, 70, 69), "D"))
        assertEquals(listOf("A", "B"), AbcNotes.spellBar(listOf(69, 71), "D"))
    }

    @Test
    fun `every pitch in every whistle's key reads back as itself`() {
        for (whistle in WhistleKey.entries) {
            val key = whistle.letter
            for (midi in 48..100) {
                assertEquals("$key $midi", listOf<Int?>(midi), AbcNotes.parseBar(listOf(AbcNotes.spell(midi, key)), key))
            }
            // A whole chromatic bar, with its accidentals carried along.
            val bar = (60..84).toList()
            assertEquals(key, bar, AbcNotes.parseBar(AbcNotes.spellBar(bar, key), key))
        }
    }

    @Test
    fun `an accidental lasts for that letter and octave until the bar ends`() {
        assertEquals(listOf(61, 61, 72), AbcNotes.parseBar(listOf("^C", "C", "c"), "C"))
        assertEquals(listOf(72, 73), AbcNotes.parseBar(listOf("=c", "^c"), "D"))
        assertEquals(listOf(null, 74), AbcNotes.parseBar(listOf("z", "d"), "D"))
    }

    @Test
    fun `anything but a plain note or rest is not read`() {
        assertNull(AbcNotes.parseBar(listOf("D2"), "D"))
        assertNull(AbcNotes.parseBar(listOf("[DF]"), "D"))
        assertNull(AbcNotes.parseBar(listOf("^^D"), "D"))
        assertNull(AbcNotes.parseBar(listOf("C'"), "D"))
        assertNull(AbcNotes.parseBar(listOf("c,"), "D"))
        assertNull(AbcNotes.parseBar(listOf("\"Am\"A"), "D"))
    }

    @Test
    fun `note names follow the key and the naming convention`() {
        assertEquals("F♯", AbcNotes.name(66, "D", NoteNames.LETTERS))
        assertEquals("Fa♯", AbcNotes.name(66, "D", NoteNames.SOLFEGE))
        assertEquals("B♭", AbcNotes.name(70, "F", NoteNames.LETTERS))
        assertEquals("Do", AbcNotes.name(72, "D", NoteNames.SOLFEGE))
        assertEquals("D", AbcNotes.name(74, "D", NoteNames.AUTO))
    }
}
