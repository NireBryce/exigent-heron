package net.breadthcharge.exigentheron.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
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
 * Wraps [android.speech.tts.TextToSpeech]. [enginePackage] is the user's
 * persisted choice from AGENTS.md §4.8's engine picker — null falls back
 * to the system default, which is only ever a *temporary* state until
 * the user has picked one in settings, never a silent permanent fallback
 * (§4.8: "do not silently accept the system default"). This class
 * exposes [statusListener] so the settings screen can surface an init
 * failure or missing-language data instead of the silent no-op AGENTS.md
 * §4.8 explicitly warns against.
 */
class AndroidTtsEngine(
    context: Context,
    enginePackage: String? = null,
    private val statusListener: (TtsEngineStatus) -> Unit = {},
) : TtsEngine {

    private val ready = CompletableDeferred<Unit>()
    private val pending = ConcurrentHashMap<String, Continuation<Unit>>()

    private val initListener = TextToSpeech.OnInitListener { code ->
        if (code != TextToSpeech.SUCCESS) {
            val reason = "TextToSpeech init failed (status=$code)"
            SafeLog.error(reason)
            statusListener(TtsEngineStatus.Failed(reason))
            ready.completeExceptionally(IllegalStateException(reason))
            return@OnInitListener
        }
        // AGENTS.md §4.8: LANG_MISSING_DATA/LANG_NOT_SUPPORTED get a
        // visible error too, not a silent no-op — the engine "succeeded"
        // but has nothing to actually speak the default locale with.
        when (val langResult = tts.setLanguage(Locale.getDefault())) {
            TextToSpeech.LANG_MISSING_DATA, TextToSpeech.LANG_NOT_SUPPORTED -> {
                val reason = "TTS language unavailable (result=$langResult)"
                SafeLog.error(reason)
                statusListener(TtsEngineStatus.Failed(reason))
                ready.completeExceptionally(IllegalStateException(reason))
            }
            else -> {
                statusListener(TtsEngineStatus.Ready)
                ready.complete(Unit)
            }
        }
    }

    private val tts: TextToSpeech = if (enginePackage != null) {
        TextToSpeech(context.applicationContext, initListener, enginePackage)
    } else {
        TextToSpeech(context.applicationContext, initListener)
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

    /**
     * AGENTS.md §4.8: "Enumerate with `TextToSpeech.getEngines()`, show
     * the list in settings." [TextToSpeech.getEngines] is an instance
     * method, not static, but it returns every engine installed on the
     * device regardless of which one *this* instance is bound to — safe
     * to call even while this instance's own init is still pending.
     */
    fun listEngines(): List<TextToSpeech.EngineInfo> = tts.engines

    override fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
