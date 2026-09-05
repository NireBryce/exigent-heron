package net.breadthcharge.exigentheron.debug

import net.breadthcharge.exigentheron.domain.NotificationPayload
import net.breadthcharge.exigentheron.domain.contentHashOf

/**
 * Synthetic [NotificationPayload]s for exercising the domain pipeline
 * (`Deduplicator` → `RuleEngine` → `SecretDetector`) without a device or
 * a real `NotificationListenerService` binding — see AGENTS.md §6 Phase 1.
 *
 * `debug/` source set only. If you're reading this from `app/src/main`,
 * something is wrong — it must not exist in a release build.
 */
object FakeNotifications {

    fun payload(
        packageName: String = "com.example.chat",
        title: String? = "Alex",
        body: String? = "Hey, are you around?",
        key: String = "fake:${System.nanoTime()}",
        postTime: Long = System.currentTimeMillis(),
        isGroupSummary: Boolean = false,
        isOngoing: Boolean = false,
        visibility: Int = 1, // Notification.VISIBILITY_PUBLIC
    ): NotificationPayload = NotificationPayload(
        key = key,
        packageName = packageName,
        postTime = postTime,
        title = title,
        body = body,
        isGroupSummary = isGroupSummary,
        isOngoing = isOngoing,
        visibility = visibility,
        contentHash = contentHashOf(title, body),
    )

    /** A handful of payloads covering the pipeline's interesting cases by hand. */
    fun scenarios(): List<NotificationPayload> = listOf(
        payload(title = "Alex", body = "Hey, are you around?"),
        payload(
            packageName = "com.example.bank",
            title = "Bank",
            body = "Your verification code is 483921. Don't share it.",
        ),
        payload(packageName = "com.example.spam", title = null, body = null),
    )
}
