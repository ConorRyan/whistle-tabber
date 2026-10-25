package io.github.conorryan.whistletabber.data

/**
 * Decodes an enum stored by [Enum.name]. An unknown name (from an older or
 * newer version) reads as null, so callers fall back to their default.
 */
inline fun <reified E : Enum<E>> enumByName(name: String?): E? =
    name?.let { n -> enumValues<E>().firstOrNull { it.name == n } }
