package dev.kyralabs.pingogram.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The demo's banner above a screen (`docs/adr/0034-demo-mode-for-store-review.md`): the screen's top bar lays out below
 * it, as the banner takes the status bar's inset. Light, dark, right to left and at 200% font scale.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class DemoBannerScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ActivityRegistration(ComponentActivity::class.java)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun light() = capture("light", ThemeMode.Light)

    @Test
    fun dark() = capture("dark", ThemeMode.Dark)

    @Test
    @Config(qualifiers = "ldrtl-w411dp-h891dp-xxhdpi")
    fun rtl() = capture("rtl", ThemeMode.Light)

    @Test
    @Config(fontScale = 2f)
    fun fontScale200() = capture("fontScale200", ThemeMode.Light)

    @OptIn(ExperimentalRoborazziApi::class, ExperimentalMaterial3Api::class)
    private fun capture(
        name: String,
        themeMode: ThemeMode,
    ) {
        composeRule.setContent {
            PingogramTheme(themeMode = themeMode, dynamicColor = false) {
                DemoFrame(isDemo = true) {
                    Scaffold(topBar = { TopAppBar(title = { Text("Chats") }) }) { padding ->
                        Box(Modifier.padding(padding).fillMaxSize())
                    }
                }
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage("src/test/snapshots/${javaClass.name}.$name.png")
    }
}
