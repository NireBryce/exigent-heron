package net.breadthcharge.exigentheron.speech

import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.breadthcharge.exigentheron.domain.SpeechRequest
import org.junit.Test

/**
 * Real time throughout (not `runTest`'s virtual time), and real
 * background dispatch (`SpeechQueue` hardcodes `Dispatchers.Default` for
 * its consumer) — see `RuleEngineTest`'s own note on why virtual time
 * doesn't mix safely with a real dispatcher. Coordination between the
 * test and the consumer coroutine goes through suspending gates
 * ([CompletableDeferred], [Channel]), never a raw sleep-then-peek.
 */
class SpeechQueueTest {

    /**
     * Records calls; [onSpeak] lets a test hold the consumer at a known
     * point, and [onStop] lets a test simulate the engine actually
     * interrupting whatever [onSpeak] is suspended in — real
     * `AndroidTtsEngine.stop()` does this via `TextToSpeech.stop()`
     * firing `onStop` on the pending utterance's continuation.
     */
    private class FakeTtsEngine(
        private val onSpeak: suspend (text: String) -> Unit = {},
        private val onStop: () -> Unit = {},
    ) : TtsEngine {
        val speakCalls = CopyOnWriteArrayList<String>()
        val silenceCalls = CopyOnWriteArrayList<Long>()

        override suspend fun speak(text: String, utteranceId: String) {
            onSpeak(text)
            speakCalls += text
        }

        override suspend fun silence(durationMillis: Long, utteranceId: String) {
            silenceCalls += durationMillis
        }

        override fun stop() = onStop()

        override fun shutdown() = Unit
    }

    private fun request(text: String) = SpeechRequest(text = text, utteranceId = text)

    /**
     * Builds a queue whose every gate permits speech: focus is granted,
     * the device is not in a call, DND is not blocking, and the output
     * route qualifies. A test then names only the one thing it varies,
     * so what's under test is the only thing visible at the call site.
     *
     * [SpeechGates] now groups the three checks, so the queue's own
     * constructor no longer takes same-shaped `() -> Boolean` gates in a
     * row. This builder stays because the defaults are what earn their
     * keep: a test names only the gate it varies, instead of restating
     * three permissive lambdas to vary one.
     */
    private fun speechQueue(
        engine: TtsEngine,
        scope: CoroutineScope,
        requestAudioFocus: () -> Boolean = { true },
        abandonAudioFocus: () -> Unit = {},
        isInCall: () -> Boolean = { false },
        isBlockedByDnd: () -> Boolean = { false },
        isOutputRouteAllowed: () -> Boolean = { true },
    ) = SpeechQueue(
        ttsEngine = engine,
        gates = SpeechGates(
            isInCall = isInCall,
            isBlockedByDnd = isBlockedByDnd,
            isOutputRouteAllowed = isOutputRouteAllowed,
        ),
        requestAudioFocus = requestAudioFocus,
        abandonAudioFocus = abandonAudioFocus,
        scope = scope,
    )

    private suspend fun awaitCount(list: List<*>, expected: Int) {
        withTimeout(5.seconds) {
            while (list.size < expected) kotlinx.coroutines.yield()
        }
    }

    /**
     * `scope.cancel()` alone only *requests* cancellation — it doesn't
     * wait for the consumer coroutine to actually stop. `Dispatchers.Default`
     * is one thread pool shared by the whole JVM test process, not
     * scoped per test, so a still-finishing coroutine from this test
     * really can overlap the next one's setup. Caught one doing exactly
     * that — see wiki/traps-and-skills.md.
     */
    private suspend fun CoroutineScope.shutdown() {
        coroutineContext[Job]?.cancelAndJoin()
    }

    @Test
    fun `a single request is spoken, then a silence gap`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob())
        val fake = FakeTtsEngine()
        val queue = speechQueue(fake, scope)

        queue.enqueue(request("hello"))
        awaitCount(fake.speakCalls, 1)
        awaitCount(fake.silenceCalls, 1)

        assertThat(fake.speakCalls).containsExactly("hello")
        assertThat(fake.silenceCalls).containsExactly(400L)
        scope.shutdown()
    }

    @Test
    fun `requests are spoken in order`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob())
        val fake = FakeTtsEngine()
        val queue = speechQueue(fake, scope)

        queue.enqueue(request("first"))
        queue.enqueue(request("second"))
        queue.enqueue(request("third"))
        awaitCount(fake.speakCalls, 3)

        assertThat(fake.speakCalls).containsExactly("first", "second", "third").inOrder()
        scope.shutdown()
    }

    @Test
    fun `in-call skips speaking entirely`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob())
        val fake = FakeTtsEngine()
        val abandonCalls = AtomicInteger()
        val queue = speechQueue(
            fake,
            scope,
            abandonAudioFocus = { abandonCalls.incrementAndGet() },
            isInCall = { true },
        )

        queue.enqueue(request("should not be heard"))
        // No speak() will ever come; wait on something that does happen
        // instead — the queue draining, which still runs even when the
        // in-call check skips the utterance itself.
        withTimeout(5.seconds) {
            while (abandonCalls.get() < 1) kotlinx.coroutines.yield()
        }

        assertThat(fake.speakCalls).isEmpty()
        scope.shutdown()
    }

    @Test
    fun `DND skips speaking entirely`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob())
        val fake = FakeTtsEngine()
        val abandonCalls = AtomicInteger()
        val queue = speechQueue(
            fake,
            scope,
            abandonAudioFocus = { abandonCalls.incrementAndGet() },
            isBlockedByDnd = { true },
        )

        queue.enqueue(request("should not be heard"))
        withTimeout(5.seconds) {
            while (abandonCalls.get() < 1) kotlinx.coroutines.yield()
        }

        assertThat(fake.speakCalls).isEmpty()
        scope.shutdown()
    }

    @Test
    fun `audio focus is requested once for a burst, not once per item`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob())
        val requestCalls = AtomicInteger()
        val releaseFirst = CompletableDeferred<Unit>()
        // Gate the first speak() so all three enqueues land as one
        // burst in the channel before the consumer processes any of it.
        val fake = FakeTtsEngine(onSpeak = { text -> if (text == "one") releaseFirst.await() })
        val queue = speechQueue(fake, scope, requestAudioFocus = { requestCalls.incrementAndGet(); true })

        queue.enqueue(request("one"))
        queue.enqueue(request("two"))
        queue.enqueue(request("three"))
        releaseFirst.complete(Unit)
        awaitCount(fake.speakCalls, 3)

        assertThat(requestCalls.get()).isEqualTo(1)
        scope.shutdown()
    }

    @Test
    fun `audio focus is abandoned once the queue drains`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob())
        val abandonCalls = AtomicInteger()
        val fake = FakeTtsEngine()
        val queue = speechQueue(fake, scope, abandonAudioFocus = { abandonCalls.incrementAndGet() })

        queue.enqueue(request("only one"))
        withTimeout(5.seconds) {
            while (abandonCalls.get() < 1) kotlinx.coroutines.yield()
        }

        assertThat(abandonCalls.get()).isEqualTo(1)
        scope.shutdown()
    }

    // There used to be a test here pinning exactly which items survive
    // `DROP_OLDEST` eviction on a >32-item burst (gating one item's
    // onSpeak() to force the rest to overflow while the consumer waited).
    // Queue-collapse-on-burst (below) removed the property that test
    // depended on: the collapse decision now drains and counts whatever
    // is in the channel *before* any request reaches onSpeak, so a burst
    // large enough to overflow the 32-item channel is — deterministically
    // — also large enough to collapse (the threshold is 5), and the
    // consumer's own concurrent draining means the exact surviving count
    // is no longer a fixed number: it depends on how much the consumer
    // reads in between sends, not just on capacity. `DROP_OLDEST` still
    // bounds memory (it's `Channel`'s own guarantee, not this class's
    // logic to re-test) — it just isn't independently observable through
    // `SpeechQueue`'s output anymore once a burst that large always also
    // collapses.

    @Test
    fun `a burst of more than 5 pending items collapses to one summary utterance`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob())
        val consumerParked = CompletableDeferred<Unit>()
        val releasePrimer = CompletableDeferred<Unit>()
        // Park the consumer inside a primer utterance *before* the burst
        // is enqueued at all, using the same onSpeak gate the audio-focus
        // test above uses.
        //
        // This gate is load-bearing, not ceremony. An earlier version of
        // this test enqueued the ten items with no gate, on the stated
        // assumption that enqueue() is a non-suspending trySend() that
        // never yields — true of the *producer*, but it says nothing
        // about the consumer, which runs on Dispatchers.Default (a
        // different thread) and is free to receive and batch the first
        // few items while the remaining enqueues are still happening. A
        // batch of 5 or fewer is then spoken item-by-item rather than
        // collapsed, which is exactly what the assertion below would
        // see. That version passed locally and failed on CI's slower,
        // more contended runner — see wiki/traps-and-skills.md.
        val fake = FakeTtsEngine(
            onSpeak = { text ->
                if (text == "primer") {
                    consumerParked.complete(Unit)
                    releasePrimer.await()
                }
            },
        )
        val queue = speechQueue(fake, scope)

        queue.enqueue(request("primer"))
        withTimeout(5.seconds) { consumerParked.await() }

        // The consumer is now suspended inside speak("primer"), so all
        // ten land in the channel before it can receive any of them —
        // making the batch it drains next deterministically all ten.
        for (i in 1..10) queue.enqueue(request(i.toString()))
        releasePrimer.complete(Unit)

        awaitCount(fake.speakCalls, 2)
        assertThat(fake.speakCalls).containsExactly("primer", "10 new notifications.").inOrder()
        scope.shutdown()
    }

    @Test
    fun `5 or fewer pending items are read individually, not collapsed`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob())
        val fake = FakeTtsEngine()
        val queue = speechQueue(fake, scope)

        for (i in 1..5) queue.enqueue(request(i.toString()))
        awaitCount(fake.speakCalls, 5)

        assertThat(fake.speakCalls).containsExactly("1", "2", "3", "4", "5").inOrder()
        scope.shutdown()
    }

    @Test
    fun `output route disallowed skips speaking entirely`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob())
        val fake = FakeTtsEngine()
        val abandonCalls = AtomicInteger()
        val queue = speechQueue(
            fake,
            scope,
            abandonAudioFocus = { abandonCalls.incrementAndGet() },
            isOutputRouteAllowed = { false },
        )

        queue.enqueue(request("should not be heard"))
        withTimeout(5.seconds) {
            while (abandonCalls.get() < 1) kotlinx.coroutines.yield()
        }

        assertThat(fake.speakCalls).isEmpty()
        scope.shutdown()
    }

    @Test
    fun `output route is re-checked per item, not just once at construction`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob())
        val fake = FakeTtsEngine()
        // A plain var, not a fixed lambda result — the whole point being
        // tested is that SpeechQueue reads this fresh for every item,
        // the same way AppContainer's real outputRouteGate::allows
        // reflects whatever's connected *now*, not at construction time.
        var routeAllowed = true
        val queue = speechQueue(fake, scope, isOutputRouteAllowed = { routeAllowed })

        queue.enqueue(request("while connected"))
        awaitCount(fake.speakCalls, 1)

        // Simulate a headset disconnecting between items (not mid-utterance
        // — that's stopCurrent's job below) before the next item is due.
        routeAllowed = false
        queue.enqueue(request("after disconnect"))

        // Give an (incorrect) second speak() call a moment to show up
        // before asserting there's still only one.
        withTimeout(2.seconds) { kotlinx.coroutines.delay(200) }
        assertThat(fake.speakCalls).containsExactly("while connected")
        scope.shutdown()
    }

    @Test
    fun `stopCurrent interrupts the in-flight utterance and the queue continues after`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob())
        val started = CompletableDeferred<Unit>()
        val stopSignal = CompletableDeferred<Unit>()
        val fake = FakeTtsEngine(
            onSpeak = { text -> if (text == "one") { started.complete(Unit); stopSignal.await() } },
            onStop = { stopSignal.complete(Unit) },
        )
        val queue = speechQueue(fake, scope)

        queue.enqueue(request("one"))
        queue.enqueue(request("two"))
        started.await() // "one" is now suspended inside onSpeak, as if mid-utterance.

        queue.stopCurrent()
        awaitCount(fake.speakCalls, 2)

        // "one" still counts as spoken (interrupted, not silently
        // dropped — TtsEngine.stop's own contract) and "two" proves the
        // consumer loop resumed normally afterward rather than getting
        // stuck on the interrupted item.
        assertThat(fake.speakCalls).containsExactly("one", "two").inOrder()
        scope.shutdown()
    }
}
