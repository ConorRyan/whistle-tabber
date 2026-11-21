package io.github.conorryan.whistletabber.data.tunes

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SharedFilesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val shared = SharedFiles(context).also { clearFileProviderCache() }

    @Test
    fun `writes UTF-8 behind our FileProvider and keeps only the latest file`() = runBlocking {
        val first = shared.write("Old.abc", "X:1\nT:Old\nK:D\nD|\n")
        val text = "X:1\nT:Cailín Deas\nK:D\nD|\n\nX:2\nT:Ríl Mhór\nK:G\nG|\n"
        val uri = shared.write("Cailín Deas.abc", text)

        assertEquals("content", uri.scheme)
        assertEquals(SharedFiles.authority(context), uri.authority)
        assertEquals("Cailín Deas.abc", uri.lastPathSegment)
        assertEquals(text, AbcFiles(context.contentResolver).read(uri))
        val dir = File(context.cacheDir, SharedFiles.DIR)
        assertEquals(listOf("Cailín Deas.abc"), dir.list()!!.toList())
        assertTrue(first != uri)
    }

    @Test
    fun `the chooser sends the file as plain text with a read grant, leaving us out`() {
        val uri = Uri.parse("content://x.files/shared/Tune.abc")
        val me = ComponentName(context, "io.github.conorryan.whistletabber.MainActivity")
        val chooser = SharedFiles.chooser(uri, "2 tunes", me)

        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION")
        assertArrayEquals(arrayOf(me), chooser.getParcelableArrayExtra(Intent.EXTRA_EXCLUDE_COMPONENTS))
        @Suppress("DEPRECATION")
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("text/plain", send.type)
        @Suppress("DEPRECATION")
        assertEquals(uri, send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertEquals("2 tunes", send.getStringExtra(Intent.EXTRA_SUBJECT))
        assertEquals(uri, send.clipData!!.getItemAt(0).uri)
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }
}
