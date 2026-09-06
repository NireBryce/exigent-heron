package net.breadthcharge.exigentheron.speech

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import net.breadthcharge.exigentheron.SafeLog
import net.breadthcharge.exigentheron.domain.SpeechRequest

private const val QUEUE_CAPACITY = 32
private const val INTER_UTTERANCE_SILENCE_MILLIS = 400L

/** AGENTS.md §4.7: "If the queue exceeds 5 pending items, collapse to a summary." */
private const val BURST_COLLAPSE_THRESHOLD = 5

/**
 * Single-consumer actor over a bounded [Channel] — see AGENTS.md §4.7.
 * [enqueue] never suspends the caller (`NotificationTtsListener`)
 * waiting for speech to finish; it posts and returns immediately,
 * dropping the *oldest* pending item on overflow rather than blocking
 * notification delivery or growing without bound.
 *
 * Takes [requestAudioFocus]/[abandonAudioFocus]/[isInCall]/[isBlockedByDnd]
 * as function references rather than an [AudioFocusManager],
 * `AudioManager`, or `NotificationManager` directly —
 * `AudioFocusManager`'s constructor touches a real `Context`
 * immediately, which makes it, and anything holding one, uninstantiable
 * in a JVM test. This keeps [TtsEngine] as the *only* real dependency
 * (per AGENTS.md §4.7: "the only way to test queue behaviour without an
 * emulator"), while still letting a JVM test substitute the audio-focus,
 * in-call, and DND behavior too, with plain lambdas instead of a second
 * fake class. `AppContainer` wires the real ones:
 * `audioFocusManager::requestFocus` / `::abandonFocus`, an
 * `AudioManager.mode` check, and a check combining
 * `NotificationManager.getCurrentInterruptionFilter()` with the
 * settings-backed DND-override toggle.
 */
@OptIn(ExperimentalCoroutinesApi::class) // Channel.isEmpty, used below
class SpeechQueue(
    private val ttsEngine: TtsEngine,
    private val requestAudioFocus: () -> Boolean,
    private val abandonAudioFocus: () -> Unit,
    private val isInCall: () -> Boolean,
    private val isBlockedByDnd: () -> Boolean,
    scope: CoroutineScope,
) {
    private val channel = Channel<SpeechRequest>(
        capacity = QUEUE_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private var holdingFocus = false

    init {
        scope.launch(Dispatchers.Default) { consume() }
    }

    fun enqueue(request: SpeechRequest) {
        channel.trySend(request)
    }

    private suspend fun consume() {
        for (request in channel) {
            // Collect whatever else is already sitting in the channel
            // alongside this one, non-blockingly — this is what makes a
            // burst (many enqueue() calls before the consumer catches
            // up) collapsible below instead of read back one at a time.
            val batch = buildList {
                add(request)
                while (true) add(channel.tryReceive().getOrNull() ?: break)
            }
            try {
                speakBatch(batch)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SafeLog.error("speech queue: utterance failed", e)
            } finally {
                // Unconditional: AudioFocusManager.abandonFocus() is a
                // no-op with nothing outstanding, and this still needs
                // to run when speakOne skipped without ever requesting
                // focus at all (isInCall/isBlockedByDnd — see below).
                if (channel.isEmpty) {
                    abandonAudioFocus()
                    holdingFocus = false
                }
            }
        }
    }

    private suspend fun speakBatch(batch: List<SpeechRequest>) {
        if (batch.size > BURST_COLLAPSE_THRESHOLD) {
            val summaryId = "${batch.last().utteranceId}-summary"
            speakOne(SpeechRequest(text = "${batch.size} new notifications.", utteranceId = summaryId))
        } else {
            for (request in batch) speakOne(request)
        }
    }

    private suspend fun speakOne(request: SpeechRequest) {
        // Checked first, deliberately, so a call in progress — or DND —
        // never even requests focus for an utterance it's about to skip
        // anyway.
        if (isInCall()) {
            SafeLog.lifecycle("speech skipped: device in call")
            return
        }
        if (isBlockedByDnd()) {
            SafeLog.lifecycle("speech skipped: DND")
            return
        }
        if (!holdingFocus) {
            holdingFocus = requestAudioFocus()
        }
        ttsEngine.speak(request.text, request.utteranceId)
        ttsEngine.silence(INTER_UTTERANCE_SILENCE_MILLIS, "${request.utteranceId}-silence")
    }
}
