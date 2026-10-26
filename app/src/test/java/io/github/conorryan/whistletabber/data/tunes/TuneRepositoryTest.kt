package io.github.conorryan.whistletabber.data.tunes

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.conorryan.whistletabber.music.AbcBook
import io.github.conorryan.whistletabber.music.AbcProblem
import io.github.conorryan.whistletabber.music.WhistleKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TuneRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: TuneRepository
    private var clock = 1_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = TuneRepository(db, now = { clock })
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun tune(title: String, key: String = "D", rhythm: String = "reel", body: String = "DEF|", extra: String = "") =
        "X:1\nT:$title\n${extra}R:$rhythm\nM:4/4\nL:1/8\nK:$key\n$body\n"

    private fun fixture(name: String): String =
        requireNotNull(javaClass.getResource("/abc/$name")) { name }.readText()

    @Test
    fun `save derives the index fields`() = runBlocking {
        val id = repo.save(tune("Cailín Deas", key = "Em", rhythm = "Slip Jig", extra = "T:Pretty Girl\n"))
        val t = repo.get(id)!!
        assertEquals("Cailín Deas", t.title)
        assertEquals(listOf("Pretty Girl"), t.alternateTitles)
        assertEquals("slip jig", t.rhythm)
        assertEquals("Em", t.key)
        assertEquals("4/4", t.meter)
        assertEquals(WhistleKey.D, t.whistle) // suggested from Em
        assertEquals(TuneEntity.SOURCE_USER, t.source)
        assertEquals(1_000L, t.createdAt)
    }

    @Test
    fun `editing the ABC updates the derived fields and keeps the rest`() = runBlocking {
        val id = repo.save(tune("Old Name"))
        repo.setStarred(id, true)
        clock = 2_000L
        repo.save(tune("New Name", key = "F", rhythm = "jig"), id)
        val t = repo.get(id)!!
        assertEquals("New Name", t.title)
        assertEquals("jig", t.rhythm)
        assertEquals(WhistleKey.C, t.whistle)
        assertTrue(t.starred)
        assertEquals(1_000L, t.createdAt)
        assertEquals(2_000L, t.updatedAt)
        assertEquals(1, repo.tunes.first().size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `save refuses ABC without a title`(): Unit = runBlocking {
        repo.save("X:1\nK:D\nD|")
    }

    @Test
    fun `setWhistle writes the directive into the ABC`() = runBlocking {
        val id = repo.save(tune("A"))
        repo.setWhistle(id, WhistleKey.LOW_D)
        val t = repo.get(id)!!
        assertEquals(WhistleKey.LOW_D, t.whistle)
        assertEquals(WhistleKey.LOW_D, AbcBook.whistleOf(t.abc))
        repo.setWhistle(id, null)
        assertNull(AbcBook.whistleOf(repo.get(id)!!.abc))
        assertEquals(WhistleKey.D, repo.get(id)!!.whistle) // back to the key's suggestion
    }

    @Test
    fun `tunes are sorted by title ignoring case, accents and a leading the`() = runBlocking {
        for (title in listOf("the Wind", "Ál Bán", "banshee", "Abbey")) repo.save(tune(title))
        assertEquals(listOf("Abbey", "Ál Bán", "banshee", "the Wind"), repo.tunes.first().map { it.title })
    }

    @Test
    fun `search matches main and alternate titles without case or accents`() = runBlocking {
        repo.save(tune("Port Uí Mhuirgheasa"))
        repo.save(tune("The Kesh", rhythm = "jig", extra = "T:Kesh Jig\n"))
        repo.save(tune("Morrison's", rhythm = "jig"))
        assertEquals(listOf("Port Uí Mhuirgheasa"), repo.search("ui mhuir").first().map { it.title })
        assertEquals(listOf("The Kesh"), repo.search("KESH JIG").first().map { it.title })
        assertEquals(3, repo.search("").first().size)
        assertEquals(0, repo.search("%").first().size)
    }

    @Test
    fun `search filters by rhythm and star`() = runBlocking {
        val a = repo.save(tune("A", rhythm = "reel"))
        repo.save(tune("B", rhythm = "Jig"))
        repo.save(tune("C", rhythm = "jig"))
        assertEquals(listOf("B", "C"), repo.search("", rhythm = "JIG").first().map { it.title })
        repo.setStarred(a, true)
        assertEquals(listOf("A"), repo.search("", starredOnly = true).first().map { it.title })
        assertEquals(emptyList<String>(), repo.search("", rhythm = "jig", starredOnly = true).first())
        assertEquals(listOf("jig", "reel"), repo.rhythms.first())
    }

    @Test
    fun `delete and restore keep ids, stars and dates`() = runBlocking {
        val a = repo.save(tune("A"))
        val b = repo.save(tune("B"))
        val kept = repo.save(tune("C"))
        repo.setStarred(a, true)
        val deleted = repo.delete(listOf(a, b, 12345))
        assertEquals(listOf(a, b), deleted.map { it.id })
        assertNull(repo.get(a))
        assertNull(repo.get(b))
        assertNotNull(repo.get(kept))
        repo.restore(deleted)
        assertEquals(deleted, listOf(repo.get(a), repo.get(b)))
        assertTrue(repo.get(a)!!.starred)
        assertEquals(emptyList<Tune>(), repo.delete(listOf(12345)))
    }

    @Test
    fun `import counts added, skipped duplicates and errors`() = runBlocking {
        repo.save(tune("Existing"))
        val book = listOf(
            tune("Existing"), // same title, same music: skipped
            tune("Existing", body = "GAB|"), // same title, different music: added
            tune("New One"),
            tune("New One"), // duplicate within the file: skipped
            "X:5\nK:D\nD|", // no title
            "X:6\nT:Keyless\nD|", // no key
        ).joinToString("\n")
        val result = repo.importBook(book)
        assertEquals(ImportResult(added = 2, skipped = 2, errors = listOf(
            ImportError(5, null, AbcProblem.NO_TITLE),
            ImportError(6, "Keyless", AbcProblem.NO_KEY),
        )), result)
        assertEquals(6, result.total)
        assertEquals(3, repo.count.first())
        assertTrue(repo.tunes.first().filter { it.title != "Existing" || it.abc.contains("GAB") }
            .all { it.source == TuneEntity.SOURCE_IMPORT })
    }

    @Test
    fun `importing the same file twice adds nothing the second time`() = runBlocking {
        val text = fixture("book-with-header.abc")
        assertEquals(3, repo.importBook(text).added)
        assertEquals(ImportResult(0, 3, emptyList()), repo.importBook(text))
    }

    @Test
    fun `export then re-import is lossless`() = runBlocking {
        repo.importBook(fixture("book-with-header.abc"))
        repo.importBook(fixture("crlf-bom.abc"))
        val before = repo.tunes.first()
        val exported = repo.exportAll()

        val other = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val copy = TuneRepository(other, now = { clock })
            assertEquals(ImportResult(4, 0, emptyList()), copy.importBook(exported))
            val after = copy.tunes.first()
            assertEquals(before.map { it.title }, after.map { it.title })
            before.zip(after).forEach { (b, a) ->
                assertTrue(b.title, TuneIndex.sameMusic(b.abc, a.abc))
                assertEquals(b.copy(id = 0, abc = ""), a.copy(id = 0, abc = ""))
            }
            // A second round trip changes nothing at all.
            assertEquals(exported, copy.exportAll())
        } finally {
            other.close()
        }
    }

    @Test
    fun `export one tune`() = runBlocking {
        val id = repo.save(tune("Solo").replace("X:1", "X:42"))
        assertEquals(tune("Solo"), repo.export(id))
        assertNull(repo.export(999))
    }

    @Test
    fun `observe follows edits`() = runBlocking {
        val id = repo.save(tune("A"))
        assertNotNull(repo.observe(id).first())
        repo.save(tune("B"), id)
        assertEquals("B", repo.observe(id).first()!!.title)
    }
}
