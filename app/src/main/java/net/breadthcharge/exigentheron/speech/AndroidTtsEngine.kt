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

/** Observable init/failure state — always visible, never a silent failure. */
sealed interface TtsEngineStatus {
    data object Initializing : TtsEngineStatus
    data object Ready : TtsEngineStatus
    data class Failed(val reason: String) : TtsEngineStatus
}

/**
 * Wraps [android.speech.tts.TextToSpeech]. [enginePackage] is the user's persisted choice,
 * null only temporarily until they pick one in settings (never a permanent silent fallback to
 * system default). Exposes [statusListener] so settings can surface init failures and
 * missing-language data — never silent failures.
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
        // LANG_MISSING_DATA/LANG_NOT_SUPPORTED are visible errors: the engine "succeeded"
        // but has nothing to speak the default locale with — surface this, don't silently fail.
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

                // Fired by tts.stop() below for whatever utterance was
                // mid-flight — without this, stop()'s caller would hang
                // forever awaiting a done/error callback that stop()
                // itself guarantees will never come.
                override fun onStop(utteranceId: String?, interrupted: Boolean) = resumePending(utteranceId)
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
     * List all installed TTS engines for the settings picker.
     * [TextToSpeech.getEngines] is an instance method but returns every engine on the device,
     * not just the one this instance uses — safe to call during init.
     */
    fun listEngines(): List<TextToSpeech.EngineInfo> = tts.engines

    override fun stop() {
        tts.stop()
    }

    override fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
