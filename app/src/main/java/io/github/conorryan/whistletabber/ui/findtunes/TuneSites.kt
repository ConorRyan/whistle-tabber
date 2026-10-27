package io.github.conorryan.whistletabber.ui.findtunes

import androidx.annotation.StringRes
import io.github.conorryan.whistletabber.R

/** A website with ABC tunes, opened in the browser; the app itself has no network access. */
data class TuneSite(@StringRes val name: Int, @StringRes val about: Int, val url: String) {
    /** The address without scheme or `www.`, as shown under the name. */
    val host: String get() = url.substringAfter("://").removePrefix("www.").substringBefore('/')
}

/**
 * The sites "Find tunes online" lists. Never TheSession.org: its licence
 * forbids the LLM processing this project uses (see AGENTS.md).
 */
val TUNE_SITES = listOf(
    TuneSite(R.string.site_oneills_name, R.string.site_oneills_about, "https://trillian.mit.edu/~jc/music/book/ONeills/"),
    TuneSite(R.string.site_abcnotation_name, R.string.site_abcnotation_about, "https://abcnotation.com/"),
    TuneSite(R.string.site_norbeck_name, R.string.site_norbeck_about, "https://www.norbeck.nu/abc/"),
    TuneSite(R.string.site_chambers_name, R.string.site_chambers_about, "https://trillian.mit.edu/~jc/cgi/abc/findtune"),
)
