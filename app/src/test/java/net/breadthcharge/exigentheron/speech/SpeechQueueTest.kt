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

    /** Records calls; [onSpeak] lets a test hold the consumer at a known point. */
    private class FakeTtsEngine(
        private val onSpeak: suspend (text: String) -> Unit = {},
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
        val queue = SpeechQueue(fake, { true }, {}, { false }, scope)

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
        val queue = SpeechQueue(fake, { true }, {}, { false }, scope)

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
        val queue = SpeechQueue(fake, { true }, { abandonCalls.incrementAndGet() }, { true }, scope)

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
    fun `audio focus is requested once for a burst, not once per item`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob())
        val requestCalls = AtomicInteger()
        val releaseFirst = CompletableDeferred<Unit>()
        // Gate the first speak() so all three enqueues land as one
        // burst in the channel before the consumer processes any of it.
        val fake = FakeTtsEngine(onSpeak = { text -> if (text == "one") releaseFirst.await() })
        val queue = SpeechQueue(fake, { requestCalls.incrementAndGet(); true }, {}, { false }, scope)

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
        val queue = SpeechQueue(fake, { true }, { abandonCalls.incrementAndGet() }, { false }, scope)

        queue.enqueue(request("only one"))
        withTimeout(5.seconds) {
            while (abandonCalls.get() < 1) kotlinx.coroutines.yield()
        }

        assertThat(abandonCalls.get()).isEqualTo(1)
        scope.shutdown()
    }

    @Test
    fun `overflow drops the oldest pending items, not the newest`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob())
        val releaseFirst = CompletableDeferred<Unit>()
        // Hold the consumer on item "0" so items "1".."39" all land in
        // the 32-capacity buffer at once, forcing real overflow.
        val fake = FakeTtsEngine(onSpeak = { text -> if (text == "0") releaseFirst.await() })
        val queue = SpeechQueue(fake, { true }, {}, { false }, scope)

        queue.enqueue(request("0"))
        for (i in 1..39) queue.enqueue(request(i.toString()))
        releaseFirst.complete(Unit)
        // "0" (already dequeued before the gate) + the newest 32 of
        // "1".."39" survive: "8".."39".
        awaitCount(fake.speakCalls, 33)

        val expected = listOf("0") + (8..39).map { it.toString() }
        assertThat(fake.speakCalls).containsExactlyElementsIn(expected).inOrder()
        scope.shutdown()
    }
}
