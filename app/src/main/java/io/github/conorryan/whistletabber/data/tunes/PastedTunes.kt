package io.github.conorryan.whistletabber.data.tunes

import android.content.ClipData
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat

/**
 * What the clipboard holds: an image (the scan screen reads it as tab),
 * `content:` files ([TuneImporter.importFiles]), or ABC text
 * ([TuneImporter.importText]) — the clipboard counterpart of [SharedTunes].
 */
data class PastedTunes(val image: Uri?, val files: List<Uri>, val text: String?) {
    companion object {
        /**
         * What [clip] holds, or null when it has nothing usable. A clip of
         * image MIME type (or an item shared as one) yields its first URI as
         * the [image]; any other `content:` URI is a file, such as an `.abc`
         * copied in a file manager; an item without a URI contributes its
         * text (a copied file's own text is its URI, so that isn't taken).
         */
        fun from(clip: ClipData?): PastedTunes? {
            if (clip == null) return null
            val imageMime = clip.description.hasMimeType("image/*")
            var image: Uri? = null
            val files = mutableListOf<Uri>()
            var text: String? = null
            for (i in 0 until clip.itemCount) {
                val item = clip.getItemAt(i)
                val uri = item.contentUri()
                when {
                    uri != null && (imageMime || item.intent?.type?.startsWith("image/") == true) ->
                        if (image == null) image = uri
                    uri != null -> files.add(uri)
                    else -> item.text?.toString()?.takeIf { it.isNotBlank() }?.let { if (text == null) text = it }
                }
            }
            return PastedTunes(image, files, text).takeIf { it.image != null || it.files.isNotEmpty() || it.text != null }
        }

        /** The item's URI, or the URI a share intent carries instead. */
        private fun ClipData.Item.contentUri(): Uri? = listOfNotNull(
            uri,
            intent?.let { IntentCompat.getParcelableExtra(it, Intent.EXTRA_STREAM, Uri::class.java) },
        ).firstOrNull { it.scheme == ContentResolver.SCHEME_CONTENT }
    }
}
