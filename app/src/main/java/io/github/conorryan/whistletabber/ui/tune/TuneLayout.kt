package io.github.conorryan.whistletabber.ui.tune

/**
 * The tune screen's geometry. The page fills the screen under the header;
 * the header floats over its top and scrolls away with it, so the music can
 * take the whole height.
 */
internal object TuneLayout {
    /** Below this height (a phone on its side) the app bar scrolls away too and the player is one row. */
    const val SHORT_SCREEN_DP = 480

    fun isShort(screenHeightDp: Int): Boolean = screenHeightDp < SHORT_SCREEN_DP

    /** How far the header has moved up, in px: with the page until it is out of sight. */
    fun headerShift(scrollY: Int, headerHeight: Int): Int = scrollY.coerceIn(0, headerHeight.coerceAtLeast(0))

    /** Where the page's card begins, in px from the top: under the header, then at the top. */
    fun pageTop(scrollY: Int, headerHeight: Int): Int = headerHeight.coerceAtLeast(0) - headerShift(scrollY, headerHeight)
}
