package net.breadthcharge.exigentheron.domain

/**
 * PURE. No Android imports. The output of [RuleEngine.evaluate], then
 * possibly downgraded (never upgraded) by [SecretDetector.scan] — see
 * AGENTS.md §3's data-flow diagram.
 */
sealed interface Decision {
    data class Speak(val text: String) : Decision
    data class AnnounceOnly(val text: String) : Decision
    data class Suppress(val reason: String) : Decision
}
