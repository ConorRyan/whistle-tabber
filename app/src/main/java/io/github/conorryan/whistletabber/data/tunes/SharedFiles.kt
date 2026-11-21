package io.github.conorryan.whistletabber.data.tunes

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The `.abc` file handed to the share sheet. Only one exists at a time, in
 * `cache/shared/` (the only folder `res/xml/shared_files.xml` exposes): each
 * share clears the last one first, so the receiving app has finished with it
 * long before. Written as UTF-8, the same text an export writes, so the
 * receiving copy of Whistle Tabber imports it like any `.abc` file.
 */
class SharedFiles(private val context: Context) {

    /** Writes [text] as [name] and returns the `content:` URI to share. */
    suspend fun write(name: String, text: String): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, DIR)
        dir.deleteRecursively()
        dir.mkdirs()
        val file = File(dir, name)
        file.writeText(text, Charsets.UTF_8)
        FileProvider.getUriForFile(context, authority(context), file)
    }

    companion object {
        const val DIR = "shared"
        /** `.abc` files are plain text; messaging apps refuse rarer types such as text/vnd.abc. */
        const val MIME_TYPE = "text/plain"

        fun authority(context: Context): String = "${context.packageName}.files"

        /**
         * The share sheet for [uri], with [subject] for email and [exclude]
         * (our own "Share" target) left out.
         */
        fun chooser(uri: Uri, subject: String, exclude: ComponentName): Intent {
            val send = Intent(Intent.ACTION_SEND)
                .setType(MIME_TYPE)
                .putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_SUBJECT, subject)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // The grant travels with the ClipData to whichever app is picked.
            send.clipData = ClipData.newRawUri(subject, uri)
            return Intent.createChooser(send, null)
                .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(exclude))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
