package io.github.conorryan.whistletabber.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** Every string the app shows in English must also exist in each translation. */
class TranslationsTest {

    private fun names(dir: String): Set<String> =
        Regex("""<(?:string|plurals) name="([^"]+)"""")
            .findAll(File("src/main/res/$dir/strings.xml").readText())
            .map { it.groupValues[1] }
            .toSet()

    @Test
    fun `french has every string`() {
        val english = names("values")
        val french = names("values-fr")
        assertEquals("missing in values-fr", emptySet<String>(), english - french)
        assertEquals("only in values-fr", emptySet<String>(), french - english)
    }
}
