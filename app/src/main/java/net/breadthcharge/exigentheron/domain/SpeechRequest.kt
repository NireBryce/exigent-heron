package net.breadthcharge.exigentheron.domain

/**
 * What `NotificationTtsListener` hands to `SpeechQueue` after a
 * [Decision] resolves to [Decision.Speak] or [Decision.AnnounceOnly] —
 * see AGENTS.md §3's data-flow diagram. A [Decision.Suppress] never
 * becomes one of these at all.
 */
data class SpeechRequest(
    val text: String,
    /** Ties a `TextToSpeech` utterance-done callback back to this request. */
    val utteranceId: String,
)
