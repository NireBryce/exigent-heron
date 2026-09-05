package net.breadthcharge.exigentheron

import android.util.Log

/**
 * The only permitted entry point to [android.util.Log] in this codebase —
 * see AGENTS.md §4.6. There is deliberately no function here that accepts
 * an arbitrary String. If you find yourself wanting one so you can log a
 * notification title or body, that is the feature working: put whatever
 * you were about to log somewhere that isn't logcat.
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
