package io.github.conorryan.whistletabber.data.tunes

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Schemas are exported to app/schemas/ and checked by AppDatabaseMigrationTest;
 * add a Migration (and a test for it) with every version bump.
 */
@Database(entities = [TuneEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tuneDao(): TuneDao

    companion object {
        const val NAME = "whistle-tabber.db"

        val MIGRATIONS = emptyArray<androidx.room.migration.Migration>()

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
