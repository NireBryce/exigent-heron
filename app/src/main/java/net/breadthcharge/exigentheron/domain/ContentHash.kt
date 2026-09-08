package net.breadthcharge.exigentheron.domain

import java.security.MessageDigest

/**
 * Stable hash of a notification's title+body — see
 * [NotificationPayload.contentHash] and [Deduplicator]. Pure JVM
 * (`java.security`, not an Android API), extracted to `domain/` so
 * [NotificationExtractor][net.breadthcharge.exigentheron.listener.NotificationExtractor]
 * (real notifications) and `debug/FakeNotifications.kt` (synthetic ones)
 * use the same implementation and can't drift apart. The latter is a
 * path rather than a link on purpose: it lives in the `debug` source
 * set, which `main`'s KDoc can't resolve a reference into at all.
 */
fun contentHashOf(title: String?, body: String?): String {
    val digest = MessageDigest.getInstance("SHA-256").digest("$title|$body".toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
}
