package io.github.conorryan.whistletabber.ui.components

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * whistle-core.js and FingeringDiagram.kt draw the same diagram in two
 * places. This reads the JS source and fails when their geometry drifts.
 */
class FingeringDiagramGeometryTest {

    private val coreJs = File("src/main/assets/web/whistle-core.js").readText()

    private fun jsNumber(name: String): Float =
        Regex("""var $name = ([0-9.]+);""").find(coreJs)
            ?.groupValues?.get(1)?.toFloat()
            ?: error("$name not found in whistle-core.js")

    @Test
    fun `compose diagram uses the page's geometry`() {
        assertEquals(jsNumber("HOLE_R"), HOLE_R)
        assertEquals(jsNumber("HOLE_PITCH"), HOLE_PITCH)
        assertEquals(jsNumber("HAND_GAP"), HAND_GAP)
    }
}
