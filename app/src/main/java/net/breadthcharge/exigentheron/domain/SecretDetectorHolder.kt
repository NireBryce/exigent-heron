package net.breadthcharge.exigentheron.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * PURE (in the AGENTS.md §3 sense — `Flow`/`CoroutineScope` are
 * coroutines, not Android). No Android imports.
 *
 * Keeps a live [SecretDetector] rebuilt from whatever [otpKeywords]
 * currently emits, so a keyword edit (via settings UI) takes effect on
 * the next notification instead of requiring an app restart.
 * Constructing a [SecretDetector] is cheap — just regex compilation over
 * ~15 keywords — so rebuilding on every emission needs no debouncing.
 */
class SecretDetectorHolder(
    otpKeywords: Flow<List<String>>,
    scope: CoroutineScope,
) {
    private val detector = MutableStateFlow(SecretDetector(SecretDetector.DEFAULT_OTP_KEYWORDS))

    init {
        scope.launch {
            otpKeywords.collect { keywords -> detector.value = SecretDetector(keywords) }
        }
    }

    suspend fun scan(decision: Decision, payload: NotificationPayload): Decision =
        detector.value.scan(decision, payload)
}
