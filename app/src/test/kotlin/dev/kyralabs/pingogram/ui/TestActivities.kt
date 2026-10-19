package dev.kyralabs.pingogram.ui

import android.Manifest
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
 * debug dependency of the app, which ships the activity in debug APKs. Its theme has no action bar, so the content
 * takes the whole window, as in the app's edge-to-edge activity, and screenshots match the device.
 */
class ActivityRegistration(
    private val activity: Class<out Activity>,
) : ExternalResource() {
    override fun before() {
        val application = RuntimeEnvironment.getApplication()
        val packageManager = shadowOf(application.packageManager)
        val info = packageManager.addActivityIfNotPresent(ComponentName(application, activity))
        info.theme = android.R.style.Theme_Material_Light_NoActionBar
        packageManager.addOrUpdateActivity(info)
    }
}

/**
 * Allows notifications (Android 13+'s runtime permission), so the chat list shows no request to allow them: screenshots
 * of the app's shell, whose chat list would otherwise begin with it (`NotificationPromptScreenshotTest` in
 * `feature:chats` has it).
 */
class NotificationsAllowed : ExternalResource() {
    override fun before() {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }
}
