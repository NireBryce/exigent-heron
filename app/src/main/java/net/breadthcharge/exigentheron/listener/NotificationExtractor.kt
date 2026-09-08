package net.breadthcharge.exigentheron.listener

import android.app.Notification
import android.service.notification.StatusBarNotification
import net.breadthcharge.exigentheron.domain.NotificationPayload
import net.breadthcharge.exigentheron.domain.contentHashOf
import net.breadthcharge.exigentheron.domain.sanitizeNotificationText

/**
 * Converts `StatusBarNotification` → [NotificationPayload].
 * Reads the platform object, sanitizes (strip control chars and zero-width), and delegates
 * the drop decision to [shouldDropNotification] — no other logic of its own.
 *
 * Deliberately reads only [Notification.EXTRA_TITLE],
 * [Notification.EXTRA_TEXT], and [Notification.EXTRA_BIG_TEXT] (preferring
 * big text when present). Does **not** read `EXTRA_TEXT_LINES` or
 * `EXTRA_MESSAGES` — those carry prior conversation history, and reading
 * them means one new message speaks the whole thread. Do not add that.
 */
object NotificationExtractor {

    fun extract(sbn: StatusBarNotification, ownPackageName: String): NotificationPayload? {
        val notification = sbn.notification
        val isOngoing = notification.flags and Notification.FLAG_ONGOING_EVENT != 0
        val isGroupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0

        val extras = notification.extras
        val title = sanitizeNotificationText(extras.getCharSequence(Notification.EXTRA_TITLE)?.toString())
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val body = sanitizeNotificationText(bigText ?: text)

        if (shouldDropNotification(isOngoing, isGroupSummary, sbn.packageName, ownPackageName, title, body)) {
            return null
        }

        return NotificationPayload(
            key = sbn.key,
            packageName = sbn.packageName,
            postTime = sbn.postTime,
            title = title,
            body = body,
            isGroupSummary = isGroupSummary,
            isOngoing = isOngoing,
            visibility = notification.visibility,
            contentHash = contentHashOf(title, body),
        )
    }
}
