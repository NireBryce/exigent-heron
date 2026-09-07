package net.breadthcharge.exigentheron.domain

/**
 * PURE. No Android imports. The output of [RuleEngine.evaluate], then
 * possibly downgraded (never upgraded) by [SecretDetector.scan] — see
 * AGENTS.md §3's data-flow diagram.
 *
 * [ruleId] is the id of the [Rule] that produced this decision, or
 * `null` when none did (the default-deny "no matching rule" case, or a
 * gate suppressing a decision that never involved a rule id in the
 * first place). Threaded through so [SafeLog.decision]'s `ruleId`
 * parameter — always `null` in practice before this — can log which
 * rule actually fired, per `wiki/open-threads.md`'s note on it.
 */
sealed interface Decision {
    val ruleId: String?

    data class Speak(val text: String, override val ruleId: String? = null) : Decision
    data class AnnounceOnly(val text: String, override val ruleId: String? = null) : Decision
    data class Suppress(val reason: String, override val ruleId: String? = null) : Decision
}
