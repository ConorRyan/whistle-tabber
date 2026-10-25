package io.github.conorryan.whistletabber.web

import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import io.github.conorryan.whistletabber.music.WhistleSynth
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Serves "/sound/<instrument>-mp3/<Note>.mp3", the URL scheme abcjs uses for
 * soundfont samples, with WAV audio from [WhistleSynth]. The extension is
 * abcjs's; decodeAudioData sniffs the bytes, so WAV data decodes fine.
 */
class SoundPathHandler(
    private val render: (String) -> ByteArray? = WhistleSynth::wavForNote,
) : WebViewAssetLoader.PathHandler {
    private val cache = ConcurrentHashMap<String, ByteArray>()

    /** [path] is relative to "/sound/", e.g. "whistle-mp3/D5.mp3". */
    fun bytesFor(path: String): ByteArray? {
        val note = noteNameIn(path) ?: return null
        cache[note]?.let { return it }
        return render(note)?.also { cache[note] = it }
    }

    override fun handle(path: String): WebResourceResponse? {
        val bytes = bytesFor(path) ?: return null
        return WebResourceResponse("audio/wav", null, ByteArrayInputStream(bytes))
    }

    companion object {
        private val PATH = Regex("^[a-z0-9_]+-mp3/([A-G]b?-?\\d)\\.mp3$")

        /** Every instrument gets the whistle sound; only the note name matters. */
        fun noteNameIn(path: String): String? = PATH.matchEntire(path)?.groupValues?.get(1)
    }
}
