package net.breadthcharge.exigentheron.domain

import java.security.MessageDigest

/**
 * Stable hash of a notification's title+body — see
 * [NotificationPayload.contentHash] and [Deduplicator]. Pure JVM
 * (`java.security`, not an Android API), extracted to `domain/` so
 * [NotificationExtractor] (real notifications) and [FakeNotifications]
 * (debug/ synthetic ones) use the same implementation and can't drift apart.
 */
fun contentHashOf(title: String?, body: String?): String {
    val digest = MessageDigest.getInstance("SHA-256").digest("$title|$body".toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
}
