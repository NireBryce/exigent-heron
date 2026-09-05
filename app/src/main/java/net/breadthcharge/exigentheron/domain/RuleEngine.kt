package net.breadthcharge.exigentheron.domain

import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val MAX_MATCH_INPUT_LENGTH = 2000
private val MATCH_TIMEOUT = 100.milliseconds

/**
 * PURE. No Android imports — see AGENTS.md §3.
 *
 * Rules are compiled once at construction (AGENTS.md §4.4), not per
 * notification. Sorted by [Rule.priority] descending; first enabled,
 * matching rule wins. Default-deny: nothing matching produces
 * [Decision.Suppress] — an app that isn't allowlisted by some rule
 * stays silent.
 *
 * A rule whose regex fails to compile, or whose regex times out
 * against a real notification, is never allowed to crash evaluation —
 * both are reported through [onRuleFailure] (for a future settings
 * screen to surface, per AGENTS.md §4.4's "mark the rule as failing in
 * the UI") and treated as that rule not matching.
 *
 * **A caveat worth knowing, not just assuming away:** AGENTS.md §4.4
 * asks for `withTimeoutOrNull(100.milliseconds)` around matching to
 * stop catastrophic regex backtracking from ANRing the app. That wrap
 * bounds how long *this function* waits, by racing the match against a
 * timer on [Dispatchers.Default] — it does **not** stop the match
 * itself: `java.util.regex` (which `kotlin.text.Regex` wraps) has no
 * cooperative-cancellation checks, verified directly — interrupting a
 * thread mid-match does not stop it (see `wiki/history.md`).
 *
 * The textbook "crafted message" scenario the spec describes is
 * narrower than it sounds, though, also verified rather than assumed:
 * OpenJDK memoizes failed backtracking positions (JDK-6328855), which
 * makes classic nested-quantifier patterns like `(a+)+$` linear-time
 * on this JVM, not exponential. That memoization is disabled whenever
 * the pattern has a backreference (`\1` etc.) — those are still
 * genuinely exponential *and* still not interruptible. A rule author
 * can still write one, so the timeout stays; a rule that hits it keeps
 * burning a [Dispatchers.Default] thread after `evaluate()` has already
 * returned `Suppress` and moved on. The input-length cap below bounds
 * how bad that leak can get per attack.
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
        try {
            Regex(pattern, RegexOption.IGNORE_CASE)
        } catch (e: java.util.regex.PatternSyntaxException) {
            onRuleFailure(rule.id, "invalid regex: ${e.message}")
            null
        }

    suspend fun evaluate(payload: NotificationPayload): Decision {
        for (candidate in compiled) {
            if (!matchesPackage(candidate.rule, payload)) continue

            val matched = withTimeoutOrNull(MATCH_TIMEOUT) {
                withContext(Dispatchers.Default) { matches(candidate, payload) }
            }

            if (matched == null) {
                onRuleFailure(candidate.rule.id, "regex match timed out")
                return Decision.Suppress(reason = "rule ${candidate.rule.id} timed out")
            }
            if (matched) return toDecision(candidate.rule, payload)
        }
        return Decision.Suppress(reason = "no matching rule")
    }

    private fun matchesPackage(rule: Rule, payload: NotificationPayload): Boolean =
        rule.packageNames.isEmpty() || payload.packageName in rule.packageNames

    private fun matches(candidate: CompiledRule, payload: NotificationPayload): Boolean {
        val title = payload.title.orEmpty().take(MAX_MATCH_INPUT_LENGTH)
        val body = payload.body.orEmpty().take(MAX_MATCH_INPUT_LENGTH)
        val titleOk = candidate.titleRegex?.containsMatchIn(title) ?: true
        val bodyOk = candidate.bodyRegex?.containsMatchIn(body) ?: true
        return titleOk && bodyOk
    }

    private fun toDecision(rule: Rule, payload: NotificationPayload): Decision {
        val text = render(rule.template, payload)
        return when (rule.action) {
            RuleAction.SPEAK -> Decision.Speak(text)
            RuleAction.ANNOUNCE_ONLY -> Decision.AnnounceOnly(text)
            RuleAction.SUPPRESS -> Decision.Suppress(reason = "rule ${rule.id}")
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
