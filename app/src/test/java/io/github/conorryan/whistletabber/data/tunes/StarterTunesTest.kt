package io.github.conorryan.whistletabber.data.tunes

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.conorryan.whistletabber.music.AbcBook
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The bundled starter set; web-tests/starter-set.test.js checks it parses cleanly in abcjs. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StarterTunesTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repo: TuneRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = TuneRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun asset(): String = context.assets.open(StarterTunes.ASSET).bufferedReader().use { it.readText() }

    @Test
    fun `asset has 50 tunes, each titled, keyed, credited and with a rhythm`() {
        val tunes = AbcBook.split(asset())
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
    fun `adding the set imports every tune once`() = runBlocking {
        val starter = StarterTunes.fromAssets(context, repo)
        assertEquals(ImportResult(50, 0, emptyList()), starter.add())
        assertTrue(repo.tunes.first().all { it.source == TuneEntity.SOURCE_STARTER })
        assertEquals(ImportResult(0, 50, emptyList()), starter.add())
        assertEquals(50, repo.count.first())
    }

    @Test
    fun `the set covers the rhythm filters`() = runBlocking {
        StarterTunes.fromAssets(context, repo).add()
        assertEquals(
            setOf("reel", "jig", "slip jig", "slide", "hornpipe", "set dance", "march", "air"),
            repo.rhythms.first().toSet(),
        )
    }
}
