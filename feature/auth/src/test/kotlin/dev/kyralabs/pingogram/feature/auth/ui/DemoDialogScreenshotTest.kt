package dev.kyralabs.pingogram.feature.auth.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.feature.auth.ui.preview.AuthPreviewData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The question after the Welcome screen's logo was held (`docs/adr/0034-demo-mode-for-store-review.md`), over the
 * Welcome screen: light, dark, right to left and at the largest font scale. The Welcome screen's own goldens stay as
 * they were: the dialog shows only once asked for.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class DemoDialogScreenshotTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun light() = capture(ScreenshotVariant.Light)

    @Test
    fun dark() = capture(ScreenshotVariant.Dark)

    // A dialog is a window of its own, whose views take their direction from the configuration and the app's RTL
    // support, not from the composition; the library's test app does not declare that support.
    @Test
    @Config(qualifiers = "+ldrtl")
    fun rtl() {
        val info = ApplicationProvider.getApplicationContext<Context>().applicationInfo
        info.flags = info.flags or ApplicationInfo.FLAG_SUPPORTS_RTL
        capture(ScreenshotVariant.Rtl)
    }

    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun fontScale200() = capture(ScreenshotVariant.Light, name = "fontScale200")

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        variant: ScreenshotVariant,
        name: String = variant.goldenName,
    ) {
        composeRule.setContent {
            ScreenshotTheme(variant) {
                AuthScreen(state = AuthPreviewData.all.getValue("welcome"), onEvent = {})
                DemoDialog(onOpen = {}, onDismiss = {})
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, name))
    }

    private companion object {
        const val FONT_SCALE_200 = 2f
    }
}
