package net.breadthcharge.exigentheron.speech

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The one fact a fake `TtsEngine` can never establish: what the *real*
 * `TextToSpeech` does. A fake's `stop()` does whatever its test wrote it
 * to do, so `SpeechQueueTest` proves the queue calls `stop()` — not that
 * calling it achieves anything.
 *
 * Two behaviours depend on it and would fail silently if the platform
 * ever stopped honouring them:
 *
 * - `SpeechQueue.stopCurrent()`, the `ACTION_AUDIO_BECOMING_NOISY` half
 *   of the output-route gate, needs `stop()` to unblock the suspended
 *   `speak()` so the consumer moves to the next item. That relies on
 *   `onStop` firing on the pending utterance's continuation.
 * - The truncation timeout calls `stop()` explicitly *because* cancelling
 *   the coroutine only abandons the wait, leaving the engine playing.
 *
 * Skipped rather than failed where no usable engine exists — a device
 * with no TTS data installed says nothing about this code.
 */
@RunWith(AndroidJUnit4::class)
class AndroidTtsEngineTest {

    private lateinit var engine: AndroidTtsEngine
    private var status: TtsEngineStatus = TtsEngineStatus.Initializing

    @Before
    fun setUp() {
        engine = AndroidTtsEngine(
            ApplicationProvider.getApplicationContext<Context>(),
        ) { status = it }
    }

    @After
    fun tearDown() {
        engine.shutdown()
    }

    private suspend fun awaitReady(): Boolean {
        withTimeoutOrNull(20.seconds) {
            while (status is TtsEngineStatus.Initializing) delay(50)
        }
        return status is TtsEngineStatus.Ready
    }

    @Test
    fun initReportsAConcreteStatusRatherThanStayingSilent() = runBlocking<Unit> {
        awaitReady()

        // Whichever way it went, it must not still be Initializing —
        // AGENTS.md §4.8's "not a silent no-op".
        assertThat(status).isNotInstanceOf(TtsEngineStatus.Initializing::class.java)
    }

    @Test
    fun stopUnblocksASuspendedSpeak() = runBlocking<Unit> {
        assumeTrue("no usable TTS engine on this device", awaitReady())

        val speaking = async {
            engine.speak(
                "This is a deliberately long utterance, long enough that the call below " +
                    "interrupts it rather than racing its natural completion.",
                "utterance-under-test",
            )
        }
        // Let the utterance actually start before interrupting it.
        delay(500)
        engine.stop()

        // The assertion is that this returns at all: without onStop
        // resuming the continuation, it would hang until the timeout.
        withTimeout(10.seconds) { speaking.await() }
    }

    @Test
    fun silenceCompletesOnItsOwn() = runBlocking<Unit> {
        assumeTrue("no usable TTS engine on this device", awaitReady())

        withTimeout(10.seconds) { engine.silence(200L, "silence-under-test") }
    }

    /**
     * `listEngines()` backs the settings picker. It is an instance method
     * that reports every engine on the device, and the picker is useless
     * if it comes back empty where one demonstrably exists.
     */
    @Test
    fun listEnginesSeesAtLeastTheOneInUse() = runBlocking<Unit> {
        assumeTrue("no usable TTS engine on this device", awaitReady())

        assertThat(engine.listEngines().map { it.name }).isNotEmpty()
    }
}
