package io.github.conorryan.whistletabber.music

/** The header fields of one tune; values are trimmed, `%` comments removed. */
data class AbcHeaders(
    val x: String? = null,
    /** Every `T:` in order; the first is the main title. */
    val titles: List<String> = emptyList(),
    val rhythm: String? = null,
    val meter: String? = null,
    val unitLength: String? = null,
    val key: String? = null,
    val composer: String? = null,
    val origin: String? = null,
    val book: String? = null,
    val transcribers: List<String> = emptyList(),
    val whistle: WhistleKey? = null,
) {
    val title: String? get() = titles.firstOrNull()
    val alternateTitles: List<String> get() = titles.drop(1)
}

/**
 * Plain-text operations on ABC tunes and tune books. Text in, text out: the
 * ABC stays the canonical record, so edits touch only the lines they must.
 *
 * Splitting follows abcjs's own book parser (`bookParser` in abcjs-basic.js):
 * a tune starts at a line beginning `X:` and ends at the first blank line, and
 * the `%%` directives of the file header (text before the first `X:`) apply to
 * every tune. Header fields such as `M:` or `L:` in the file header are copied
 * in after the tune's `X:` line unless the tune sets that field itself.
 *
 * The per-tune whistle uses Michael Eskin's directive from his ABC Tools
 * (seisiuneer/abctools, `abcjs-basic-eskin.js`, MIT), so files round-trip:
 * - `%whistle_tab_key <name>` at the start of a line, anywhere in the tune;
 *   only the first such line counts. Eskin reads only the first word after the
 *   directive, case-insensitively: c c# db d d# eb e f f# gb g g# ab a a# bb b.
 * - `%whistle_tab_octave <n>` shifts his tab's octave marks. We don't use it
 *   and leave it untouched.
 * Our extension: a second word `low` marks a low whistle
 * (`%whistle_tab_key d low`); Eskin's tool ignores it. Names we have no
 * whistle for (c#, e, f#, g#, b) read as no whistle, and the line is kept.
 */
object AbcBook {

    private const val BOM = '﻿'
    private const val WHISTLE_DIRECTIVE = "%whistle_tab_key"
    private val FIELD = Regex("""^([A-Za-z]):\s*(.*)$""")
    private val TUNE_START = Regex("""^X:""")

    /**
     * The tunes of a tune book, each ready to store and parse on its own, with
     * `\n` line endings. Text without any `X:` line counts as one tune if it
     * has a `K:` line (abcjs accepts a lone tune without `X:`).
     */
    fun split(text: String): List<String> {
        val lines = text.removePrefix(BOM.toString()).lines()
        val firstTune = lines.indexOfFirst { TUNE_START.containsMatchIn(it) }
        if (firstTune < 0) {
            val tune = lines.dropWhile { it.isBlank() }.takeWhile { it.isNotBlank() }
            return if (tune.any { it.startsWith("K:") }) listOf(tune.joinToString("\n")) else emptyList()
        }
        val header = lines.subList(0, firstTune)
        val directives = header.filter { it.startsWith("%%") }
        val fields = header.filter { FIELD.matches(it) }

        // Between tunes, `%%` lines right before an X: belong to that tune (as join writes them).
        class Tune(val prefix: List<String>, val lines: MutableList<String>)
        val tunes = mutableListOf<Tune>()
        var current: MutableList<String>? = null
        val run = mutableListOf<String>()
        for (line in lines.subList(firstTune, lines.size)) {
            when {
                TUNE_START.containsMatchIn(line) -> {
                    val own = run.filter { it.startsWith("%%") && it !in directives }
                    current = mutableListOf(line).also { tunes += Tune(own, it) }
                    run.clear()
                }
                line.isBlank() -> {
                    current = null
                    run.clear()
                }
                current != null -> current.add(line)
                else -> run += line
            }
        }
        return tunes.map { tune ->
            val own = tune.lines.mapNotNull { FIELD.find(it)?.groupValues?.get(1) }.toSet()
            val inherited = fields.filter { FIELD.find(it)!!.groupValues[1] !in own }
            (directives + tune.prefix + tune.lines.first() + inherited + tune.lines.drop(1)).joinToString("\n")
        }
    }

    /**
     * Reads the header (up to and including the first `K:`). The whistle
     * directive is read from anywhere in the tune, as Eskin's tool does.
     */
    fun headers(abc: String): AbcHeaders {
        val values = mutableMapOf<Char, MutableList<String>>()
        for (raw in abc.removePrefix(BOM.toString()).lineSequence()) {
            val line = raw.trimEnd('\r')
            if (line.startsWith("%")) continue
            val match = FIELD.find(line) ?: continue
            val field = match.groupValues[1][0]
            values.getOrPut(field) { mutableListOf() } += stripComment(match.groupValues[2])
            if (field == 'K') break
        }
        fun first(field: Char) = values[field]?.firstOrNull()?.takeIf { it.isNotEmpty() }
        fun all(field: Char) = values[field].orEmpty().filter { it.isNotEmpty() }
        return AbcHeaders(
            x = first('X'),
            titles = all('T'),
            rhythm = first('R'),
            meter = first('M'),
            unitLength = first('L'),
            key = first('K'),
            composer = first('C'),
            origin = first('O'),
            book = first('B'),
            transcribers = all('Z'),
            whistle = whistleOf(abc),
        )
    }

    fun whistleOf(abc: String): WhistleKey? =
        abc.lineSequence().firstOrNull(::isWhistleLine)?.let(::parseWhistleLine)

    /**
     * Sets the tune's whistle directive, or removes it when [whistle] is null.
     * An existing directive is replaced in place; a new one goes on the line
     * before the first `K:` so it stays in the header.
     */
    fun withWhistle(abc: String, whistle: WhistleKey?): String {
        val eol = if ("\r\n" in abc) "\r\n" else "\n"
        val lines = abc.split(eol).toMutableList()
        val existing = lines.indexOfFirst(::isWhistleLine)
        val directive = whistle?.let(::directiveFor)
        when {
            existing >= 0 && directive != null -> lines[existing] = directive
            existing >= 0 -> lines.removeAt(existing)
            directive != null -> {
                val keyLine = lines.indexOfFirst { it.startsWith("K:") }
                lines.add(if (keyLine >= 0) keyLine else lines.size, directive)
            }
        }
        return lines.joinToString(eol)
    }

    /** A tune book with `X:` renumbered from 1, tunes separated by blank lines. */
    fun join(tunes: List<String>): String = tunes.mapIndexed { i, tune ->
        val lines = tune.removePrefix(BOM.toString()).trimEnd().lines()
        val x = lines.indexOfFirst { TUNE_START.containsMatchIn(it) }
        if (x >= 0) {
            (lines.subList(0, x) + "X:${i + 1}" + lines.subList(x + 1, lines.size)).joinToString("\n")
        } else {
            (listOf("X:${i + 1}") + lines).joinToString("\n")
        }
    }.joinToString("\n\n", postfix = "\n")

    /**
     * The whistle a tune in key [key] (a `K:` value) is most likely written
     * for, or null when there is no clear choice.
     */
    fun suggestWhistle(key: String?): WhistleKey? {
        val (tonic, mode) = parseKey(key ?: return null) ?: return null
        return when ("$tonic$mode") {
            "Dmaj", "Gmaj", "Emin", "Bmin", "Amin", "Ador", "Amix", "Edor", "Dmix" -> WhistleKey.D
            "Fmaj", "Dmin" -> WhistleKey.C
            else -> null
        }
    }

    /** Tonic (e.g. "F#") and a three-letter mode ("maj", "min", "dor", …). */
    internal fun parseKey(key: String): Pair<String, String>? {
        // A word followed by "=" is a modifier such as clef=treble, not a mode.
        val match = Regex("""^\s*([A-Ga-g])([#b]?)\s*([A-Za-z]*)(=?)""").find(key) ?: return null
        val tonic = match.groupValues[1].uppercase() + match.groupValues[2]
        val word = if (match.groupValues[4].isEmpty()) match.groupValues[3] else ""
        val mode = when (val m = word.lowercase().take(3)) {
            "", "maj", "ion" -> "maj"
            "m", "mi", "min", "aeo" -> "min"
            "dor", "mix", "phr", "lyd", "loc" -> m
            else -> return null
        }
        return tonic to mode
    }

    private fun stripComment(value: String): String {
        var i = value.indexOf('%')
        while (i > 0 && value[i - 1] == '\\') i = value.indexOf('%', i + 1)
        return (if (i >= 0) value.substring(0, i) else value).trim()
    }

    private fun isWhistleLine(line: String): Boolean = line.startsWith(WHISTLE_DIRECTIVE) &&
        line.getOrNull(WHISTLE_DIRECTIVE.length).let { it == null || it.isWhitespace() }

    private fun parseWhistleLine(line: String): WhistleKey? {
        val words = line.removePrefix(WHISTLE_DIRECTIVE).trim().lowercase().split(Regex("""\s+"""))
        val low = words.getOrNull(1) == "low"
        return when (words.firstOrNull()) {
            "c" -> if (low) WhistleKey.LOW_C else WhistleKey.C
            "d" -> if (low) WhistleKey.LOW_D else WhistleKey.D
            "eb", "d#" -> WhistleKey.E_FLAT
            "f" -> if (low) WhistleKey.LOW_F else WhistleKey.F
            "g" -> if (low) WhistleKey.LOW_G else WhistleKey.HIGH_G
            "a" -> WhistleKey.A
            "bb", "a#" -> WhistleKey.B_FLAT
            else -> null
        }
    }

    private fun directiveFor(whistle: WhistleKey): String = WHISTLE_DIRECTIVE + " " + when (whistle) {
        WhistleKey.HIGH_G -> "g"
        WhistleKey.F -> "f"
        WhistleKey.E_FLAT -> "eb"
        WhistleKey.D -> "d"
        WhistleKey.C -> "c"
        WhistleKey.B_FLAT -> "bb"
        WhistleKey.A -> "a"
        WhistleKey.LOW_G -> "g low"
        WhistleKey.LOW_F -> "f low"
        WhistleKey.LOW_D -> "d low"
        WhistleKey.LOW_C -> "c low"
    }
}
