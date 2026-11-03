package io.github.conorryan.whistletabber.music

/**
 * One way to play a note.
 *
 * @property fife the source charts it for six-hole fifes, not tin whistles
 *   (it may or may not work on a given whistle)
 */
data class FingeringOption(val fingering: Fingering, val fife: Boolean = false)

/**
 * Every fingering the app knows for each note, by semitones above the
 * whistle's bottom note; the first is the default. If a source charts a way
 * to play a note, it is offered. Only sourced fingerings belong here:
 * the Woodwind Fingering Guide's tin whistle and fife charts
 * (https://www.wfg.woodwind.org/tinwhistle/tw_bas_1.html, `tw_bas_2.html`,
 * `tw_bas_3.html`) and the Chiff & Fipple / tutor-book C naturals.
 *
 * Defaults are what the app always drew, so a user who chooses nothing sees
 * the same tab as before.
 */
object FingeringOptions {

    private fun opts(vararg notations: String, octave: Int = 0, hard: Boolean = false): List<FingeringOption> =
        notations.map { n ->
            val fife = n.endsWith(FIFE)
            FingeringOption(Fingering.parse(n.removeSuffix(FIFE), hard).inOctave(octave), fife)
        }

    private const val FIFE = " fife"

    /** C natural has no standard fingering: which one is in tune depends on the whistle. */
    private val C_NATURAL = arrayOf(
        "OXX OOO",
        "½OO OOO",
        "OXX OXX",
        "OXX XOX",
        "OXX XXX",
        "OXX OOX",
        "OXX XOO fife", //  WFG: "More in tune."
        "OXO XXX fife", //  WFG: "Easier than half-holing, but prone to cracking to a higher note."
        "OXO XXO fife", //  WFG: "For fife in B♭."
    )

    private val FIRST_OCTAVE: List<Array<String>> = listOf(
        arrayOf("XXX XXX"), //                 0  D
        arrayOf("XXX XX½"), //                 1  D♯/E♭
        arrayOf("XXX XXO"), //                 2  E
        arrayOf("XXX X½O"), //                 3  F
        arrayOf("XXX XOO"), //                 4  F♯
        arrayOf("XXX OOO"), //                 5  G
        arrayOf("XX½ OOO"), //                 6  G♯/A♭
        arrayOf("XXO OOO"), //                 7  A
        arrayOf("X½O OOO", "XOX XOO fife"), // 8  A♯/B♭; WFG: "In tune. Good for fast passages."
        arrayOf("XOO OOO"), //                 9  B
        C_NATURAL, //                          10 C
        arrayOf("OOO OOO", "OOO OOX"), //      11 C♯; WFG: "Better balance (tin whistle)."
    )

    /** Second-octave notes whose choices differ from the first octave's. */
    private val SECOND_OCTAVE: Map<Int, Array<String>> = mapOf(
        12 to arrayOf("XXX XXX", "OXX XXX"), //            D: WFG's basic D is vented (top hole open)
        23 to arrayOf("OOO OOO", "OOO OOX", "OOO XXX fife"), // C♯: "Better balance." / "For fife in D."
    )

    /** Third octave, all hard: "These fingerings may or may not work on all tin whistles and fifes." */
    private val THIRD_OCTAVE: Map<Int, Array<String>> = mapOf(
        24 to arrayOf("OXX XXX"), //                                D
        25 to arrayOf("XXO XOX fife"), //                           D♯  "In tune."
        26 to arrayOf("XXX XXO", "XXO OXO fife"), //                E   "Difficult. Do not overblow." / "Great response."
        27 to arrayOf("XXX X½X", "X½O OXO fife"), //                F   "Tricky."
        28 to arrayOf("XXX XOX"), //                                F♯
        29 to arrayOf("XXX OOX"), //                                G   "Good on most whistles."
        30 to arrayOf("XX½ OOX", "OOX OOO fife"), //                G♯  "Occasionally possible in passing."
        31 to arrayOf("XXO OOX", "XXO OXX", "OXX XXO fife"), //     A   "Good tuning and response on some whistles."
        32 to arrayOf("OXO XOO"), //                                A♯  "Typically flat."
        33 to arrayOf("XOX OXO"), //                                B   "Typically sharp."
        34 to arrayOf("XOX OXX"), //                                C   "Requires a lot of air."
    )

    const val HIGHEST_OFFSET = 34

    private val ALL: Map<Int, List<FingeringOption>> = buildMap {
        FIRST_OCTAVE.forEachIndexed { offset, n -> put(offset, opts(*n)) }
        FIRST_OCTAVE.forEachIndexed { offset, n -> put(offset + 12, opts(*(SECOND_OCTAVE[offset + 12] ?: n), octave = 1)) }
        THIRD_OCTAVE.forEach { (offset, n) -> put(offset, opts(*n, octave = 2, hard = true)) }
    }

    /** The fingerings for [offset], default first; empty where the whistle has none. */
    fun at(offset: Int): List<FingeringOption> = ALL[offset].orEmpty()

    /** Offsets with at least one fingering, lowest first. */
    val offsets: List<Int> = ALL.keys.sorted()
}

/**
 * The user's choice of fingering per note, by offset (see [FingeringOptions]).
 * A missing pick, or one that isn't among the note's options, is the default.
 */
data class FingeringChoices(val picks: Map<Int, Fingering> = emptyMap()) {

    /** Index of the chosen option at [offset] (0 = the default). */
    fun index(offset: Int): Int =
        picks[offset]?.let { pick -> FingeringOptions.at(offset).indexOfFirst { it.fingering.sameShape(pick) } }
            ?.takeIf { it >= 0 } ?: 0

    fun fingeringAt(offset: Int): Fingering? = FingeringOptions.at(offset).getOrNull(index(offset))?.fingering

    /** These choices with [offset] set to its option [index]; the default is stored as no pick. */
    fun with(offset: Int, index: Int): FingeringChoices {
        val option = FingeringOptions.at(offset).getOrNull(index) ?: return this
        return if (index == 0) copy(picks = picks - offset) else copy(picks = picks + (offset to option.fingering))
    }

    /** Stable text form, e.g. `"12=OXXXXX+;22=½OOOOO+"`: shapes, not indices, so adding options never moves a pick. */
    fun encode(): String = picks.toSortedMap().entries.joinToString(";") { (offset, f) -> "$offset=${f.notation().replace(" ", "")}" }

    companion object {
        val DEFAULT = FingeringChoices()

        /** Reads [encode]'s form; malformed entries and fingerings no note offers are dropped. */
        fun decode(text: String?): FingeringChoices {
            if (text.isNullOrBlank()) return DEFAULT
            val picks = text.split(';').mapNotNull { entry ->
                val (o, n) = entry.split('=', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
                val offset = o.trim().toIntOrNull() ?: return@mapNotNull null
                val f = runCatching { Fingering.parse(n) }.getOrNull() ?: return@mapNotNull null
                val option = FingeringOptions.at(offset).firstOrNull { it.fingering.sameShape(f) } ?: return@mapNotNull null
                offset to option.fingering
            }.toMap()
            return FingeringChoices(picks)
        }
    }
}

/** Same holes and octave; [Fingering.hard] is ignored. */
fun Fingering.sameShape(other: Fingering): Boolean = holes == other.holes && octave == other.octave
