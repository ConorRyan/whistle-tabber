package io.github.conorryan.whistletabber

import android.app.Application
import android.content.Context
import io.github.conorryan.whistletabber.data.settings.TabSettingsStore
import io.github.conorryan.whistletabber.data.tunes.AbcFiles
import io.github.conorryan.whistletabber.data.tunes.AppDatabase
import io.github.conorryan.whistletabber.data.tunes.IncomingTunes
import io.github.conorryan.whistletabber.data.tunes.RecentlyDeleted
import io.github.conorryan.whistletabber.data.tunes.TuneImporter
import io.github.conorryan.whistletabber.data.tunes.TuneRepository
import io.github.conorryan.whistletabber.scan.TabImageLoader
import io.github.conorryan.whistletabber.ui.settings.TabSettingsEditor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency graph; one instance per process, owned by the Application. */
class AppGraph(
    val tabSettingsStore: TabSettingsStore,
    db: AppDatabase,
    loadStarterSet: () -> String,
    val abcFiles: AbcFiles,
    val tabImageLoader: TabImageLoader,
) {
    val tuneRepository = TuneRepository(db)
    val tuneImporter = TuneImporter(tuneRepository, abcFiles, loadStarterSet)

    /** Application-lived scope for work that must outlive a screen, like an import. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Delete with undo: the tune screen deletes, the library offers Undo. */
    val recentlyDeleted = RecentlyDeleted(tuneRepository, appScope)

    /** Files and text shared from other apps: MainActivity receives, the library reports. */
    val incomingTunes = IncomingTunes(tuneImporter, appScope)

    /** One owner for tab-settings writes from the UI (both settings surfaces). */
    val tabSettingsEditor = TabSettingsEditor(tabSettingsStore, appScope)

    companion object {
        fun create(context: Context): AppGraph = AppGraph(
            tabSettingsStore = TabSettingsStore.create(context),
            db = AppDatabase.build(context),
            loadStarterSet = { TuneImporter.readStarterSet(context) },
            abcFiles = AbcFiles(context.contentResolver),
            tabImageLoader = TabImageLoader(context.contentResolver),
        )
    }
}

class WhistleTabberApplication : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph.create(this)
    }
}
