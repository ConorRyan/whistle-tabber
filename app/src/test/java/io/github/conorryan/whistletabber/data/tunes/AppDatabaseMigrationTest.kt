package io.github.conorryan.whistletabber.data.tunes

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Harness for schema migrations, reading the schemas exported to app/schemas/.
 * With every version bump: add a test that creates the old version, fills it,
 * runs [AppDatabase.MIGRATIONS] and checks the rows survived.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppDatabaseMigrationTest {

    private val name = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun `v1 schema matches the exported schema`() {
        helper.createDatabase(name, 1).apply {
            execSQL(
                """INSERT INTO tunes (abc, title, altTitles, searchKey, sortKey, rhythm, keySignature, meter,
                   whistle, starred, source, createdAt, updatedAt)
                   VALUES ('X:1\nT:A\nK:D', 'A', '', 'a', 'a', NULL, 'D', NULL, 'D', 0, 'user', 1, 1)""",
            )
            close()
        }
        helper.runMigrationsAndValidate(name, 1, true, *AppDatabase.MIGRATIONS).use { db ->
            db.query("SELECT title FROM tunes").use { c ->
                c.moveToFirst()
                assertEquals("A", c.getString(0))
            }
        }
    }
}
