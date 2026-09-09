package net.breadthcharge.exigentheron.domain

/**
 * ANDROID-FREE. No Android imports. The output of [RuleEngine.evaluate], then
 * possibly downgraded (never upgraded) by [SecretDetector.scan]. The decision is threaded through the
 * pipeline: extract → dedup → rule engine → secret detector → output gate → lock gate → speech queue.
 *
 * [ruleId] is the id of the [Rule] that produced this decision, or
 * `null` when none did (the default-deny "no matching rule" case, or a
 * gate suppressing a decision that never involved a rule id in the
 * first place). Threaded through so
 * [SafeLog.decision][net.breadthcharge.exigentheron.SafeLog.decision]'s `ruleId`
 * parameter — always `null` in practice before this — can log which
 * rule actually fired, per `wiki/open-threads-4llm.md`'s note on it.
 */
sealed interface Decision {
    val ruleId: String?

    data class Speak(val text: String, override val ruleId: String? = null) : Decision
    data class AnnounceOnly(val text: String, override val ruleId: String? = null) : Decision
    data class Suppress(val reason: String, override val ruleId: String? = null) : Decision
}
