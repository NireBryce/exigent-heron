package net.breadthcharge.exigentheron.domain

import java.security.MessageDigest

/**
 * Stable hash of a notification's title+body — see
 * [NotificationPayload.contentHash] and [Deduplicator]. Pure JVM
 * (`java.security`, not an Android API), so it belongs in `domain/`
 * rather than duplicated between `NotificationExtractor` (real
 * notifications) and `FakeNotifications` (debug/ synthetic ones) — it
 * started as the latter's private copy in Phase 1; sharing it here is
 * what keeps the two from silently drifting apart.
 */
fun contentHashOf(title: String?, body: String?): String {
    val digest = MessageDigest.getInstance("SHA-256").digest("$title|$body".toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
}
