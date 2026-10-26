package io.github.conorryan.whistletabber.data.tunes

import androidx.room.withTransaction
import io.github.conorryan.whistletabber.data.enumByName
import io.github.conorryan.whistletabber.music.AbcBook
import io.github.conorryan.whistletabber.music.AbcProblem
import io.github.conorryan.whistletabber.music.WhistleKey
import io.github.conorryan.whistletabber.music.problems
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** A stored tune as the UI sees it. */
data class Tune(
    val id: Long,
    val abc: String,
    val title: String,
    val alternateTitles: List<String>,
    val rhythm: String?,
    val key: String?,
    val meter: String?,
    /** The whistle the tune is written for, if its directive or key says. */
    val whistle: WhistleKey?,
    val starred: Boolean,
    val source: String,
    val createdAt: Long,
    val updatedAt: Long,
) {
    /** The whistle named by the tune's own directive, if any; [whistle] is that, else its key's suggestion. */
    val explicitWhistle: WhistleKey? get() = AbcBook.whistleOf(abc)
}

/** [unreadable] files could not be opened or decoded at all. */
data class ImportResult(
    val added: Int,
    val skipped: Int,
    val errors: List<ImportError>,
    val unreadable: Int = 0,
) {
    val total: Int get() = added + skipped + errors.size

    operator fun plus(other: ImportResult) = ImportResult(
        added + other.added, skipped + other.skipped, errors + other.errors, unreadable + other.unreadable,
    )

    companion object {
        val NONE = ImportResult(0, 0, emptyList())
        val UNREADABLE_FILE = ImportResult(0, 0, emptyList(), unreadable = 1)
    }
}

/** A tune that could not be imported; [position] counts from 1 in its file. */
data class ImportError(val position: Int, val title: String?, val problem: AbcProblem)

/**
 * The only way from the UI to stored tunes. Every write goes through
 * [TuneIndex.apply], so the index columns always match the ABC.
 */
class TuneRepository(
    private val db: AppDatabase,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.tuneDao()

    val tunes: Flow<List<Tune>> = dao.observeAll().map { it.map(::toTune) }
    val count: Flow<Int> = dao.observeCount()
    val rhythms: Flow<List<String>> = dao.observeRhythms()

    fun search(query: String, rhythm: String? = null, starredOnly: Boolean = false): Flow<List<Tune>> =
        dao.search(TuneIndex.likePattern(query), rhythm?.let(TuneIndex::fold), starredOnly)
            .map { it.map(::toTune) }

    fun observe(id: Long): Flow<Tune?> = dao.observe(id).map { it?.let(::toTune) }

    suspend fun get(id: Long): Tune? = dao.get(id)?.let(::toTune)

    /**
     * Inserts [abc] as a new tune when [id] is null, otherwise replaces that
     * tune's ABC (keeping its star, source and creation time). Returns the id.
     */
    suspend fun save(abc: String, id: Long? = null, source: String = TuneEntity.SOURCE_USER): Long {
        val headers = AbcBook.headers(abc)
        require(headers.problems().isEmpty()) { "ABC has no T: or K: line" }
        val time = now()
        val existing = id?.let { dao.get(it) }
        return if (existing == null) {
            val entity = TuneIndex.apply(blank(source, time), abc, headers)
            dao.insert(if (id != null) entity.copy(id = id) else entity)
        } else {
            dao.update(TuneIndex.apply(existing.copy(updatedAt = time), abc, headers))
            existing.id
        }
    }

    suspend fun setStarred(id: Long, starred: Boolean) = dao.setStarred(id, starred)

    /** Sets or clears the tune's `%whistle_tab_key` directive. */
    suspend fun setWhistle(id: Long, whistle: WhistleKey?) {
        val tune = dao.get(id) ?: return
        save(AbcBook.withWhistle(tune.abc, whistle), id)
    }

    /** Deletes the tune and returns it, so the caller can offer [restore]. */
    suspend fun delete(id: Long): Tune? {
        val tune = dao.get(id) ?: return null
        dao.delete(id)
        return toTune(tune)
    }

    /** Puts back a tune returned by [delete], with its id, star and dates. */
    suspend fun restore(tune: Tune) {
        dao.insert(toEntity(tune))
    }

    /**
     * Adds every tune in [text] (one or many). A tune with the same title and
     * the same music as one already stored (or earlier in [text]) is skipped;
     * the same title with different music is added. One transaction.
     */
    suspend fun importBook(text: String, source: String = TuneEntity.SOURCE_IMPORT): ImportResult =
        db.withTransaction {
            val time = now()
            val errors = mutableListOf<ImportError>()
            val toAdd = mutableListOf<TuneEntity>()
            var skipped = 0
            AbcBook.split(text).forEachIndexed { i, abc ->
                val headers = AbcBook.headers(abc)
                val title = headers.title
                when (headers.problems().firstOrNull()) {
                    AbcProblem.NO_TITLE -> errors += ImportError(i + 1, null, AbcProblem.NO_TITLE)
                    AbcProblem.NO_KEY -> errors += ImportError(i + 1, title, AbcProblem.NO_KEY)
                    null -> when {
                        // No problems, so [title] is there.
                        (dao.findByTitle(title!!) + toAdd.filter { it.title == title })
                            .any { TuneIndex.sameMusic(it.abc, abc) } -> skipped++
                        else -> toAdd += TuneIndex.apply(blank(source, time), abc, headers)
                    }
                }
            }
            dao.insertAll(toAdd)
            ImportResult(added = toAdd.size, skipped = skipped, errors = errors)
        }

    /** The whole library as one tune book, sorted by title. */
    suspend fun exportAll(): String = AbcBook.join(dao.getAll().map { it.abc })

    suspend fun export(id: Long): String? = dao.get(id)?.let { AbcBook.join(listOf(it.abc)) }

    private fun blank(source: String, time: Long) = TuneEntity(
        abc = "", title = "", altTitles = "", searchKey = "", sortKey = "",
        rhythm = null, keySignature = null, meter = null, whistle = null,
        source = source, createdAt = time, updatedAt = time,
    )

    private fun toTune(e: TuneEntity) = Tune(
        id = e.id,
        abc = e.abc,
        title = e.title,
        alternateTitles = e.altTitles.lines().filter { it.isNotEmpty() },
        rhythm = e.rhythm,
        key = e.keySignature,
        meter = e.meter,
        whistle = enumByName<WhistleKey>(e.whistle),
        starred = e.starred,
        source = e.source,
        createdAt = e.createdAt,
        updatedAt = e.updatedAt,
    )

    private fun toEntity(t: Tune) = TuneIndex.apply(
        blank(t.source, t.createdAt).copy(id = t.id, starred = t.starred, updatedAt = t.updatedAt),
        t.abc,
    )
}
