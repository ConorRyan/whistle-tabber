package io.github.conorryan.whistletabber.data.tunes

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A document provider over files in the cache dir, standing in for SAF. */
class FakeDocumentsProvider : ContentProvider() {
    private fun file(uri: Uri) = File(context!!.cacheDir, uri.lastPathSegment!!)

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val f = file(uri)
        if (mode.startsWith("r") && !f.exists()) throw java.io.FileNotFoundException(uri.toString())
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.parseMode(mode))
    }

    override fun onCreate() = true
    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri) = "text/plain"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0

    companion object {
        const val AUTHORITY = "io.github.conorryan.whistletabber.test.docs"
        fun uri(name: String): Uri = Uri.parse("content://$AUTHORITY/$name")
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AbcFilesTest {

    private lateinit var context: Context
    private lateinit var files: AbcFiles

    @Before
    fun setUp() {
        Robolectric.setupContentProvider(FakeDocumentsProvider::class.java, FakeDocumentsProvider.AUTHORITY)
        context = ApplicationProvider.getApplicationContext()
        files = AbcFiles(context.contentResolver)
    }

    private fun put(name: String, bytes: ByteArray): Uri {
        File(context.cacheDir, name).writeBytes(bytes)
        return FakeDocumentsProvider.uri(name)
    }

    @Test
    fun `reads UTF-8`() = runBlocking {
        val uri = put("utf8.abc", "X:1\nT:Port Uí Mhuirgheasa\nK:D\n".toByteArray(Charsets.UTF_8))
        assertEquals("X:1\nT:Port Uí Mhuirgheasa\nK:D\n", files.read(uri))
    }

    @Test
    fun `falls back to Latin-1`() = runBlocking {
        val uri = put("latin1.abc", "X:1\nT:Cailín Óg\nK:D\n".toByteArray(Charsets.ISO_8859_1))
        assertEquals("X:1\nT:Cailín Óg\nK:D\n", files.read(uri))
    }

    @Test(expected = IOException::class)
    fun `a missing file is an IOException`(): Unit = runBlocking {
        files.read(FakeDocumentsProvider.uri("missing.abc"))
    }

    @Test
    fun `writes UTF-8 and truncates`() = runBlocking {
        val uri = put("out.abc", ByteArray(1000) { 'x'.code.toByte() })
        files.write(uri, "T:Ó\n")
        assertEquals("T:Ó\n", File(context.cacheDir, "out.abc").readText(Charsets.UTF_8))
    }

    @Test
    fun `a 10k-line book reads and splits`() = runBlocking {
        val tune = (1..10).joinToString("\n") { "|:dAFA dAFA|BcdB AFEF:|" }
        val book = (1..1000).joinToString("\n\n") { "X:$it\nT:Tune $it\nR:reel\nK:D\n$tune" }
        assertEquals(true, book.lines().size > 10_000)
        val text = files.read(put("big.abc", book.toByteArray()))
        assertEquals(1000, io.github.conorryan.whistletabber.music.AbcBook.split(text).size)
    }

    @Test
    fun `export names`() {
        assertEquals("whistle-tabber-2026-10-25.abc", ExportNames.library(LocalDate.of(2026, 10, 25)))
        assertEquals("Fair Maid of Cavan., The.abc", ExportNames.tune("Fair Maid of Cavan., The"))
        assertEquals("Tobin's Favorite.abc", ExportNames.tune("Tobin's Favorite."))
        assertEquals("Will You Come Down to Limerick.abc", ExportNames.tune("Will You Come Down to Limerick?"))
        assertEquals("AC DC.abc", ExportNames.tune("AC/DC"))
        assertEquals("tune.abc", ExportNames.tune(" ?? "))
        assertEquals(84, ExportNames.tune("x".repeat(200)).length)
    }
}
