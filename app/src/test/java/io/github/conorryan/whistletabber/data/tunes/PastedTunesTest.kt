package io.github.conorryan.whistletabber.data.tunes

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PastedTunesTest {

    private val image = Uri.parse("content://clipboard/one.png")
    private val abc = Uri.parse("content://clipboard/a.abc")

    /** A clip with explicit mime types and one URI item, as `ClipData.newUri` makes. */
    private fun uriClip(mime: String, uri: Uri) =
        ClipData("label", arrayOf(mime), ClipData.Item(uri))

    @Before
    fun setUp() {
        Robolectric.setupContentProvider(FakeDocumentsProvider::class.java, FakeDocumentsProvider.AUTHORITY)
    }

    @Test
    fun `text becomes text`() {
        val clip = ClipData.newPlainText("label", "X:1\nT:A\nK:D\nDEF|\n")
        assertEquals(PastedTunes(null, emptyList(), "X:1\nT:A\nK:D\nDEF|\n"), PastedTunes.from(clip))
    }

    @Test
    fun `an image clip gives the image, not a file`() {
        assertEquals(PastedTunes(image, emptyList(), null), PastedTunes.from(uriClip("image/png", image)))
    }

    @Test
    fun `a copied file gives a file, and its own text is not taken as ABC`() {
        assertEquals(PastedTunes(null, listOf(abc), null), PastedTunes.from(uriClip("text/plain", abc)))
        val resolver = ApplicationProvider.getApplicationContext<Context>().contentResolver
        assertEquals(PastedTunes(null, listOf(abc), null), PastedTunes.from(ClipData.newUri(resolver, "label", abc)))
        val two = uriClip("image/png", image)
        two.addItem(ClipData.Item(Uri.parse("content://clipboard/two.png")))
        assertEquals(PastedTunes(image, emptyList(), null), PastedTunes.from(two))
    }

    @Test
    fun `an image shared through an intent counts, other streams are files`() {
        val sharedImage = ClipData.newPlainText("label", "pasted")
        sharedImage.addItem(ClipData.Item(Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, image)))
        assertEquals(PastedTunes(image, emptyList(), "pasted"), PastedTunes.from(sharedImage))

        val sharedFile = ClipData.newPlainText("label", "pasted")
        sharedFile.addItem(ClipData.Item(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, abc)))
        assertEquals(PastedTunes(null, listOf(abc), "pasted"), PastedTunes.from(sharedFile))
    }

    @Test
    fun `nothing usable gives null`() {
        assertNull(PastedTunes.from(null))
        assertNull(PastedTunes.from(ClipData.newPlainText("label", "  ")))
        assertNull(PastedTunes.from(uriClip("text/plain", Uri.parse("file:///sdcard/a.abc"))))
    }

    @Test
    fun `any other text is given to the importer, which reports finding nothing`() {
        assertEquals(
            PastedTunes(null, emptyList(), "https://example.org/tune"),
            PastedTunes.from(ClipData.newPlainText("label", "https://example.org/tune")),
        )
    }
}
