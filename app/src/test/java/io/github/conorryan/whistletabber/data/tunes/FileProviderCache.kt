package io.github.conorryan.whistletabber.data.tunes

import androidx.core.content.FileProvider

/**
 * FileProvider caches each authority's roots in a static map, but Robolectric
 * gives every test a new cache folder: clear it before a test shares a file.
 */
fun clearFileProviderCache() {
    val field = FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }
    val cache = field.get(null) as MutableMap<*, *>
    synchronized(cache) { cache.clear() }
}
