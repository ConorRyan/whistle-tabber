// A low-latency AAudio output for the rhythm tapper (audio/NativeTapStream in AAudioOutput.kt).
//
// This only moves PCM: the Kotlin TapMixer renders every chunk, and a Kotlin
// thread hands it here with a blocking write. AAudio in low-latency,
// exclusive mode reaches the device's fast (MMAP) path, which Java's
// AudioTrack can't; on the Pixel 7a that is ~6 ms of output delay against
// ~60 ms for AudioTrack.

#include <aaudio/AAudio.h>
#include <android/log.h>
#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <time.h>

#define TAG "TapAudio"
#define FN(name) Java_io_github_conorryan_whistletabber_audio_NativeTapStream_##name

typedef struct {
    AAudioStream *stream;
    int16_t *buffer;     // a copy of the chunk, so no JVM array is held while blocked
    int32_t capacity;    // in frames (mono)
} TapStream;

static TapStream *from(jlong handle) { return (TapStream *) (intptr_t) handle; }

// The properties nativeProperty reports, matching NativeTapStream in AAudioOutput.kt.
enum {
    PROP_RATE = 0,
    PROP_BURST = 1,
    PROP_BUFFER = 2,
    PROP_CAPACITY = 3,
    PROP_XRUNS = 4,
    PROP_SHARING = 5,
    PROP_PERFORMANCE = 6,
};

JNIEXPORT jlong JNICALL FN(nativeOpen)(JNIEnv *env, jclass cls) {
    (void) env;
    (void) cls;
    AAudioStreamBuilder *builder = NULL;
    if (AAudio_createStreamBuilder(&builder) != AAUDIO_OK) return 0;
    AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_OUTPUT);
    AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    // A request: AAudio falls back to shared if exclusive isn't available.
    AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_EXCLUSIVE);
    AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_I16);
    AAudioStreamBuilder_setChannelCount(builder, 1);
    // No sample rate: the device's own rate avoids a resampler.

    AAudioStream *stream = NULL;
    aaudio_result_t result = AAudioStreamBuilder_openStream(builder, &stream);
    AAudioStreamBuilder_delete(builder);
    if (result != AAUDIO_OK) {
        __android_log_print(ANDROID_LOG_WARN, TAG, "AAudio open failed: %s", AAudio_convertResultToText(result));
        return 0;
    }
    TapStream *t = calloc(1, sizeof *t);
    if (t == NULL) {
        AAudioStream_close(stream);
        return 0;
    }
    t->stream = stream;
    return (jlong) (intptr_t) t;
}

JNIEXPORT jboolean JNICALL FN(nativeStart)(JNIEnv *env, jclass cls, jlong handle) {
    (void) env;
    (void) cls;
    return AAudioStream_requestStart(from(handle)->stream) == AAUDIO_OK;
}

// Writes the first `frames` samples of `data`, blocking up to `timeoutNanos`.
// Returns the frames written, or a negative AAudio error.
JNIEXPORT jint JNICALL FN(nativeWrite)(JNIEnv *env, jclass cls, jlong handle, jshortArray data, jint frames, jlong timeoutNanos) {
    (void) cls;
    TapStream *t = from(handle);
    if (frames > t->capacity) {
        int16_t *grown = realloc(t->buffer, (size_t) frames * sizeof(int16_t));
        if (grown == NULL) return AAUDIO_ERROR_NO_MEMORY;
        t->buffer = grown;
        t->capacity = frames;
    }
    (*env)->GetShortArrayRegion(env, data, 0, frames, t->buffer);
    return AAudioStream_write(t->stream, t->buffer, frames, timeoutNanos);
}

// The frame most recently presented and when, on CLOCK_MONOTONIC (as uptimeMillis).
JNIEXPORT jboolean JNICALL FN(nativeTimestamp)(JNIEnv *env, jclass cls, jlong handle, jlongArray out) {
    (void) cls;
    int64_t frame = 0;
    int64_t nanos = 0;
    if (AAudioStream_getTimestamp(from(handle)->stream, CLOCK_MONOTONIC, &frame, &nanos) != AAUDIO_OK) return JNI_FALSE;
    jlong values[2] = {frame, nanos};
    (*env)->SetLongArrayRegion(env, out, 0, 2, values);
    return JNI_TRUE;
}

JNIEXPORT jint JNICALL FN(nativeProperty)(JNIEnv *env, jclass cls, jlong handle, jint which) {
    (void) env;
    (void) cls;
    AAudioStream *s = from(handle)->stream;
    switch (which) {
        case PROP_RATE: return AAudioStream_getSampleRate(s);
        case PROP_BURST: return AAudioStream_getFramesPerBurst(s);
        case PROP_BUFFER: return AAudioStream_getBufferSizeInFrames(s);
        case PROP_CAPACITY: return AAudioStream_getBufferCapacityInFrames(s);
        case PROP_XRUNS: return AAudioStream_getXRunCount(s);
        case PROP_SHARING: return AAudioStream_getSharingMode(s);
        case PROP_PERFORMANCE: return AAudioStream_getPerformanceMode(s);
        default: return -1;
    }
}

// Returns the buffer size actually set, or a negative AAudio error.
JNIEXPORT jint JNICALL FN(nativeSetBufferSize)(JNIEnv *env, jclass cls, jlong handle, jint frames) {
    (void) env;
    (void) cls;
    return AAudioStream_setBufferSizeInFrames(from(handle)->stream, frames);
}

JNIEXPORT void JNICALL FN(nativeClose)(JNIEnv *env, jclass cls, jlong handle) {
    (void) env;
    (void) cls;
    TapStream *t = from(handle);
    AAudioStream_requestStop(t->stream);
    AAudioStream_close(t->stream);
    free(t->buffer);
    free(t);
}
