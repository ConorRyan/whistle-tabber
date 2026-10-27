package io.github.conorryan.whistletabber.data.tunes

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
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
class IncomingTunesTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repo: TuneRepository
    private lateinit var scope: CoroutineScope
    private lateinit var incoming: IncomingTunes

    @Before
    fun setUp() {
        Robolectric.setupContentProvider(FakeDocumentsProvider::class.java, FakeDocumentsProvider.AUTHORITY)
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = TuneRepository(db)
        scope = CoroutineScope(SupervisorJob())
        incoming = IncomingTunes(TuneImporter(repo, AbcFiles(context.contentResolver)) { "" }, scope)
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    private fun tune(title: String) = "X:1\nT:$title\nK:D\nDEF|\n"

    private fun file(name: String, text: String): Uri {
        File(context.cacheDir, name).writeText(text)
        return FakeDocumentsProvider.uri(name)
    }

    private val a = FakeDocumentsProvider.uri("a.abc")
    private val b = FakeDocumentsProvider.uri("b.abc")

    @Test
    fun `open with gives the file`() {
        assertEquals(SharedTunes(listOf(a), null), SharedTunes.from(Intent(Intent.ACTION_VIEW, a)))
    }

    @Test
    fun `share gives the stream, else the text`() {
        val stream = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, a).putExtra(Intent.EXTRA_TEXT, "ignored")
        assertEquals(SharedTunes(listOf(a), null), SharedTunes.from(stream))
        val text = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, tune("Shared"))
        assertEquals(SharedTunes(emptyList(), tune("Shared")), SharedTunes.from(text))
    }

    @Test
    fun `share multiple gives every stream`() {
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(a, b))
        assertEquals(SharedTunes(listOf(a, b), null), SharedTunes.from(intent))
    }

    @Test
    fun `nothing to import gives null`() {
        assertNull(SharedTunes.from(Intent(Intent.ACTION_MAIN)))
        assertNull(SharedTunes.from(Intent(Intent.ACTION_VIEW, Uri.parse("file:///sdcard/a.abc"))))
        assertNull(SharedTunes.from(Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, "  ")))
        assertNull(SharedTunes.from(Intent(Intent.ACTION_SEND)))
    }

    @Test
    fun `received files and text are imported as one result`() = runBlocking {
        val files = listOf(file("a.abc", tune("One")), file("b.abc", tune("One")))
        incoming.receive(SharedTunes(files, tune("Two")))
        val result = withTimeout(5_000) { incoming.result.filterNotNull().first() }
        assertEquals(ImportResult(2, 1, emptyList()), result)
        assertEquals(listOf("One", "Two"), repo.tunes.first().map { it.title }.sorted())
    }

    @Test
    fun `text that isn't ABC finds nothing`() = runBlocking {
        incoming.receive(SharedTunes(emptyList(), "https://example.org/tune"))
        assertEquals(ImportResult.NONE, withTimeout(5_000) { incoming.result.filterNotNull().first() })
    }

    @Test
    fun `consume clears the result, but not a newer one`() = runBlocking {
        incoming.receive(SharedTunes(emptyList(), tune("One")))
        val first = withTimeout(5_000) { incoming.result.filterNotNull().first() }
        incoming.consume(first)
        assertNull(incoming.result.value)

        incoming.receive(SharedTunes(emptyList(), tune("Two")))
        val second = withTimeout(5_000) { incoming.result.filterNotNull().first() }
        incoming.consume(ImportResult(9, 9, emptyList()))
        assertEquals(second, incoming.result.value)
    }
}
