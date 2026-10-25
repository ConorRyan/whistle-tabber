package io.github.conorryan.whistletabber

import android.app.Application
import android.content.Context
import io.github.conorryan.whistletabber.data.settings.TabSettingsStore
import io.github.conorryan.whistletabber.data.tunes.AbcFiles
import io.github.conorryan.whistletabber.data.tunes.AppDatabase
import io.github.conorryan.whistletabber.data.tunes.RecentlyDeleted
import io.github.conorryan.whistletabber.data.tunes.StarterTunes
import io.github.conorryan.whistletabber.data.tunes.TuneRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency graph; one instance per process, owned by the Application. */
class AppGraph(
    val tabSettingsStore: TabSettingsStore,
    db: AppDatabase,
    loadStarterSet: () -> String,
    val abcFiles: AbcFiles,
) {
    val tuneRepository = TuneRepository(db)
    val starterTunes = StarterTunes(tuneRepository, loadStarterSet)
    val recentlyDeleted = RecentlyDeleted()

    /** Application-lived scope for work that must outlive a screen, like an import. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        fun create(context: Context): AppGraph = AppGraph(
            tabSettingsStore = TabSettingsStore.create(context),
            db = AppDatabase.build(context),
            loadStarterSet = { StarterTunes.readAsset(context) },
            abcFiles = AbcFiles(context.contentResolver),
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
