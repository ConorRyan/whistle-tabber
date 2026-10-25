package io.github.conorryan.whistletabber.data.tunes

import android.content.ContentResolver
import android.net.Uri
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads and writes `.abc` files picked through the Storage Access Framework.
 * Files are written as UTF-8. Reading tries strict UTF-8 first and falls back
 * to Latin-1, the encoding of many older ABC collections.
 */
class AbcFiles(private val resolver: ContentResolver) {

    suspend fun read(uri: Uri): String = withContext(Dispatchers.IO) {
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException("Cannot open $uri")
        decode(bytes)
    }

    suspend fun write(uri: Uri, text: String) = withContext(Dispatchers.IO) {
        val out = resolver.openOutputStream(uri, "wt") ?: throw IOException("Cannot write $uri")
        out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    companion object {
        fun decode(bytes: ByteArray): String = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            String(bytes, Charsets.ISO_8859_1)
        }
    }
}

/** Suggested names for exported files. */
object ExportNames {
    private val UNSAFE = Regex("""[\\/:*?"<>|\p{Cntrl}]+""")

    /** The whole library: `whistle-tabber-2026-10-25.abc`. */
    fun library(date: LocalDate): String = "whistle-tabber-$date.abc"

    /** One tune, named after its title with characters file systems reject removed. */
    fun tune(title: String): String {
        val safe = title.replace(UNSAFE, " ").replace(Regex("""\s+"""), " ").trim().trimEnd('.').take(80).trim()
        return (safe.ifEmpty { "tune" }) + ".abc"
    }
}
