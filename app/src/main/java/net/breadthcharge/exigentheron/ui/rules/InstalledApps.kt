package net.breadthcharge.exigentheron.ui.rules

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

data class InstalledApp(val label: String, val packageName: String)

/**
 * Launchable apps only (matches the `<queries>` declaration in
 * `AndroidManifest.xml` — package-visibility rules on API 30+ otherwise
 * hide everything but this app itself from `PackageManager`). No icons:
 * AGENTS.md §2 has no image-loading dependency and doesn't want one.
 *
 * Blocking (`PackageManager` calls) — call from a background dispatcher.
 */
fun loadInstalledApps(context: Context): List<InstalledApp> {
    val pm = context.packageManager
    val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(launcherIntent, 0)
        .map { InstalledApp(label = it.loadLabel(pm).toString(), packageName = it.activityInfo.packageName) }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}
