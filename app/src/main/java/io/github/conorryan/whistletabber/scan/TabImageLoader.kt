package io.github.conorryan.whistletabber.scan

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Decodes a picked image into a [GrayImage], shrinking large photos so the
 * longest side is at most [MAX_SIDE] (tab diagrams stay many pixels wide).
 */
class TabImageLoader(private val resolver: ContentResolver) {

    /** Null when the image can't be read. */
    suspend fun load(uri: Uri): GrayImage? = withContext(Dispatchers.IO) {
        runCatching { decode(uri) }.getOrNull()?.let { bitmap ->
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            GrayImage.fromArgb(bitmap.width, bitmap.height, pixels).also { bitmap.recycle() }
        }
    }

    private fun decode(uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder also applies EXIF rotation from camera photos.
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longest = maxOf(info.size.width, info.size.height)
                if (longest > MAX_SIDE) {
                    val f = MAX_SIDE.toDouble() / longest
                    decoder.setTargetSize((info.size.width * f).toInt(), (info.size.height * f).toInt())
                }
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    companion object {
        const val MAX_SIDE = 2400
    }
}
