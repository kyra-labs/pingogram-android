package dev.kyralabs.pingogram.ui

import android.app.Activity
import android.content.ComponentName
import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint
import org.junit.rules.ExternalResource
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * An empty Hilt activity for Compose tests of destinations with Hilt view models. The long form of
 * `@AndroidEntryPoint` needs no bytecode transformation of test classes.
 */
@AndroidEntryPoint(ComponentActivity::class)
class HiltTestActivity : Hilt_HiltTestActivity()

/**
 * Declares [activity] to Robolectric so a compose rule can launch it. The usual `ui-test-manifest` would have to be a
 * debug dependency of the app, which ships the activity in debug APKs.
 */
class ActivityRegistration(
    private val activity: Class<out Activity>,
) : ExternalResource() {
    override fun before() {
        val application = RuntimeEnvironment.getApplication()
        shadowOf(application.packageManager).addActivityIfNotPresent(ComponentName(application, activity))
    }
}
