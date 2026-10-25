package io.github.conorryan.whistletabber.render

import io.github.conorryan.whistletabber.web.BridgeCommand
import io.github.conorryan.whistletabber.web.BridgeEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/** In-memory [TunePage] for tests: records sent commands, emits events by hand. */
class FakeTunePage : TunePage {
    private val _events = MutableSharedFlow<BridgeEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<BridgeEvent> = _events.asSharedFlow()

    private val _commands = Channel<BridgeCommand>(Channel.UNLIMITED)
    private val sentList = mutableListOf<BridgeCommand>()

    /** Everything ever sent, in order. */
    val sent: List<BridgeCommand> get() = sentList.toList()

    /** The sent commands as a flow, so tests can await the next one. */
    val commands: Flow<BridgeCommand> = _commands.receiveAsFlow()

    override fun send(command: BridgeCommand) {
        sentList += command
        _commands.trySend(command)
    }

    suspend fun emit(event: BridgeEvent) {
        _events.emit(event)
    }
}
