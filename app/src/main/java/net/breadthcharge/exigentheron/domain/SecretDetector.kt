package net.breadthcharge.exigentheron.domain

/**
 * PURE. No Android imports — see AGENTS.md §3. Runs after [RuleEngine]
 * and can only downgrade a [Decision], never upgrade one — see
 * AGENTS.md §4.5.
 *
 * **Downgrade semantics, spelled out because the spec states the rule
 * but not the mechanics:** downgrading [Decision.Speak] to
 * [Decision.AnnounceOnly] must **not** carry [Decision.Speak.text]
 * forward — that text is exactly what looked like a secret, so
 * reusing it would defeat the downgrade entirely. Instead a generic
 * "New notification from X" is synthesized from [NotificationPayload.title]
 * only. If the incoming decision is already [Decision.AnnounceOnly]
 * (e.g. a user-authored [Rule.template] embedding `{body}`) and its own
 * text still contains the flagged body, that can't be safely announced
 * either — it downgrades one step further to [Decision.Suppress].
 */
class SecretDetector(
    private val keywords: List<String> = DEFAULT_OTP_KEYWORDS,
) {
    // \b-wrapped: plain substring matching would flag "shopping" for
    // containing "pin", or "encode" for containing "code". Word
    // boundaries fix that without complicating the multi-word entries
    // ("security code") — \b only checks the transition at each
    // phrase's own start/end, not anything about the space in between.
    private val keywordPattern = Regex(
        keywords.joinToString("|") { "\\b${Regex.escape(it)}\\b" },
        RegexOption.IGNORE_CASE,
    )

    fun scan(decision: Decision, payload: NotificationPayload): Decision {
        if (decision is Decision.Suppress) return decision

        val body = payload.body

        // Hardcoded floor (AGENTS.md §4.5): cannot be disabled by the
        // keyword list above, and independent of proximity matching.
        if (decision is Decision.Speak && body != null && BARE_SIX_DIGIT_BODY.matches(body.trim())) {
            return Decision.Suppress(reason = "bare 6-digit body")
        }

        if (payload.visibility == VISIBILITY_PRIVATE || payload.visibility == VISIBILITY_SECRET) {
            return downgrade(decision, payload, reason = "notification marked private/secret")
        }

        if (body != null && looksLikeOtp(body)) {
            return downgrade(decision, payload, reason = "otp-shaped content")
        }

        return decision
    }

    private fun looksLikeOtp(body: String): Boolean {
        for (match in DIGIT_RUN.findAll(body)) {
            val start = (match.range.first - PROXIMITY_WINDOW).coerceAtLeast(0)
            val end = (match.range.last + PROXIMITY_WINDOW).coerceAtMost(body.length - 1)
            if (keywordPattern.containsMatchIn(body.substring(start, end + 1))) return true
        }
        return false
    }

    private fun downgrade(decision: Decision, payload: NotificationPayload, reason: String): Decision =
        when (decision) {
            is Decision.Speak -> Decision.AnnounceOnly(announceOnlyText(payload))
            is Decision.AnnounceOnly ->
                if (payload.body != null && decision.text.contains(payload.body)) {
                    Decision.Suppress(reason = "$reason (announce text still contained it)")
                } else {
                    decision
                }
            is Decision.Suppress -> decision
        }

    private fun announceOnlyText(payload: NotificationPayload): String {
        val who = payload.title?.takeIf { it.isNotBlank() } ?: payload.packageName
        return "New notification from $who"
    }

    private companion object {
        const val PROXIMITY_WINDOW = 40

        // Mirrors android.app.Notification.VISIBILITY_PRIVATE / VISIBILITY_SECRET
        // (0 / -1). Domain stays Android-import-free (AGENTS.md §3);
        // NotificationExtractor (Phase 2) passes the real platform constant
        // straight through as an Int, so these values must track the
        // framework's, not be reinvented.
        const val VISIBILITY_PRIVATE = 0
        const val VISIBILITY_SECRET = -1

        val DIGIT_RUN = Regex("""\b\d{4,8}\b""")
        val BARE_SIX_DIGIT_BODY = Regex("""^\d{6}$""")

        val DEFAULT_OTP_KEYWORDS = listOf(
            "code", "otp", "one-time", "one time", "passcode", "pin",
            "verification", "verify", "2fa", "two-factor", "authenticat",
            "security code", "token",
        )
    }
}
