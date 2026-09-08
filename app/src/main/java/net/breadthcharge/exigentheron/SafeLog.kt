package net.breadthcharge.exigentheron

import android.util.Log

/**
 * The only permitted entry point to [android.util.Log] in this codebase.
 * Three functions only: decision(pkg, ruleId, action), lifecycle(msg), error(msg, t?).
 * Deliberately no arbitrary-String function — if you're reaching for one to log notification
 * content, that's the safety feature working. Put that data somewhere other than logcat.
 *
 * CI (or a pre-commit grep) should assert `android.util.Log` appears in
 * exactly this one file.
 */
internal object SafeLog {

    private const val TAG = "ExigentHeron"

    /** A rule-engine decision. Never pass notification title/body here. */
    fun decision(pkg: String, ruleId: String?, action: String) {
        Log.d(TAG, "decision pkg=$pkg rule=${ruleId ?: "none"} action=$action")
    }

    /** Component lifecycle events (service (dis)connected, app start, ...). */
    fun lifecycle(msg: String) {
        Log.i(TAG, msg)
    }

    fun error(msg: String, t: Throwable? = null) {
        Log.e(TAG, msg, t)
    }
}
