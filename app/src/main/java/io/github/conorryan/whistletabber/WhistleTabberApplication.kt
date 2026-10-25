package io.github.conorryan.whistletabber

import android.app.Application
import android.content.Context
import io.github.conorryan.whistletabber.data.settings.TabSettingsStore

/** Manual dependency graph; one instance per process, owned by the Application. */
class AppGraph(
    val tabSettingsStore: TabSettingsStore,
) {
    companion object {
        fun create(context: Context): AppGraph = AppGraph(
            tabSettingsStore = TabSettingsStore.create(context),
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
