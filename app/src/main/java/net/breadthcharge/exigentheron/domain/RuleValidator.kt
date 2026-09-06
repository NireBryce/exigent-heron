package net.breadthcharge.exigentheron.domain

import java.util.regex.PatternSyntaxException

/**
 * PURE. No Android imports — see AGENTS.md §3.
 *
 * Single source of truth for "is this a pattern we'll accept" — used by
 * both [RuleEngine.compileOrNull] (defensive: a rule already on disk
 * shouldn't crash evaluation even if it somehow bypassed the check
 * below) and the rule editor (Phase 3: surfaces [PatternValidation.Invalid]
 * inline at save time, per AGENTS.md §4.4 — "Catch `PatternSyntaxException`
 * at rule-save time and show the error in the editor").
 */
object RuleValidator {

    fun validatePattern(pattern: String?): PatternValidation {
        if (pattern == null) return PatternValidation.Ok
        if (BACKREFERENCE.containsMatchIn(pattern)) {
            return PatternValidation.Invalid(
                "backreferences (\\1, \\k<name>) are not allowed — " +
                    "they can make matching non-interruptible and exponential",
            )
        }
        return try {
            Regex(pattern)
            PatternValidation.Ok
        } catch (e: PatternSyntaxException) {
            PatternValidation.Invalid(e.message ?: "invalid regex")
        }
    }

    // A conservative heuristic, not a full parser: matches a backslash
    // immediately followed by a digit 1-9 (unambiguous in Java regex —
    // there's no octal escape starting with a nonzero digit) or `\k<`
    // (named-group backreference syntax). Can over-reject the rare
    // pattern that means an escaped literal backslash followed by a
    // literal digit (e.g. the four-character pattern text `\\1`) — that
    // false positive is accepted deliberately; the reverse (a real
    // backreference sneaking past this check) is what matters here.
    private val BACKREFERENCE = Regex("""\\[1-9]|\\k<""")
}

sealed interface PatternValidation {
    data object Ok : PatternValidation
    data class Invalid(val message: String) : PatternValidation
}
