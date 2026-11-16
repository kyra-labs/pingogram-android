package dev.kyralabs.pingogram.feature.chat.ui.interactions

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.feature.chat.ui.COMPACT
import dev.kyralabs.pingogram.feature.chat.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chat.ui.ScreenshotTheme
import dev.kyralabs.pingogram.feature.chat.ui.ScreenshotVariant
import dev.kyralabs.pingogram.feature.chat.ui.goldenPath
import dev.kyralabs.pingogram.feature.chat.ui.preview.InteractionsPreviewData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of the question before removing reactions (`docs/DESIGN_SYSTEM.md` §6 "Seen by and reactions"), captured
 * alone as a dialog over the sheet never settles under Robolectric: all of a sender's recent reactions in every look
 * the audit asks for and at 200% font, and one reaction while Telegram answers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class RemoveReactionsDialogScreenshotTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun light() = capture(ScreenshotVariant.Light)

    @Test
    fun dark() = capture(ScreenshotVariant.Dark)

    @Test
    fun amoled() = capture(ScreenshotVariant.Amoled)

    @Test
    fun highContrast() = capture(ScreenshotVariant.HighContrast)

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
    @Config(fontScale = 2f)
    fun fontScale200() = capture(ScreenshotVariant.Light, name = "fontScale200")

    @Test
    fun removing() = capture(ScreenshotVariant.Light, InteractionsPreviewData.removing, "removing")

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        variant: ScreenshotVariant,
        dialog: ReactionRemovalDialog = InteractionsPreviewData.removeAll,
        name: String = variant.goldenName,
    ) {
        composeRule.setContent { ScreenshotTheme(variant) { RemoveReactionsDialog(dialog, onEvent = {}) } }
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, name))
    }
}
