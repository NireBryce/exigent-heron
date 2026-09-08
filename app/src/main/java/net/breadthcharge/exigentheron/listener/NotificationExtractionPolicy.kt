package net.breadthcharge.exigentheron.listener

/**
 * Drop if: FLAG_ONGOING_EVENT, FLAG_GROUP_SUMMARY, own package, or both title and body empty.
 * Pure function over plain values (not Android objects) so it's unit-testable on the JVM.
 * Zero Android imports by design — [NotificationExtractor] is the thin, Android-bound glue that
 * reads these values off platform objects and passes them here.
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
