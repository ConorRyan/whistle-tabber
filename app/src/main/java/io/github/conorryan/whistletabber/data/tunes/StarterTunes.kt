package io.github.conorryan.whistletabber.data.tunes

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The bundled O'Neill's starter set (built by scripts/build-starter-set.sh).
 * Adding it twice is harmless: the import skips tunes already stored.
 */
class StarterTunes(
    private val repository: TuneRepository,
    private val load: () -> String,
) {
    suspend fun add(): ImportResult {
        val text = withContext(Dispatchers.IO) { load() }
        return repository.importBook(text, TuneEntity.SOURCE_STARTER)
    }

    companion object {
        const val ASSET = "tunes/oneills.abc"

        fun readAsset(context: Context): String =
            context.assets.open(ASSET).bufferedReader().use { it.readText() }

        fun fromAssets(context: Context, repository: TuneRepository) =
            StarterTunes(repository) { readAsset(context) }
    }
}
