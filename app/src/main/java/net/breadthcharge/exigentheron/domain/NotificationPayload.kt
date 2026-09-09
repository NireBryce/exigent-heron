package net.breadthcharge.exigentheron.domain

/**
 * ANDROID-FREE. No Android imports — domain/ must be unit-testable on the JVM without Robolectric. Constructed by
 * [NotificationExtractor][net.breadthcharge.exigentheron.listener.NotificationExtractor]
 * (Android-facing) from a `StatusBarNotification`.
 *
 * [toString] is overridden deliberately — the generated data-class `toString()` would include [title]
 * and [body], which is exactly how notification content ends up in logcat via string interpolation.
 * This override must never contain title or body. Do not remove it.
 */
data class NotificationPayload(
    val key: String,
    val packageName: String,
    val postTime: Long,
    val title: String?,
    val body: String?,
    val isGroupSummary: Boolean,
    val isOngoing: Boolean,
    val visibility: Int,
    /** Stable hash of title+body — see [Deduplicator]. */
    val contentHash: String,
) {
    override fun toString(): String = "NotificationPayload(key=$key, pkg=$packageName)"
}
