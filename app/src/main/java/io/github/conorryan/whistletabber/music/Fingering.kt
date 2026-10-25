package io.github.conorryan.whistletabber.music

enum class Hole(val symbol: Char) {
    CLOSED('X'),
    OPEN('O'),
    HALF('½'),
    ;

    companion object {
        fun of(symbol: Char): Hole = entries.firstOrNull { it.symbol == symbol }
            ?: throw IllegalArgumentException("Unknown hole symbol '$symbol'")
    }
}

/**
 * One fingering on a six-hole whistle or flute.
 *
 * @property holes six holes, index 0 = hole 1 nearest the mouthpiece
 * @property octave 0 for the bottom octave, 1 for "+", 2 for "++"
 * @property hard a third-octave or otherwise unreliable fingering
 */
data class Fingering(
    val holes: List<Hole>,
    val octave: Int = 0,
    val hard: Boolean = false,
) {
    init {
        require(holes.size == 6) { "A fingering has six holes, got ${holes.size}" }
        require(octave in 0..2) { "Octave must be 0..2, got $octave" }
    }

    /** Chart notation, e.g. "XXX X½O+". */
    fun notation(): String =
        holes.take(3).joinToString("") { it.symbol.toString() } + " " +
            holes.drop(3).joinToString("") { it.symbol.toString() } + "+".repeat(octave)

    fun inOctave(octave: Int, hard: Boolean = this.hard): Fingering = copy(octave = octave, hard = hard)

    companion object {
        /** Parses chart notation: X closed, O open, ½ half; spaces ignored; trailing "+" per octave. */
        fun parse(notation: String, hard: Boolean = false): Fingering {
            val compact = notation.filterNot { it.isWhitespace() }
            val octave = compact.count { it == '+' }
            return Fingering(compact.trimEnd('+').map(Hole::of), octave, hard)
        }
    }
}
