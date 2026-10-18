package dev.kyralabs.pingogram.feature.auth.ui

import android.content.ComponentName
import androidx.activity.ComponentActivity
import org.junit.rules.ExternalResource
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** A compact phone in portrait (`docs/TESTING.md`). */
const val COMPACT = "w411dp-h891dp-xxhdpi"

/** An expanded window, e.g. a tablet or an unfolded foldable. */
const val EXPANDED = "w840dp-h891dp-xxhdpi"

/** A compact phone in landscape: an expanded width with a compact height. */
const val LANDSCAPE = "w891dp-h411dp-land-xxhdpi"

/**
 * Declares [ComponentActivity] to Robolectric so the compose rule can launch it, as in `:app`'s UI tests; the usual
 * `ui-test-manifest` would be one more dependency. Its theme has no action bar, so whole-screen captures show the
 * login screen only, as the app's edge-to-edge activity does.
 */
class ComponentActivityRegistration : ExternalResource() {
    override fun before() {
        val application = RuntimeEnvironment.getApplication()
        val packageManager = shadowOf(application.packageManager)
        val activity = packageManager.addActivityIfNotPresent(ComponentName(application, ComponentActivity::class.java))
        activity.theme = android.R.style.Theme_Material_Light_NoActionBar
        packageManager.addOrUpdateActivity(activity)
    }
}

/** Where a golden of [testClass] is recorded and verified: `src/test/snapshots`, as Roborazzi names them. */
fun goldenPath(
    testClass: Class<*>,
    name: String,
): String = "src/test/snapshots/${testClass.name}.$name.png"
