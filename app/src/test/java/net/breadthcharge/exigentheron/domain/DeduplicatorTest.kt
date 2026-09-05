package net.breadthcharge.exigentheron.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The five cases AGENTS.md §4.3 requires, plus the side-effect contract. */
class DeduplicatorTest {

    private class FakeClock(private var now: Long = 0) {
        fun advanceBy(millis: Long) {
            now += millis
        }
        fun get(): Long = now
    }

    @Test
    fun `same content twice in 5s is a duplicate`() {
        val clock = FakeClock()
        val dedup = Deduplicator(clock = clock::get)

        assertThat(dedup.isDuplicate("com.example.chat", "hash-1")).isFalse()
        clock.advanceBy(5_000)
        assertThat(dedup.isDuplicate("com.example.chat", "hash-1")).isTrue()
    }

    @Test
    fun `same content twice 90s apart is not a duplicate`() {
        val clock = FakeClock()
        val dedup = Deduplicator(clock = clock::get)

        assertThat(dedup.isDuplicate("com.example.chat", "hash-1")).isFalse()
        clock.advanceBy(90_000)
        assertThat(dedup.isDuplicate("com.example.chat", "hash-1")).isFalse()
    }

    @Test
    fun `same sbn key but different content is not a duplicate`() {
        // Deduplicator never sees sbn.key at all — it's keyed on
        // packageName+contentHash precisely so a repost under the same
        // key with new content isn't suppressed. This test documents
        // that by construction rather than by passing a key in.
        val clock = FakeClock()
        val dedup = Deduplicator(clock = clock::get)

        assertThat(dedup.isDuplicate("com.example.chat", "hash-1")).isFalse()
        assertThat(dedup.isDuplicate("com.example.chat", "hash-2")).isFalse()
    }

    @Test
    fun `different package same content is not a duplicate`() {
        val clock = FakeClock()
        val dedup = Deduplicator(clock = clock::get)

        assertThat(dedup.isDuplicate("com.example.chat", "hash-1")).isFalse()
        assertThat(dedup.isDuplicate("com.example.other", "hash-1")).isFalse()
    }

    @Test
    fun `300 distinct entries keeps map size at or below 200`() {
        val clock = FakeClock()
        val dedup = Deduplicator(clock = clock::get, maxEntries = 200)

        repeat(300) { i -> dedup.isDuplicate("com.example.chat", "hash-$i") }

        // No direct size accessor by design (it's an implementation
        // detail) — assert the bound behaviorally: the earliest entries
        // must have been evicted, so re-seeing #0 is not a duplicate.
        assertThat(dedup.isDuplicate("com.example.chat", "hash-0")).isFalse()
        // ...while a recent one, still within the window, is.
        assertThat(dedup.isDuplicate("com.example.chat", "hash-299")).isTrue()
    }

    @Test
    fun `isDuplicate refreshes the window on every call, including duplicates`() {
        val clock = FakeClock()
        val dedup = Deduplicator(clock = clock::get, ttlMillis = 60_000)

        assertThat(dedup.isDuplicate("com.example.chat", "hash-1")).isFalse()
        clock.advanceBy(50_000)
        assertThat(dedup.isDuplicate("com.example.chat", "hash-1")).isTrue() // refreshes timestamp
        clock.advanceBy(50_000) // 100s since first sighting, but only 50s since the refresh
        assertThat(dedup.isDuplicate("com.example.chat", "hash-1")).isTrue()
    }
}
