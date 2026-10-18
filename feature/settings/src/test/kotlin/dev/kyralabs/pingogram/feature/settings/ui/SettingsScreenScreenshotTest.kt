package dev.kyralabs.pingogram.feature.settings.ui

import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The Settings screen with and without the log out confirmation; screen captures, so the dialog's window shows. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class SettingsScreenScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration =
        object : ExternalResource() {
            // Declares ComponentActivity to Robolectric (ui-test-manifest is not a dependency), without the platform
            // action bar, which would show in screen captures.
            override fun before() {
                val application = RuntimeEnvironment.getApplication()
                val activity =
                    ActivityInfo().apply {
                        name = ComponentActivity::class.java.name
                        packageName = application.packageName
                        theme = android.R.style.Theme_DeviceDefault_NoActionBar
                    }
                shadowOf(application.packageManager).addOrUpdateActivity(activity)
            }
        }

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun light() = capture(ThemeMode.Light, SettingsUiState())

    @Test
    fun dark() = capture(ThemeMode.Dark, SettingsUiState())

    @Test
    fun logOutDialogLight() = capture(ThemeMode.Light, SettingsUiState(isLogOutDialogOpen = true))

    @Test
    fun logOutDialogDark() = capture(ThemeMode.Dark, SettingsUiState(isLogOutDialogOpen = true))

    // captureScreenRoboImage captures the dialog window too; Roborazzi still marks it experimental.
    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        themeMode: ThemeMode,
        state: SettingsUiState,
    ) {
        composeRule.setContent {
            PingogramTheme(themeMode = themeMode, dynamicColor = false) { SettingsScreen(state = state, onEvent = {}) }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage()
    }
}
