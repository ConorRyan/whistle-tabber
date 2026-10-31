package io.github.conorryan.whistletabber.audio

/** The JNI side of `app/src/main/cpp/tap_audio.c`. */
internal object NativeTapStream {
    val loaded: Boolean = runCatching { System.loadLibrary("tapaudio") }.isSuccess

    // Property numbers, matching tap_audio.c.
    const val RATE = 0
    const val BURST = 1
    const val BUFFER = 2
    const val CAPACITY = 3
    const val XRUNS = 4
    const val SHARING = 5

    /** AAudio's AAUDIO_SHARING_MODE_EXCLUSIVE. */
    const val SHARING_EXCLUSIVE = 0

    @JvmStatic external fun nativeOpen(): Long
    @JvmStatic external fun nativeStart(handle: Long): Boolean
    @JvmStatic external fun nativeWrite(handle: Long, data: ShortArray, frames: Int, timeoutNanos: Long): Int
    @JvmStatic external fun nativeTimestamp(handle: Long, out: LongArray): Boolean
    @JvmStatic external fun nativeProperty(handle: Long, which: Int): Int
    @JvmStatic external fun nativeSetBufferSize(handle: Long, frames: Int): Int
    @JvmStatic external fun nativeClose(handle: Long)
}

/**
 * AAudio in low-latency, exclusive-if-possible mode, through [NativeTapStream].
 * Only the writing thread calls [write]; everything else, [release] included,
 * is synchronized so the stream is never used after it is closed.
 */
class AAudioOutput private constructor(private var handle: Long) : PcmOutput {

    override val rate: Int = NativeTapStream.nativeProperty(handle, NativeTapStream.RATE)
    override val burst: Int = NativeTapStream.nativeProperty(handle, NativeTapStream.BURST).coerceAtLeast(MIN_BURST)
    override val path: OutputPath =
        if (NativeTapStream.nativeProperty(handle, NativeTapStream.SHARING) == NativeTapStream.SHARING_EXCLUSIVE) {
            OutputPath.AAUDIO_EXCLUSIVE
        } else {
            OutputPath.AAUDIO_SHARED
        }
    private val stamp = LongArray(2)

    init {
        // Two bursts to start with; TapAudio adds a burst whenever the stream underruns.
        NativeTapStream.nativeSetBufferSize(handle, burst * 2)
    }

    override val bufferFrames: Int get() = property(NativeTapStream.BUFFER)
    override val capacityFrames: Int get() = property(NativeTapStream.CAPACITY)
    override val underruns: Int get() = property(NativeTapStream.XRUNS)

    @Synchronized
    private fun property(which: Int): Int = if (handle == 0L) 0 else NativeTapStream.nativeProperty(handle, which)

    @Synchronized
    override fun start(): Boolean = handle != 0L && NativeTapStream.nativeStart(handle)

    override fun write(chunk: ShortArray): Boolean =
        handle != 0L && NativeTapStream.nativeWrite(handle, chunk, chunk.size, WRITE_TIMEOUT_NANOS) >= 0

    @Synchronized
    override fun setBufferFrames(frames: Int): Int =
        if (handle == 0L) 0 else NativeTapStream.nativeSetBufferSize(handle, frames).let { if (it < 0) bufferFrames else it }

    @Synchronized
    override fun timestamp(): FrameTime? {
        if (handle == 0L || !NativeTapStream.nativeTimestamp(handle, stamp)) return null
        return FrameTime(stamp[0], stamp[1])
    }

    @Synchronized
    override fun release() {
        if (handle != 0L) NativeTapStream.nativeClose(handle)
        handle = 0L
    }

    companion object {
        private const val WRITE_TIMEOUT_NANOS = 100_000_000L
        /** A JNI call per burst; below this, write fewer, larger chunks. */
        private const val MIN_BURST = 64

        /** The stream, or null if the library or AAudio isn't there. */
        fun open(): AAudioOutput? {
            if (!NativeTapStream.loaded) return null
            val handle = NativeTapStream.nativeOpen()
            return if (handle == 0L) null else AAudioOutput(handle)
        }
    }
}
