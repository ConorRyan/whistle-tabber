package io.github.conorryan.whistletabber.render

import io.github.conorryan.whistletabber.web.BridgeCommand
import io.github.conorryan.whistletabber.web.BridgeEvent
import kotlinx.coroutines.flow.SharedFlow

/**
 * The page as the render cycle sees it: commands in, events out. Two adapters
 * exist: [io.github.conorryan.whistletabber.web.TuneWebController] (the real
 * WebView page) and an in-memory fake in unit tests. The JSON protocol lives
 * in `web/BridgeProtocol.kt`; change it there, never here.
 */
interface TunePage {
    /**
     * Fire-and-forget. Safe before the page reports ready: the adapter queues
     * (keeping only the newest Render).
     */
    fun send(command: BridgeCommand)

    /** Page events, no replay; the renderer subscribes on attach. */
    val events: SharedFlow<BridgeEvent>
}
