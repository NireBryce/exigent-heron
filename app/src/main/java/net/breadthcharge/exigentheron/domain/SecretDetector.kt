package net.breadthcharge.exigentheron.domain

/**
 * ANDROID-FREE. No Android imports — domain/ must be unit-testable on the JVM without Robolectric.
 * Runs after [RuleEngine] and can only downgrade a [Decision], never upgrade one.
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
    val keywords: List<String> = DEFAULT_OTP_KEYWORDS,
) {
    // \b-wrapped: plain substring matching would flag "shopping" for
    // containing "pin", or "encode" for containing "code". Word
    // boundaries fix that without complicating the multi-word entries
    // ("security code") — \b only checks the transition at each
    // phrase's own start/end, not anything about the space in between.
    // If keywords list is empty, this pattern is never used (see looksLikeOtp).
    private val keywordPattern: Regex? = if (keywords.isEmpty()) {
        null
    } else {
        Regex(
            keywords.joinToString("|") { "\\b${Regex.escape(it)}\\b" },
            RegexOption.IGNORE_CASE,
        )
    }

    fun scan(decision: Decision, payload: NotificationPayload): Decision {
        if (decision is Decision.Suppress) return decision

        val body = payload.body

        // Hardcoded floor: never speak a bare 6-digit number. Cannot be disabled by the
        // keyword list above, and independent of proximity matching.
        if (decision is Decision.Speak && body != null && BARE_SIX_DIGIT_BODY.matches(body.trim())) {
            return Decision.Suppress(reason = "bare 6-digit body", ruleId = decision.ruleId)
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
        if (keywordPattern == null) return false // No keywords to match

        for (match in DIGIT_RUN.findAll(body)) {
            val start = (match.range.first - PROXIMITY_WINDOW).coerceAtLeast(0)
            val end = (match.range.last + PROXIMITY_WINDOW).coerceAtMost(body.length - 1)
            if (keywordPattern.containsMatchIn(body.substring(start, end + 1))) return true
        }
        return false
    }

    private fun downgrade(decision: Decision, payload: NotificationPayload, reason: String): Decision =
        when (decision) {
            is Decision.Speak -> Decision.AnnounceOnly(announceOnlyText(payload), ruleId = decision.ruleId)
            is Decision.AnnounceOnly ->
                if (payload.body != null && decision.text.contains(payload.body)) {
                    Decision.Suppress(reason = "$reason (announce text still contained it)", ruleId = decision.ruleId)
                } else {
                    decision
                }
            is Decision.Suppress -> decision
        }

    private fun announceOnlyText(payload: NotificationPayload): String {
        val who = payload.title?.takeIf { it.isNotBlank() } ?: payload.packageName
        return "New notification from $who"
    }

    // Non-private only so DEFAULT_OTP_KEYWORDS can reach AppContainer,
    // SettingsScreen and SecretDetectorHolder (the editable keyword list).
    // Everything else here, the hardcoded floor's own regex above all, stays private
    // — the floor is not user-configurable.
    companion object {
        private const val PROXIMITY_WINDOW = 40

        // Mirrors android.app.Notification.VISIBILITY_PRIVATE / VISIBILITY_SECRET
        // (0 / -1). These constants must stay in sync — NotificationExtractor
        // passes the real platform value straight through as an Int, so these must track
        // the framework's values, not be reinvented.
        //
        // internal, not private, purely so an instrumented test can assert
        // that they still match the framework's — a drift here wouldn't
        // fail to compile, it would silently misclassify every
        // notification, and this file can't import android.app to check
        // itself. See androidTest's GatePolicyFrameworkConstantsTest.
        internal const val VISIBILITY_PRIVATE = 0
        internal const val VISIBILITY_SECRET = -1

        private val DIGIT_RUN = Regex("""\b\d{4,8}\b""")
        private val BARE_SIX_DIGIT_BODY = Regex("""^\d{6}$""")

        val DEFAULT_OTP_KEYWORDS = listOf(
            "code", "otp", "one-time", "one time", "passcode", "pin",
            "verification", "verify", "2fa", "two-factor", "authenticat",
            "security code", "token",
        )
    }
}
