package net.breadthcharge.exigentheron.ui.rules

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A regression test for the `<queries>` block in `AndroidManifest.xml`
 * rather than for this function's own three lines.
 *
 * Under Android 11+ package-visibility rules — enforced at both minSdk 31
 * and targetSdk 37 — `queryIntentActivities` returns *nothing but this
 * app* unless the manifest declares the launcher intent it's querying
 * for. Dropping that declaration doesn't fail to build and doesn't
 * throw: the app picker simply comes back empty, which is why
 * wiki/testing.md's "App picker" manual check is a human opening the
 * picker and checking it isn't. This asserts the same thing without the human.
 */
@RunWith(AndroidJUnit4::class)
class InstalledAppsTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun theLauncherQuerySeesAppsBesidesThisOne() {
        val apps = loadInstalledApps(context)

        // Any stock image has a launcher, settings, and more; the
        // failure this guards against returns zero or only this app.
        assertThat(apps).isNotEmpty()
        assertThat(apps.map { it.packageName }.filter { it != context.packageName }).isNotEmpty()
    }

    @Test
    fun resultsAreDistinctByPackageAndSortedByLabel() {
        val apps = loadInstalledApps(context)

        val packages = apps.map { it.packageName }
        assertThat(packages).containsNoDuplicates()
        assertThat(apps.map { it.label.lowercase() })
            .isInOrder(Comparator<String> { a, b -> a.compareTo(b) })
    }

    @Test
    fun everyEntryHasANonBlankLabel() {
        assertThat(loadInstalledApps(context).filter { it.label.isBlank() }).isEmpty()
    }
}
