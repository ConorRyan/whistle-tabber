package io.github.conorryan.whistletabber.web

import io.github.conorryan.whistletabber.music.Hole
import io.github.conorryan.whistletabber.music.NoteFingering
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Messages between the app and assets/web/whistle-page.js. JSON with a "type"
 * discriminator on both sides; change the two files together.
 */
@Serializable
sealed interface BridgeCommand {
    @Serializable
    @SerialName("render")
    data class Render(
        val abc: String,
        val visualTranspose: Int = 0,
        /** Written-to-played semitones, e.g. +12 for a soprano whistle. */
        val midiTranspose: Int = 0,
    ) : BridgeCommand

    /** Fingering diagrams by note index (as in [BridgeEvent.Rendered]); null draws nothing. */
    @Serializable
    @SerialName("diagrams")
    data class Diagrams(val diagrams: List<DiagramSpec?>) : BridgeCommand

    @Serializable
    @SerialName("play")
    data object Play : BridgeCommand

    @Serializable
    @SerialName("pause")
    data object Pause : BridgeCommand

    @Serializable
    @SerialName("stop")
    data object Stop : BridgeCommand

    @Serializable
    @SerialName("seek")
    data class Seek(val fraction: Double) : BridgeCommand

    @Serializable
    @SerialName("tempo")
    data class Tempo(val percent: Int) : BridgeCommand
}

@Serializable
sealed interface BridgeEvent {
    @Serializable
    @SerialName("ready")
    data class Ready(val abcjsVersion: String) : BridgeEvent

    @Serializable
    @SerialName("rendered")
    data class Rendered(val notes: List<NoteEvent>, val warnings: List<String>) : BridgeEvent

    @Serializable
    @SerialName("audioLoaded")
    data class AudioLoaded(val loaded: Int, val errors: List<String>) : BridgeEvent

    @Serializable
    @SerialName("playing")
    data class Playing(val startChar: Int? = null, val measure: Int? = null) : BridgeEvent

    @Serializable
    @SerialName("finished")
    data object Finished : BridgeEvent

    @Serializable
    @SerialName("error")
    data class Error(val message: String) : BridgeEvent
}

/** One written note of the tune's first voice, as WhistleCore.extractNotes reports it. */
@Serializable
data class NoteEvent(
    val index: Int,
    val startChar: Int,
    val endChar: Int,
    val midi: Int?,
    val bar: Int,
    val startWhole: Double?,
    val durationWhole: Double?,
    val tieContinuation: Boolean,
)

/**
 * One diagram as the page draws it.
 *
 * @property holes six characters from the mouthpiece down: C closed, O open, H half
 * @property octave 0, 1 ("+") or 2 ("++")
 * @property folded the note was moved by this many octaves to fit the whistle
 */
@Serializable
data class DiagramSpec(
    val holes: String = "",
    val octave: Int = 0,
    val hard: Boolean = false,
    val folded: Int = 0,
    val outOfRange: Boolean = false,
) {
    companion object {
        fun from(fingering: NoteFingering?): DiagramSpec? = when (fingering) {
            null -> null
            is NoteFingering.OutOfRange -> DiagramSpec(outOfRange = true)
            is NoteFingering.Playable -> DiagramSpec(
                holes = fingering.fingering.holes.joinToString("") {
                    when (it) {
                        Hole.CLOSED -> "C"
                        Hole.OPEN -> "O"
                        Hole.HALF -> "H"
                    }
                },
                octave = fingering.fingering.octave,
                hard = fingering.fingering.hard,
                folded = fingering.folded,
            )
        }
    }
}

object BridgeCodec {
    private val json = Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(command: BridgeCommand): String = json.encodeToString(BridgeCommand.serializer(), command)

    /** Null for messages the app doesn't understand (logged, never fatal). */
    fun decode(message: String): BridgeEvent? =
        runCatching { json.decodeFromString(BridgeEvent.serializer(), message) }.getOrNull()

    /**
     * JS statement delivering [command] to the page. JSON is a valid JS
     * expression, so the encoded object is passed as a literal.
     */
    fun script(command: BridgeCommand): String = "window.whistlePage.handle(${encode(command)});"
}
