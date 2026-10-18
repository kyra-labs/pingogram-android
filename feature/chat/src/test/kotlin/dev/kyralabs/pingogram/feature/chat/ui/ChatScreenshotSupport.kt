package dev.kyralabs.pingogram.feature.chat.ui

import android.content.ComponentName
import androidx.activity.ComponentActivity
import org.junit.rules.ExternalResource
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** A compact phone in portrait (`docs/TESTING.md`). */
const val COMPACT = "w411dp-h891dp-xxhdpi"

/** An expanded window, e.g. a tablet or an unfolded foldable. */
const val EXPANDED = "w840dp-h891dp-xxhdpi"

/**
 * Declares [ComponentActivity] to Robolectric so a compose rule can launch it, without the platform action bar,
 * which whole-screen captures would show.
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
