package net.breadthcharge.exigentheron.domain

/**
 * PURE (clock injected). No Android imports — see AGENTS.md §3.
 *
 * Apps repost notifications constantly (progress updates, read
 * receipts, reactions); reading each repost aloud is the #1 quality
 * complaint this app exists to fix — see AGENTS.md §4.3.
 *
 * Keyed on `packageName + ":" + contentHash`, **not** [NotificationPayload.key]
 * — the key stays constant across reposts of the same conversation, so
 * keying on it alone would suppress genuinely new messages there.
 *
 * LRU with TTL: [isDuplicate] both answers the question and records the
 * call as a side effect (refreshing the entry's timestamp), so the
 * window slides with each repost rather than expiring from the first
 * sighting. Bounded at [maxEntries] regardless of TTL, evicting the
 * least-recently-touched entry first, so a burst of distinct content
 * can't grow this without limit.
 */
class Deduplicator(
    private val clock: () -> Long,
    private val ttlMillis: Long = 60_000,
    private val maxEntries: Int = 200,
) {
    // accessOrder=true turns iteration order into LRU order: the first
    // entry is always the least-recently-touched one, which is exactly
    // what eviction below wants.
    private val lastSeen = LinkedHashMap<String, Long>(16, 0.75f, true)

    /**
     * Returns true if this exact (package, content) pair was already
     * seen within [ttlMillis]. Always records the current call — even
     * a non-duplicate — as the new "last seen" time for this key.
     */
    @Synchronized
    fun isDuplicate(packageName: String, contentHash: String): Boolean {
        val now = clock()
        val key = "$packageName:$contentHash"
        val previous = lastSeen[key]
        val duplicate = previous != null && now - previous < ttlMillis

        lastSeen[key] = now
        evictOverflow()

        return duplicate
    }

    private fun evictOverflow() {
        val iterator = lastSeen.keys.iterator()
        while (lastSeen.size > maxEntries && iterator.hasNext()) {
            iterator.next()
            iterator.remove()
        }
    }
}
