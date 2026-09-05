package net.breadthcharge.exigentheron.listener

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.breadthcharge.exigentheron.App
import net.breadthcharge.exigentheron.SafeLog
import net.breadthcharge.exigentheron.domain.Decision
import net.breadthcharge.exigentheron.domain.NotificationPayload
import net.breadthcharge.exigentheron.domain.SpeechRequest

/**
 * Routing only, per AGENTS.md §3: extract → dedup → rules → secret scan
 * → speak, each step exactly one call. No decision-making of its own —
 * see [route] if that stops being true at a glance.
 */
class NotificationTtsListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val container get() = (application as App).container

    override fun onListenerConnected() {
        super.onListenerConnected()
        SafeLog.lifecycle("listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        SafeLog.lifecycle("listener disconnected")
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val payload = NotificationExtractor.extract(sbn, ownPackageName = packageName) ?: return
        if (container.deduplicator.isDuplicate(payload.packageName, payload.contentHash)) return

        // Off the binder thread this callback runs on — RuleEngine's
        // matching can take up to its own 100ms timeout budget, and
        // onNotificationPosted must return promptly.
        scope.launch { route(payload) }
    }

    private suspend fun route(payload: NotificationPayload) {
        val ruleDecision = container.ruleEngine.evaluate(payload)
        val decision = container.secretDetector.scan(ruleDecision, payload)

        val text = when (decision) {
            is Decision.Speak -> decision.text
            is Decision.AnnounceOnly -> decision.text
            is Decision.Suppress -> {
                SafeLog.decision(payload.packageName, ruleId = null, action = "suppress")
                return
            }
        }
        SafeLog.decision(payload.packageName, ruleId = null, action = decision::class.simpleName.orEmpty())
        container.speechQueue.enqueue(SpeechRequest(text = text, utteranceId = UUID.randomUUID().toString()))
    }
}
