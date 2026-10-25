package io.github.conorryan.whistletabber.data.tunes

import io.github.conorryan.whistletabber.music.AbcBook
import io.github.conorryan.whistletabber.music.AbcHeaders
import java.text.Normalizer
import java.util.Locale

/** Index columns derived from a tune's ABC; pure, so the repository never lets them drift. */
object TuneIndex {

    private val MARKS = Regex("""\p{Mn}+""")
    private val SPACE = Regex("""\s+""")

    /** Case- and accent-insensitive form: "Ó Raghallaigh's" → "o raghallaigh's". */
    fun fold(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(MARKS, "")
            .lowercase(Locale.ROOT)
            .replace(SPACE, " ")
            .trim()

    fun sortKey(title: String): String = fold(title).removePrefix("the ")

    /**
     * The ABC as compared for duplicates: line endings, trailing space and the
     * `X:` number don't count.
     */
    fun sameMusic(a: String, b: String): Boolean = comparable(a) == comparable(b)

    private fun comparable(abc: String): List<String> = abc.lines()
        .map { it.trimEnd() }
        .filter { it.isNotEmpty() && !it.startsWith("X:") }

    /** [entity] with its index columns recomputed from [abc]. */
    fun apply(entity: TuneEntity, abc: String, headers: AbcHeaders = AbcBook.headers(abc)): TuneEntity {
        val title = headers.title.orEmpty()
        return entity.copy(
            abc = abc,
            title = title,
            altTitles = headers.alternateTitles.joinToString("\n"),
            searchKey = headers.titles.joinToString("\n") { fold(it) },
            sortKey = sortKey(title),
            rhythm = headers.rhythm?.let { fold(it) },
            keySignature = headers.key,
            meter = headers.meter,
            whistle = (headers.whistle ?: AbcBook.suggestWhistle(headers.key))?.name,
        )
    }

    /** `%`, `_` and the escape character itself, escaped for `LIKE … ESCAPE '\'`. */
    fun likePattern(query: String): String =
        "%" + fold(query).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
}
