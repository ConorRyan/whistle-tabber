package io.github.conorryan.whistletabber.ui.tap

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import io.github.conorryan.whistletabber.music.WhistleSynth

/**
 * Sounds one short whistle note when a fingering is tapped, natively (the
 * page would need a render first). One note at a time: a new note stops the
 * last. [release] when the screen goes.
 */
class NotePlayer {

    private var track: AudioTrack? = null
    private val samples = mutableMapOf<Int, ShortArray>()

    /** Plays sounding pitch [midi]. */
    fun play(midi: Int) {
        val pcm = samples.getOrPut(midi) { faded(WhistleSynth.renderPcm(midi, seconds = SECONDS)) }
        release()
        track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(WhistleSynth.SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size * 2)
                .build()
                .also {
                    it.write(pcm, 0, pcm.size)
                    it.play()
                }
        }.getOrNull()
    }

    fun release() {
        track?.release()
        track = null
    }

    companion object {
        const val SECONDS = 0.4
        private const val FADE_SECONDS = 0.05

        /** [pcm] with its last [FADE_SECONDS] faded to silence, so a short note doesn't click. */
        internal fun faded(pcm: ShortArray, sampleRate: Int = WhistleSynth.SAMPLE_RATE): ShortArray {
            val fade = minOf(pcm.size, (sampleRate * FADE_SECONDS).toInt())
            val start = pcm.size - fade
            return ShortArray(pcm.size) { i ->
                if (i < start) pcm[i] else (pcm[i] * (pcm.size - 1 - i).toFloat() / fade).toInt().toShort()
            }
        }
    }
}
