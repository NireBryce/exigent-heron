package net.breadthcharge.exigentheron.domain

import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeoutOrNull

private const val MAX_MATCH_INPUT_LENGTH = 2000
private val MATCH_TIMEOUT = 100.milliseconds

/**
 * ANDROID-FREE. No Android imports — domain/ must be unit-testable on the JVM without Robolectric.
 *
 * Rules are compiled once at construction (cached regex patterns), not per
 * notification. Sorted by [Rule.priority] descending; first enabled,
 * matching rule wins. Default-deny: nothing matching produces
 * [Decision.Suppress] — an app that isn't allowlisted by some rule
 * stays silent.
 *
 * A rule whose regex fails to compile, or whose regex times out
 * against a real notification, is never allowed to crash evaluation —
 * both are reported through [onRuleFailure] (for a future settings
 * screen to surface and treated as that rule not matching).
 *
 * **Why not just use `withTimeoutOrNull`?** Regex matching has no cooperative-cancellation
 * checks built in: `java.util.regex` (wrapped by `kotlin.text.Regex`) won't abort just because
 * `Thread.interrupt()` was called. A bare timeout only abandons the caller; the thread keeps
 * burning CPU on catastrophic backtracking. [matches] closes this gap by wrapping input in
 * [InterruptibleCharSequence] (which checks `Thread.interrupted()` on every character read).
 * Matching runs inside [runInterruptible] (which does call `Thread.interrupt()` on cancellation),
 * so an interrupt now actually stops a runaway match — no `Dispatchers.Default` thread keeps
 * burning after `evaluate()` has already returned and moved on.
 *
 * The textbook "crafted message" scenario the spec describes is
 * narrower than it sounds anyway, verified rather than assumed: OpenJDK
 * memoizes failed backtracking positions (JDK-6328855), which makes
 * classic nested-quantifier patterns like `(a+)+$` linear-time on this
 * JVM, not exponential. That memoization is disabled whenever the
 * pattern has a backreference (`\1` etc.) — those are still genuinely
 * exponential, which is why [RuleValidator] rejects them outright at
 * rule-save time rather than relying on interruption alone: even a
 * cleanly-interrupted match still costs the full 100ms timeout on every
 * notification from that app, forever. The input-length cap below
 * bounds the remaining (non-backreference, JIT-memoized) case further.
 */
class RuleEngine(
    rules: List<Rule>,
    private val onRuleFailure: (ruleId: String, reason: String) -> Unit = { _, _ -> },
) {

    private class CompiledRule(
        val rule: Rule,
        val titleRegex: Regex?,
        val bodyRegex: Regex?,
    )

    private val compiled: List<CompiledRule> = rules
        .filter { it.enabled }
        .sortedByDescending { it.priority }
        .mapNotNull(::compileOrReportFailure)

    private fun compileOrReportFailure(rule: Rule): CompiledRule? {
        val title = rule.titlePattern?.let { compileOrNull(rule, it) ?: return null }
        val body = rule.bodyPattern?.let { compileOrNull(rule, it) ?: return null }
        return CompiledRule(rule, title, body)
    }

    private fun compileOrNull(rule: Rule, pattern: String): Regex? =
        when (val result = RuleValidator.validatePattern(pattern)) {
            is PatternValidation.Ok -> Regex(pattern, RegexOption.IGNORE_CASE)
            is PatternValidation.Invalid -> {
                onRuleFailure(rule.id, result.message)
                null
            }
        }

    suspend fun evaluate(payload: NotificationPayload): Decision {
        for (candidate in compiled) {
            if (!matchesPackage(candidate.rule, payload)) continue

            val matched = withTimeoutOrNull(MATCH_TIMEOUT) {
                runInterruptible(Dispatchers.Default) { matches(candidate, payload) }
            }

            if (matched == null) {
                onRuleFailure(candidate.rule.id, "regex match timed out")
                return Decision.Suppress(reason = "rule ${candidate.rule.id} timed out", ruleId = candidate.rule.id)
            }
            if (matched) return toDecision(candidate.rule, payload)
        }
        return Decision.Suppress(reason = "no matching rule")
    }

    private fun matchesPackage(rule: Rule, payload: NotificationPayload): Boolean =
        rule.packageNames.isEmpty() || payload.packageName in rule.packageNames

    // Wrapped in InterruptibleCharSequence so runInterruptible's
    // Thread.interrupt() (see the class doc above) actually stops a
    // runaway match instead of only abandoning the caller. Catching
    // InterruptedMatchException here rather than letting it propagate:
    // by the time it's thrown, withTimeoutOrNull has already returned
    // and moved on — this is purely about not burning the thread
    // further, not about producing a meaningful return value.
    private fun matches(candidate: CompiledRule, payload: NotificationPayload): Boolean =
        try {
            val title = InterruptibleCharSequence(payload.title.orEmpty().take(MAX_MATCH_INPUT_LENGTH))
            val body = InterruptibleCharSequence(payload.body.orEmpty().take(MAX_MATCH_INPUT_LENGTH))
            val titleOk = candidate.titleRegex?.containsMatchIn(title) ?: true
            val bodyOk = candidate.bodyRegex?.containsMatchIn(body) ?: true
            titleOk && bodyOk
        } catch (_: InterruptedMatchException) {
            false
        }

    private fun toDecision(rule: Rule, payload: NotificationPayload): Decision {
        val text = render(rule.template, payload)
        return when (rule.action) {
            RuleAction.SPEAK -> Decision.Speak(text, ruleId = rule.id)
            RuleAction.ANNOUNCE_ONLY -> Decision.AnnounceOnly(text, ruleId = rule.id)
            RuleAction.SUPPRESS -> Decision.Suppress(reason = "rule ${rule.id}", ruleId = rule.id)
        }
    }

    private fun render(template: String?, payload: NotificationPayload): String {
        if (template == null) {
            return listOfNotNull(payload.title, payload.body).joinToString(": ")
        }
        return template
            .replace("{title}", payload.title.orEmpty())
            .replace("{body}", payload.body.orEmpty())
    }
}
