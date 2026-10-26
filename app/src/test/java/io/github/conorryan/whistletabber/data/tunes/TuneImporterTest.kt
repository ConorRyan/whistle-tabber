package io.github.conorryan.whistletabber.data.tunes

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.conorryan.whistletabber.music.AbcBook
import io.github.conorryan.whistletabber.music.AbcProblem
import java.io.File
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Imports from files and the bundled starter set; web-tests/starter-set.test.js checks the set parses cleanly in abcjs. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TuneImporterTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repo: TuneRepository
    private lateinit var importer: TuneImporter

    @Before
    fun setUp() {
        Robolectric.setupContentProvider(FakeDocumentsProvider::class.java, FakeDocumentsProvider.AUTHORITY)
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = TuneRepository(db)
        importer = TuneImporter(repo, AbcFiles(context.contentResolver)) { TuneImporter.readStarterSet(context) }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun tune(title: String) = "X:1\nT:$title\nR:reel\nK:D\nDEF|\n"

    private fun file(name: String, text: String) = File(context.cacheDir, name).writeText(text).let {
        FakeDocumentsProvider.uri(name)
    }

    @Test
    fun `asset has 50 tunes, each titled, keyed, credited and with a rhythm`() {
        val tunes = AbcBook.split(TuneImporter.readStarterSet(context))
        assertEquals(50, tunes.size)
        for (tune in tunes) {
            val h = AbcBook.headers(tune)
            assertFalse(tune, h.title.isNullOrBlank())
            assertNotNull(h.title, h.key)
            assertNotNull(h.title, h.rhythm)
            assertTrue(h.title, h.transcribers.isNotEmpty())
            assertNotNull(h.title, AbcBook.suggestWhistle(h.key))
        }
    }

    @Test
    fun `adding the starter set imports every tune once`() = runBlocking {
        assertEquals(ImportResult(50, 0, emptyList()), importer.addStarterSet())
        assertTrue(repo.tunes.first().all { it.source == TuneEntity.SOURCE_STARTER })
        assertEquals(ImportResult(0, 50, emptyList()), importer.addStarterSet())
        assertEquals(50, repo.count.first())
    }

    @Test
    fun `the starter set covers the rhythm filters`() = runBlocking {
        importer.addStarterSet()
        assertEquals(
            setOf("reel", "jig", "slip jig", "slide", "hornpipe", "set dance", "march", "air"),
            repo.rhythms.first().toSet(),
        )
    }

    @Test
    fun `files are imported as one result, counting unreadable files`() = runBlocking {
        val a = file("a.abc", tune("One") + "\n" + tune("Two") + "\nX:3\nT:Keyless\n")
        val b = file("b.abc", tune("One"))
        val result = importer.importFiles(listOf(a, b, FakeDocumentsProvider.uri("missing.abc")))
        assertEquals(
            ImportResult(2, 1, listOf(ImportError(3, "Keyless", AbcProblem.NO_KEY)), unreadable = 1),
            result,
        )
        assertTrue(repo.tunes.first().all { it.source == TuneEntity.SOURCE_IMPORT })
    }

    @Test
    fun `no files import nothing`() = runBlocking {
        assertEquals(ImportResult.NONE, importer.importFiles(emptyList()))
    }

    @Test
    fun `busy while an import runs`() = runBlocking {
        val gate = CountDownLatch(1)
        val slow = TuneImporter(repo, AbcFiles(context.contentResolver)) {
            gate.await()
            tune("Slow")
        }
        assertFalse(slow.busy.value)
        val job = launch(Dispatchers.Default) { slow.addStarterSet() }
        withTimeout(5_000) { slow.busy.first { it } }
        gate.countDown()
        job.join()
        assertFalse(slow.busy.value)
        assertEquals(1, repo.count.first())
    }
}
