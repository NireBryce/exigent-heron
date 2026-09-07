package net.breadthcharge.exigentheron.speech

/**
 * Interface over the real TTS engine — see AGENTS.md §4.7. Exists so
 * [SpeechQueue] is testable with a fake that records calls instead of a
 * real `android.speech.tts.TextToSpeech`, which needs a device/emulator.
 */
interface TtsEngine {
    /** Suspends until this utterance completes (or fails — see [AndroidTtsEngine]). */
    suspend fun speak(text: String, utteranceId: String)

    /** Suspends until the silence finishes playing. */
    suspend fun silence(durationMillis: Long, utteranceId: String)

    /**
     * Stops whatever utterance is currently playing, if any, and unblocks
     * whichever [speak]/[silence] call is suspended waiting on it — the
     * engine itself stays usable afterward (unlike [shutdown]). See
     * [SpeechQueue]'s `stopCurrent` doc comment for why this exists:
     * closing the gap between a route disconnecting *mid-utterance* and
     * the next queued item's gate re-check.
     */
    fun stop()

    fun shutdown()
}
