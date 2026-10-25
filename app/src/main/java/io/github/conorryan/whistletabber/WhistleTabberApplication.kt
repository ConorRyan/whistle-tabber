package io.github.conorryan.whistletabber

import android.app.Application
import android.content.Context
import io.github.conorryan.whistletabber.data.settings.TabSettingsStore
import io.github.conorryan.whistletabber.data.tunes.AppDatabase
import io.github.conorryan.whistletabber.data.tunes.TuneRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency graph; one instance per process, owned by the Application. */
class AppGraph(
    val tabSettingsStore: TabSettingsStore,
    db: AppDatabase,
) {
    val tuneRepository = TuneRepository(db)

    /** Application-lived scope for work that must outlive a screen, like an import. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        fun create(context: Context): AppGraph = AppGraph(
            tabSettingsStore = TabSettingsStore.create(context),
            db = AppDatabase.build(context),
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
