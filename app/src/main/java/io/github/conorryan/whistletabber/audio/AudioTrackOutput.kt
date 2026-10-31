package io.github.conorryan.whistletabber.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTimestamp
import android.media.AudioTrack

/** The Java fallback: a streaming [AudioTrack] asking for the low-latency path at the native rate. */
class AudioTrackOutput private constructor(
    private val track: AudioTrack,
    override val rate: Int,
    override val burst: Int,
) : PcmOutput {

    override val path: OutputPath =
        if (track.performanceMode == AudioTrack.PERFORMANCE_MODE_LOW_LATENCY) OutputPath.AUDIO_TRACK_FAST else OutputPath.AUDIO_TRACK_NORMAL
    private val stamp = AudioTimestamp()

    init {
        // The primary mixer's period is usually several bursts (480 frames on a Pixel 7a,
        // whose burst reads 128), so start at four; TapAudio grows it on underruns.
        runCatching { track.setBufferSizeInFrames(burst * 4) }
    }

    override val bufferFrames: Int get() = track.bufferSizeInFrames
    override val capacityFrames: Int get() = track.bufferCapacityInFrames
    override val underruns: Int get() = track.underrunCount

    override fun start(): Boolean = runCatching { track.play() }.isSuccess

    override fun write(chunk: ShortArray): Boolean = track.write(chunk, 0, chunk.size) >= 0

    override fun setBufferFrames(frames: Int): Int = track.setBufferSizeInFrames(frames).let { if (it < 0) bufferFrames else it }

    override fun timestamp(): FrameTime? =
        if (runCatching { track.getTimestamp(stamp) }.getOrDefault(false)) FrameTime(stamp.framePosition, stamp.nanoTime) else null

    override fun release() {
        runCatching { track.stop() }
        track.release()
    }

    companion object {
        private const val DEFAULT_BURST = 256

        fun open(context: Context): AudioTrackOutput? {
            val audioManager = context.getSystemService(AudioManager::class.java)
            // A track at any other rate is resampled and loses the fast path.
            val rate = audioManager?.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull()?.takeIf { it > 0 }
                ?: AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC)
            val burst = audioManager?.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull()?.takeIf { it > 0 }
                ?: DEFAULT_BURST
            val minBytes = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val track = runCatching {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(rate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    )
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                    .setBufferSizeInBytes(maxOf(minBytes, burst * 8 * 2))
                    .build()
            }.getOrNull() ?: return null
            return AudioTrackOutput(track, rate, burst)
        }
    }
}
