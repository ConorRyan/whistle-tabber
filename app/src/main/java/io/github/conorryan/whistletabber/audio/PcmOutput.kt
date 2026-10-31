package io.github.conorryan.whistletabber.audio

import android.os.Build

/** Which way the rhythm tapper's audio reaches the speaker. */
enum class OutputPath { AAUDIO_EXCLUSIVE, AAUDIO_SHARED, AUDIO_TRACK_FAST, AUDIO_TRACK_NORMAL }

/** A frame position and the CLOCK_MONOTONIC time it was presented. */
data class FrameTime(val frame: Long, val nanos: Long)

/**
 * A mono 16-bit output that [TapAudio] writes one [burst] at a time from its
 * own thread. Opened before the count-in (so samples can be rendered at its
 * [rate]), then [start]ed.
 */
interface PcmOutput {
    val rate: Int
    val burst: Int
    val path: OutputPath
    val bufferFrames: Int
    val capacityFrames: Int
    val underruns: Int

    fun start(): Boolean

    /** Writes [chunk], blocking until it fits; false when the output has failed. */
    fun write(chunk: ShortArray): Boolean

    /** Sets the buffer to [frames], returning the size actually set. */
    fun setBufferFrames(frames: Int): Int

    fun timestamp(): FrameTime?

    fun release()
}

/**
 * The best output this device has: AAudio (its low-latency path, reached
 * only from native code) on Android 8.1+, else, or if it doesn't open,
 * [AudioTrackOutput].
 */
internal fun openOutput(sdk: Int, aaudio: () -> PcmOutput?, audioTrack: () -> PcmOutput?): PcmOutput? =
    (if (sdk >= Build.VERSION_CODES.O_MR1) runCatching(aaudio).getOrNull() else null)
        ?: runCatching(audioTrack).getOrNull()
