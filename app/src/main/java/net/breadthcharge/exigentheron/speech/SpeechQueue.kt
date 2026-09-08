package net.breadthcharge.exigentheron.speech

import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import net.breadthcharge.exigentheron.SafeLog
import net.breadthcharge.exigentheron.domain.SpeechRequest

private const val QUEUE_CAPACITY = 32
private const val INTER_UTTERANCE_SILENCE_MILLIS = 400L

/** If queue exceeds this many pending items, collapse to "5 new notifications" instead of reading each one. */
private const val BURST_COLLAPSE_THRESHOLD = 5

/**
 * Single-consumer actor over a bounded [Channel].
 * [enqueue] posts and returns immediately (never blocks the notification listener);
 * overflow drops the oldest pending item rather than blocking delivery or growing unbounded.
 *
 * Takes [requestAudioFocus]/[abandonAudioFocus] and [SpeechGates]'
 * three checks as function references rather than an
 * [AudioFocusManager], `AudioManager`, or `NotificationManager` directly —
 * `AudioFocusManager`'s constructor touches a real `Context`
 * immediately, which makes it, and anything holding one, uninstantiable
 * in a JVM test. [TtsEngine] is the only real dependency, making this
 * testable on the JVM without an emulator. Tests substitute audio-focus,
 * in-call, DND, and output-route behavior too, with plain lambdas instead
 * of a second fake class. `AppContainer` wires the real ones:
 * `audioFocusManager::requestFocus` / `::abandonFocus`, an
 * `AudioManager.mode` check, a check combining
 * `NotificationManager.getCurrentInterruptionFilter()` with the
 * settings-backed DND-override toggle, and `outputRouteGate::allows`.
 *
 * **[SpeechGates.isOutputRouteAllowed] is re-checked here, per utterance, not just
 * once at enqueue time.** `NotificationTtsListener.route()` already
 * checks `OutputRouteGate.allows()` before ever calling [enqueue] — that
 * alone leaves a real gap: a headset connected at enqueue time can
 * disconnect before this queue actually gets to that item (a burst still
 * draining, TTS still speaking the previous one), and without a second
 * check here the fallback would be the device speaker, exactly what the
 * headset-only setting exists to prevent. This closes that gap for every
 * *queued* item; it does not by itself stop a route change that happens
 * *while* an utterance is already playing — see [stopCurrent] for that
 * half, wired to `AudioManager.ACTION_AUDIO_BECOMING_NOISY` by
 * `AppContainer`.
 *
 * [truncationLengthSeconds] is read fresh per utterance, same as every
 * other settings-backed lambda here. A non-null value races
 * [TtsEngine.speak] against a timeout of that many seconds; on timeout
 * [TtsEngine.stop] is called explicitly rather than relying on the
 * timeout's coroutine cancellation to do it — cancelling the suspended
 * `speak()` call only stops *waiting* for the engine's completion
 * callback, it does not stop the real `TextToSpeech` from continuing to
 * play the rest of the utterance underneath.
 *
 * [dispatcher] defaults to `Dispatchers.Default` — the production
 * choice — but is injectable so a test can confine the consumer to its
 * own single thread. `Dispatchers.Default` is one pool shared by the
 * whole JVM test process rather than scoped per test, which is how a
 * still-finishing consumer from one test came to overlap the next one's
 * setup; see wiki/traps-and-skills.md.
 */
/**
 * The three per-utterance gates [SpeechQueue] consults, grouped so they
 * are named rather than positional at every call site, and so the
 * queue's own constructor isn't four same-shaped `() -> Boolean`
 * parameters in a row.
 *
 * Each is a function reference, not a framework object, for the reason
 * [SpeechQueue]'s own doc comment gives: `AudioFocusManager`'s
 * constructor touches a real `Context`, so anything holding one is
 * uninstantiable in a JVM test.
 */
class SpeechGates(
    val isInCall: () -> Boolean,
    val isBlockedByDnd: () -> Boolean,
    val isOutputRouteAllowed: () -> Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class) // Channel.isEmpty, used below
class SpeechQueue(
    private val ttsEngine: TtsEngine,
    private val gates: SpeechGates,
    private val requestAudioFocus: () -> Boolean,
    private val abandonAudioFocus: () -> Unit,
    scope: CoroutineScope,
    private val truncationLengthSeconds: () -> Int? = { null },
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val channel = Channel<SpeechRequest>(
        capacity = QUEUE_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private var holdingFocus = false

    init {
        scope.launch(dispatcher) { consume() }
    }

    fun enqueue(request: SpeechRequest) {
        channel.trySend(request)
    }

    /**
     * Halts whatever utterance is playing *right now*, immediately —
     * for the one case [SpeechGates.isOutputRouteAllowed]'s per-item re-check can't
     * reach: a route disconnecting mid-utterance rather than between
     * items. `AppContainer` calls this from an
     * `AudioManager.ACTION_AUDIO_BECOMING_NOISY` receiver, Android's own
     * signal that the active route is about to fall back to a less
     * private one *during playback* (headphones pulled, Bluetooth
     * dropping) — the standard place apps stop/pause on exactly this.
     * [TtsEngine.stop] unblocks whichever `speak()`/`silence()` call was
     * suspended waiting on it, so [consume] moves on to the next queued
     * item (if any) normally — which then goes through the same
     * [SpeechGates.isOutputRouteAllowed] check above and gets skipped too if the
     * route is still bad.
     */
    fun stopCurrent() {
        ttsEngine.stop()
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
        // Checked first, deliberately, so a call in progress — DND, or a
        // route that no longer qualifies — never even requests focus for
        // an utterance it's about to skip anyway.
        if (gates.isInCall()) {
            SafeLog.lifecycle("speech skipped: device in call")
            return
        }
        if (gates.isBlockedByDnd()) {
            SafeLog.lifecycle("speech skipped: DND")
            return
        }
        // Re-checked here, not trusted from whatever NotificationTtsListener
        // saw at enqueue time — see this class's own doc comment on
        // [SpeechGates.isOutputRouteAllowed] for why that's a real gap otherwise.
        if (!gates.isOutputRouteAllowed()) {
            SafeLog.lifecycle("speech skipped: output route")
            return
        }
        if (!holdingFocus) {
            holdingFocus = requestAudioFocus()
        }
        val limitSeconds = truncationLengthSeconds()
        if (limitSeconds != null) {
            val finished = withTimeoutOrNull(limitSeconds.seconds) {
                ttsEngine.speak(request.text, request.utteranceId)
            }
            if (finished == null) ttsEngine.stop()
        } else {
            ttsEngine.speak(request.text, request.utteranceId)
        }
        ttsEngine.silence(INTER_UTTERANCE_SILENCE_MILLIS, "${request.utteranceId}-silence")
    }
}
