package io.github.conorryan.whistletabber.music

/**
 * C natural (a minor seventh above the tonic) has no standard fingering:
 * which one is in tune depends on the whistle. Offered choices, from the
 * Chiff & Fipple community and tutor books.
 */
enum class CNatural(val fingering: Fingering) {
    OXX_OOO(Fingering.parse("OXX OOO")),
    HALF_TOP(Fingering.parse("½OO OOO")),
    OXX_OXX(Fingering.parse("OXX OXX")),
    OXX_XOX(Fingering.parse("OXX XOX")),
    OXX_XXX(Fingering.parse("OXX XXX")),
    OXX_OOX(Fingering.parse("OXX OOX")),
    ;

    companion object {
        val DEFAULT = OXX_OOO
    }
}
