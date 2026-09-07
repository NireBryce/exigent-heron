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
        val queue = SpeechQueue(fake, { true }, {}, { false }, { false }, { true }, scope)

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
        val queue = SpeechQueue(fake, { true }, {}, { false }, { false }, { true }, scope)

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
        val queue = SpeechQueue(fake, { true }, { abandonCalls.incrementAndGet() }, { true }, { false }, { true }, scope)

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
        val queue = SpeechQueue(fake, { true }, { abandonCalls.incrementAndGet() }, { false }, { true }, { true }, scope)

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
        val queue = SpeechQueue(fake, { requestCalls.incrementAndGet(); true }, {}, { false }, { false }, { true }, scope)

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
        val queue = SpeechQueue(fake, { true }, { abandonCalls.incrementAndGet() }, { false }, { false }, { true }, scope)

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
        val fake = FakeTtsEngine()
        // No gate needed: enqueue() is a fast, non-suspending trySend()
        // on this test's own thread, and none of these ten calls yields
        // — same assumption the "requests are spoken in order" test
        // above already relies on for its (smaller, ungated) burst.
        val queue = SpeechQueue(fake, { true }, {}, { false }, { false }, { true }, scope)

        for (i in 1..10) queue.enqueue(request(i.toString()))
        awaitCount(fake.speakCalls, 1)

        // Give any (incorrect) extra speak() calls a moment to show up
        // before asserting there's exactly one.
        withTimeout(2.seconds) { kotlinx.coroutines.delay(200) }
        assertThat(fake.speakCalls).containsExactly("10 new notifications.")
        scope.shutdown()
    }

    @Test
    fun `5 or fewer pending items are read individually, not collapsed`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob())
        val fake = FakeTtsEngine()
        val queue = SpeechQueue(fake, { true }, {}, { false }, { false }, { true }, scope)

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
        val queue = SpeechQueue(fake, { true }, { abandonCalls.incrementAndGet() }, { false }, { false }, { false }, scope)

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
        val queue = SpeechQueue(fake, { true }, {}, { false }, { false }, { routeAllowed }, scope)

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
        val queue = SpeechQueue(fake, { true }, {}, { false }, { false }, { true }, scope)

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
