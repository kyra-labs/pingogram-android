package dev.kyralabs.pingogram.feature.chat.ui.saved

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.feature.chat.ui.COMPACT
import dev.kyralabs.pingogram.feature.chat.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chat.ui.ScreenshotTheme
import dev.kyralabs.pingogram.feature.chat.ui.ScreenshotVariant
import dev.kyralabs.pingogram.feature.chat.ui.goldenPath
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Saved Messages' Bookmarks card (the owner's board 34e): light, dark, right to left and 200% font; one button. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h160dp-xxhdpi")
class SavedBookmarksCardScreenshotTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun light() = capture("light", ScreenshotVariant.Light)

    @Test
    fun dark() = capture("dark", ScreenshotVariant.Dark)

    @Test
    fun rtl() = capture("rtl", ScreenshotVariant.Rtl)

    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun fontScale200() = capture("fontScale200", ScreenshotVariant.Light)

    @Test
    @Config(qualifiers = COMPACT)
    fun `the card is one accessible button that opens the page`() {
        var opened = 0
        composeRule.setContent {
            ScreenshotTheme(ScreenshotVariant.Light) { SavedBookmarksCard(count = 27, onOpen = { opened++ }) }
        }
        composeRule.onNodeWithText("Bookmarks · 27").performClick()
        assertEquals(1, opened)
        composeRule.assertAccessible()
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        name: String,
        variant: ScreenshotVariant,
    ) {
        composeRule.setContent {
            ScreenshotTheme(variant) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer) { SavedBookmarksCard(27, onOpen = {}) }
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, name))
    }

    private companion object {
        const val FONT_SCALE_200 = 2f
    }
}
