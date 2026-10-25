package io.github.conorryan.whistletabber.web

import java.io.File
import kotlinx.serialization.descriptors.elementNames
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * whistle-page.js and BridgeProtocol.kt describe the same protocol in two
 * languages. This reads the JS source and fails when they drift apart.
 */
class BridgeProtocolSyncTest {

    private val pageJs = File("src/main/assets/web/whistle-page.js").readText()

    private fun serialNames(descriptor: kotlinx.serialization.descriptors.SerialDescriptor): Set<String> =
        descriptor.getElementDescriptor(1).elementNames.toSet()

    @Test
    fun `every command Kotlin sends has a case in the page`() {
        val handled = Regex("""case "(\w+)":""").findAll(pageJs).map { it.groupValues[1] }.toSet()
        assertEquals(serialNames(BridgeCommand.serializer().descriptor), handled)
    }

    @Test
    fun `every event the page posts is one Kotlin decodes`() {
        val posted = Regex("""post\(\{\s*type: "(\w+)"""").findAll(pageJs).map { it.groupValues[1] }.toSet()
        assertEquals(serialNames(BridgeEvent.serializer().descriptor), posted)
    }
}
