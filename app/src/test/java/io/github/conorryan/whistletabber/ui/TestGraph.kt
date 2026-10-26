package io.github.conorryan.whistletabber.ui

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.conorryan.whistletabber.data.settings.TabSettingsStore
import io.github.conorryan.whistletabber.data.tunes.AbcFiles
import io.github.conorryan.whistletabber.data.tunes.AppDatabase
import io.github.conorryan.whistletabber.data.tunes.RecentlyDeleted
import io.github.conorryan.whistletabber.data.tunes.TuneImporter
import io.github.conorryan.whistletabber.data.tunes.TuneRepository
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/** Real data layer for ViewModel tests: in-memory Room and a temp-file DataStore. Robolectric only. */
class TestGraph(dir: File) {
    val context: Context = ApplicationProvider.getApplicationContext()
    val db: AppDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    val repository = TuneRepository(db)
    val store = TabSettingsStore(
        PreferenceDataStoreFactory.create(produceFile = { File(dir, "tab.preferences_pb") }),
    )
    val files = AbcFiles(context.contentResolver)
    /** Stands in for the app scope; cancelled by [close]. */
    val appScope = CoroutineScope(SupervisorJob())
    val recentlyDeleted = RecentlyDeleted(repository, appScope)
    val importer = TuneImporter(repository, files) { TuneImporter.readStarterSet(context) }

    fun close() {
        appScope.cancel()
        db.close()
    }
}
