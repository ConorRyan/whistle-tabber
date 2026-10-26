package io.github.conorryan.whistletabber.data.tunes

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RecentlyDeletedTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: TuneRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = TuneRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun save(title: String): Long = repo.save("X:1\nT:$title\nK:D\nDEF|\n")

    @Test
    fun `delete removes the tune and offers it, and undo puts it back as it was`() = runBlocking {
        val id = save("Gone")
        repo.setStarred(id, true)
        val before = repo.get(id)!!
        val bin = RecentlyDeleted(repo, this)

        assertTrue(bin.delete(id))
        assertNull(repo.get(id))
        assertEquals(before, bin.offer.value)

        bin.undo(bin.offer.value!!)
        assertNull(bin.offer.value)
        assertEquals(before, withTimeout(5_000) { repo.observe(id).first { it != null } })
    }

    @Test
    fun `a tune is restored at most once`() = runBlocking {
        val id = save("Gone")
        val bin = RecentlyDeleted(repo, this)
        bin.delete(id)
        val tune = bin.offer.value!!
        bin.undo(tune)
        bin.undo(tune)
        assertEquals(1, withTimeout(5_000) { repo.count.first { it == 1 } })
    }

    @Test
    fun `undo after dismiss does nothing`() = runBlocking {
        val id = save("Gone")
        val bin = RecentlyDeleted(repo, this)
        bin.delete(id)
        val tune = bin.offer.value!!
        bin.dismiss(tune)
        assertNull(bin.offer.value)
        bin.undo(tune)
        assertEquals(0, repo.count.first())
    }

    @Test
    fun `a newer deletion replaces the offer, and answers about the older tune do nothing`() = runBlocking {
        val first = save("First")
        val second = save("Second")
        val bin = RecentlyDeleted(repo, this)
        bin.delete(first)
        val older = bin.offer.value!!
        bin.delete(second)
        assertEquals(second, bin.offer.value?.id)

        bin.undo(older)
        bin.dismiss(older)
        assertEquals(second, bin.offer.value?.id)
        assertEquals(0, repo.count.first())
    }

    @Test
    fun `deleting a tune that is already gone offers nothing`() = runBlocking {
        val bin = RecentlyDeleted(repo, this)
        assertFalse(bin.delete(42))
        assertNull(bin.offer.value)
    }
}
