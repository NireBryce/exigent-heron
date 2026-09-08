package net.breadthcharge.exigentheron.domain

// \p{Cc} = control characters. The rest are the specific zero-width and
// bidi-override code points the NotificationExtractor contract requires stripping — not covered by \p{Cc}.
private val UNSAFE_CHARS = Regex("[\\p{Cc}\\u200B-\\u200F\\uFEFF\\u202A-\\u202E]")

/**
 * Strips control characters, zero-width characters (U+200B–U+200F,
 * U+FEFF), and bidi override marks (U+202A–U+202E) — part of the NotificationExtractor contract.
 * Returns null for a null or now-empty result, so callers can treat
 * "sanitized to nothing" the same as "wasn't there."
 */
fun sanitizeNotificationText(raw: String?): String? {
    val cleaned = raw?.let { UNSAFE_CHARS.replace(it, "") }
    return cleaned?.ifEmpty { null }
}
