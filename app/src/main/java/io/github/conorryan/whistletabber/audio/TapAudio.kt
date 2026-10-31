package io.github.conorryan.whistletabber.audio

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.util.Log
import io.github.conorryan.whistletabber.music.WhistleSynth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** What the rhythm pad sounds while it is held. */
enum class PadSound { NOTE, TICK, SILENT }

/**
 * The tap editor's sound engine: one always-on, low-latency [PcmOutput]
 * (native AAudio where it opens, else an `AudioTrack`), fed one burst at a
 * time from a [TapMixer], while the screen is showing: [resume] then [open]
 * (off the main thread) on resume, [close] on pause, which frees the stream
 * for other apps. If the output dies under us — an exclusive stream is
 * disconnected whenever anything else plays, the WebView's preview
 * included — the writer thread says so ([alive] goes false) and the next
 * [open] starts a fresh stream.
 *
 * - [tap] sounds a palette tap ([prepareTaps] renders them ahead).
 * - [prepare] renders the rhythm pad's samples at the output's rate;
 *   [noteOn]/[noteOff] then only queue a note.
 * - [startMetronome]/[stopMetronome] around a pass of tapping; [relative]
 *   turns a touch event's `uptimeMillis` into ms from the first downbeat
 *   after the count-in, using the output's own timestamps.
 */
class TapAudio(
    context: Context,
    private val openOutput: () -> PcmOutput? = {
        openOutput(Build.VERSION.SDK_INT, { AAudioOutput.open() }, { AudioTrackOutput.open(context) })
    },
) {

    /** Debug builds log the output's setup, its delay once a second, and each press, under [TAG]. */
    private val logging = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    /** Samples, rendered at [samplesRate]: pad notes, palette taps and the pad's tick. */
    private val padNotes = ConcurrentHashMap<Int, ShortArray>()
    private val tapNotes = ConcurrentHashMap<Int, ShortArray>()
    @Volatile private var tick: ShortArray? = null
    private var samplesRate = 0
    @Volatile private var sound = PadSound.NOTE

    /** Between [resume] and [close]; an [open] that runs after a close does nothing. */
    @Volatile private var wanted = false
    @Volatile private var output: PcmOutput? = null
    @Volatile private var mixer: TapMixer? = null
    @Volatile private var running = false
    @Volatile private var framesWritten = 0L
    private var thread: Thread? = null
    @Volatile private var rate = 48_000
    @Volatile private var beatFrames = 1L
    @Volatile private var beatsPerBar = 1

    /** The screen is showing: [open] may open the output. Cheap, for the main thread. */
    fun resume() {
        wanted = true
    }

    /** Opens the output and starts it (silent until something plays), if [resume]d and not open yet. */
    suspend fun open() = withContext(Dispatchers.Default) { openNow() }

    @Synchronized
    private fun openNow() {
        if (!wanted || output != null) return
        val out = openOutput() ?: return
        rate = out.rate
        if (samplesRate != rate) {
            padNotes.clear()
            tapNotes.clear()
            tick = null
            samplesRate = rate
        }
        val m = TapMixer(rate, TapMixer.click(rate, TapMixer.ACCENT_HZ), TapMixer.click(rate, TapMixer.PLAIN_HZ))
        if (!out.start()) {
            out.release()
            return
        }
        output = out
        mixer = m
        framesWritten = 0
        running = true
        if (logging) {
            Log.d(TAG, "open path=${out.path} rate=$rate burst=${out.burst} buffer=${out.bufferFrames} capacity=${out.capacityFrames}")
        }
        // The thread owns the output from here: it alone writes, and it closes the output when it stops.
        thread = Thread({ write(out, m) }, "tap-audio").apply { start() }
    }

    private fun write(out: PcmOutput, m: TapMixer) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val chunk = ShortArray(out.burst)
        var underruns = out.underruns
        // One glitch can count as several underruns over the next few writes; grow once per glitch.
        var grownAt = -rate.toLong()
        try {
            while (running) {
                m.render(chunk)
                if (!out.write(chunk)) break
                framesWritten += chunk.size
                val now = out.underruns
                if (now > underruns && framesWritten - grownAt > rate / GROWTHS_PER_SECOND && out.bufferFrames < out.capacityFrames) {
                    val grown = out.setBufferFrames(out.bufferFrames + out.burst)
                    grownAt = framesWritten
                    if (logging) Log.d(TAG, "underrun $now: buffer grown to $grown frames")
                }
                underruns = now
                if (logging && m.clickStartFrame >= 0 && framesWritten % rate < chunk.size) {
                    Log.d(TAG, "status written=$framesWritten delay=${latencyMs()}ms buffer=${out.bufferFrames} underruns=$now")
                }
            }
        } finally {
            out.release()
            writerStopped(out)
        }
    }

    /**
     * The writer thread ended, because the output failed (an exclusive stream
     * is disconnected whenever anything else plays, the WebView included) or
     * because of [close]: forget it, so the next [open] starts a fresh stream
     * instead of queueing into a dead one.
     */
    @Synchronized
    private fun writerStopped(out: PcmOutput) {
        if (output === out) {
            running = false
            output = null
            mixer = null
            thread = null
        }
    }

    /** True while the output is open and its writer thread is alive. */
    val alive: Boolean get() = output != null && running

    /** Stops the output and frees it for other apps, until the next [resume]. */
    fun close() {
        val t: Thread?
        synchronized(this) {
            wanted = false
            running = false
            t = thread
            output = null
            mixer = null
            thread = null
        }
        t?.join(1_000)
    }

    /** Renders what the pad will play: each sounding pitch in [midis] for [PadSound.NOTE]. Opens the output if needed. */
    suspend fun prepare(midis: Set<Int>, sound: PadSound) = withContext(Dispatchers.Default) {
        openNow()
        this@TapAudio.sound = sound
        val started = SystemClock.uptimeMillis()
        when (sound) {
            PadSound.NOTE -> for (midi in midis) padNotes.getOrPut(midi) { note(midi, NOTE_SECONDS, TapMixer.NOTE_VOLUME) }
            PadSound.TICK -> if (tick == null) tick = TapMixer.click(rate, TapMixer.TICK_HZ, TapMixer.TICK_SECONDS)
            PadSound.SILENT -> Unit
        }
        if (logging) Log.d(TAG, "prepared ${midis.size} pad samples (${sound.name}) in ${SystemClock.uptimeMillis() - started} ms")
    }

    /** A whistle note with a near-instant, tongued start (a short tick mixed in), at [volume]. */
    private fun note(midi: Int, seconds: Double, volume: Float): ShortArray {
        val tone = TapMixer.faded(
            WhistleSynth.renderPcm(midi, sampleRate = rate, seconds = seconds, attackSeconds = NOTE_ATTACK),
            sampleRate = rate,
        )
        val onset = TapMixer.click(rate, TapMixer.TICK_HZ, TapMixer.ONSET_SECONDS)
        return TapMixer.withOnset(tone, volume, onset, TapMixer.ONSET_VOLUME)
    }

    /** Renders the palette taps of sounding pitches [midis] ahead, at the open output's rate. */
    suspend fun prepareTaps(midis: Set<Int>) = withContext(Dispatchers.Default) {
        if (output == null) return@withContext
        val started = SystemClock.uptimeMillis()
        for (midi in midis) tapNote(midi)
        if (logging) Log.d(TAG, "prepared ${midis.size} tap samples in ${SystemClock.uptimeMillis() - started} ms")
    }

    private fun tapNote(midi: Int): ShortArray = tapNotes.getOrPut(midi) { note(midi, TapMixer.TAP_SECONDS, TAP_VOLUME) }

    /** Sounds a palette tap of sounding pitch [midi] (rendered now if [prepareTaps] didn't). */
    fun tap(midi: Int) {
        val m = mixer ?: return
        m.noteOn(tapNote(midi), 1f)
    }

    /** Starts the metronome at [bpm], [beatsPerBar] to a bar, one count-in bar first. */
    fun startMetronome(bpm: Int, beatsPerBar: Int) {
        val m = mixer ?: return
        beatFrames = maxOf(1L, rate * 60L / bpm)
        this.beatsPerBar = beatsPerBar
        m.startClicks(beatFrames, beatsPerBar)
        if (logging) Log.d(TAG, "metronome bpm=$bpm beatsPerBar=$beatsPerBar sound=$sound")
    }

    fun stopMetronome() {
        val m = mixer ?: return
        m.stopClicks()
        m.noteOff()
    }

    /**
     * Sounds the pad for sounding pitch [midi] (prepared by [prepare]);
     * [eventUptimeMs], the touch event's time, is only for the debug log.
     */
    fun noteOn(midi: Int, eventUptimeMs: Long? = null) {
        val m = mixer ?: return
        if (logging) logPress("noteOn midi=$midi", eventUptimeMs, m)
        when (sound) {
            PadSound.NOTE -> padNotes[midi]?.let { m.noteOn(it, 1f) }
            PadSound.TICK -> tick?.let { m.noteOn(it, TapMixer.TICK_VOLUME) }
            PadSound.SILENT -> Unit
        }
    }

    fun noteOff(eventUptimeMs: Long? = null) {
        val m = mixer ?: return
        if (logging) logPress("noteOff", eventUptimeMs, m)
        m.noteOff()
    }

    /**
     * Logs a press: how late it was handled after the touch event, and about
     * when it will be heard (the mixer's next chunk, then the output delay).
     */
    private fun logPress(what: String, eventUptimeMs: Long?, m: TapMixer) {
        val now = SystemClock.uptimeMillis()
        val handled = eventUptimeMs?.let { now - it }
        val out = output
        val heard = out?.timestamp()?.let {
            // The next chunk starts at frame m.frame + the burst being written.
            it.nanos / 1_000_000 + (m.frame + out.burst - it.frame) * 1000 / rate - now
        }
        Log.d(TAG, "$what event=$eventUptimeMs fromDownbeat=${eventUptimeMs?.let(::relative)}ms handledAfter=${handled}ms heardIn≈${heard}ms")
    }

    /** The frame of beat 1 of the bar after the count-in; before the mixer starts the clicks, its next chunk is assumed. */
    internal fun downbeatFrame(): Long {
        val start = mixer?.clickStartFrame?.takeIf { it >= 0 } ?: framesWritten
        return start + beatFrames * beatsPerBar
    }

    /** [uptimeMs] as ms from beat 1 of the bar after the count-in. */
    fun relative(uptimeMs: Long): Long {
        val stamp = output?.timestamp() ?: return 0
        // The output's clock is CLOCK_MONOTONIC, like uptimeMillis.
        val downbeat = stamp.nanos / 1_000_000 + (downbeatFrame() - stamp.frame) * 1000 / rate
        return uptimeMs - downbeat
    }

    /** ms per beat at the running tempo. */
    val beatMs: Long get() = beatFrames * 1000 / rate

    /** What the output granted: its path, buffer and rate. */
    data class Info(val bufferFrames: Int, val burstFrames: Int, val rate: Int, val path: OutputPath, val underruns: Int)

    fun info(): Info? {
        val out = output?.takeIf { running } ?: return null
        return runCatching { Info(out.bufferFrames, out.burst, out.rate, out.path, out.underruns) }.getOrNull()
    }

    /** About how long a sample takes from being written to being heard, or null before the output reports. */
    fun latencyMs(): Int? {
        val stamp = output?.takeIf { running }?.timestamp() ?: return null
        val queued = (framesWritten - stamp.frame) * 1000.0 / rate
        val sinceStamp = (System.nanoTime() - stamp.nanos) / 1_000_000.0
        return (queued - sinceStamp).toInt().coerceAtLeast(0)
    }

    private companion object {
        const val TAG = "TapAudio"
        const val NOTE_SECONDS = 4.0
        const val NOTE_ATTACK = 0.001
        /** A palette tap, as loud as the old tap sound (there is no metronome to hear over). */
        const val TAP_VOLUME = 0.6f
        /** The buffer grows by one burst at most this often (every 100 ms). */
        const val GROWTHS_PER_SECOND = 10
    }
}
