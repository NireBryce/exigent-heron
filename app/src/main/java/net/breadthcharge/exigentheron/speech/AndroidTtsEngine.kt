package net.breadthcharge.exigentheron.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import net.breadthcharge.exigentheron.SafeLog

/** Observable init/failure state — see AGENTS.md §4.8's "not a silent no-op". */
sealed interface TtsEngineStatus {
    data object Initializing : TtsEngineStatus
    data object Ready : TtsEngineStatus
    data class Failed(val reason: String) : TtsEngineStatus
}

/**
 * Wraps [android.speech.tts.TextToSpeech]. Uses the system default
 * engine for now — AGENTS.md §4.8's explicit engine picker (enumerate
 * [TextToSpeech.getEngines], persist a choice, show the active one) is
 * Phase 4, once there's a settings screen and `SettingsRepository` to
 * hold that choice in. This class exposes [statusListener] so that UI,
 * once it exists, can still surface an init failure or missing-language
 * data instead of the silent no-op AGENTS.md §4.8 explicitly warns
 * against — Phase 2 has nowhere to show it yet, but nothing here
 * swallows it.
 */
class AndroidTtsEngine(
    context: Context,
    private val statusListener: (TtsEngineStatus) -> Unit = {},
) : TtsEngine {

    private val ready = CompletableDeferred<Unit>()
    private val pending = ConcurrentHashMap<String, Continuation<Unit>>()

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { code ->
        if (code == TextToSpeech.SUCCESS) {
            statusListener(TtsEngineStatus.Ready)
            ready.complete(Unit)
        } else {
            val reason = "TextToSpeech init failed (status=$code)"
            SafeLog.error(reason)
            statusListener(TtsEngineStatus.Failed(reason))
            ready.completeExceptionally(IllegalStateException(reason))
        }
    }.apply {
        setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) = resumePending(utteranceId)

                @Deprecated("Deprecated in Java, still abstract on this listener")
                override fun onError(utteranceId: String?) = resumePending(utteranceId)

                override fun onError(utteranceId: String?, errorCode: Int) {
                    SafeLog.error("TTS utterance error: id=$utteranceId code=$errorCode")
                    resumePending(utteranceId)
                }
            },
        )
    }

    private fun resumePending(utteranceId: String?) {
        val id = utteranceId ?: return
        pending.remove(id)?.resume(Unit)
    }

    override suspend fun speak(text: String, utteranceId: String) {
        ready.await()
        awaitUtterance(utteranceId) { tts.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId) }
    }

    override suspend fun silence(durationMillis: Long, utteranceId: String) {
        ready.await()
        awaitUtterance(utteranceId) {
            tts.playSilentUtterance(durationMillis, TextToSpeech.QUEUE_ADD, utteranceId)
        }
    }

    /**
     * [start] is one of the two `tts.*` calls above, invoked while this
     * function is already suspended so [resumePending] can never fire
     * before [pending] holds the continuation it's meant to resume.
     */
    private suspend fun awaitUtterance(utteranceId: String, start: () -> Int) =
        suspendCancellableCoroutine { continuation ->
            pending[utteranceId] = continuation
            if (start() != TextToSpeech.SUCCESS) {
                // Enqueue itself failed; no onDone/onError will ever
                // arrive for this id, so resume it here instead of
                // hanging the queue forever.
                pending.remove(utteranceId)?.resume(Unit)
            }
        }

    override fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
