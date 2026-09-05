package net.breadthcharge.exigentheron.listener

/**
 * The drop conditions from AGENTS.md §4.2, as a pure function over
 * plain values rather than an `android.service.notification.StatusBarNotification`.
 * Deliberately has zero Android imports — not because it's `domain/`
 * (it's notification-extraction policy, not rule/secret/dedup logic),
 * but so it's unit-testable on the JVM the same way `domain/` is,
 * without needing a real or mocked Android object to exercise it.
 * [NotificationExtractor] is the thin, untestable-without-a-device glue
 * that reads these values off the real platform types and calls this.
 */
fun shouldDropNotification(
    isOngoing: Boolean,
    isGroupSummary: Boolean,
    packageName: String,
    ownPackageName: String,
    title: String?,
    body: String?,
): Boolean =
    isOngoing ||
        isGroupSummary ||
        packageName == ownPackageName ||
        (title.isNullOrEmpty() && body.isNullOrEmpty())
