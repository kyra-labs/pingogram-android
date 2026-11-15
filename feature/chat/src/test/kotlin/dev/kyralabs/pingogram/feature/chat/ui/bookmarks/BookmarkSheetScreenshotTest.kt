package dev.kyralabs.pingogram.feature.chat.ui.bookmarks

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.feature.chat.ui.COMPACT
import dev.kyralabs.pingogram.feature.chat.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chat.ui.ScreenshotTheme
import dev.kyralabs.pingogram.feature.chat.ui.ScreenshotVariant
import dev.kyralabs.pingogram.feature.chat.ui.goldenPath
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of the bookmark sheet's content (the owner's board 34a): a new bookmark light and dark, one bookmarked
 * already, AMOLED, high contrast, right to left and 200% font. Brand colors.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class BookmarkSheetScreenshotTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun light() = capture("light", ScreenshotVariant.Light)

    @Test
    fun dark() = capture("dark", ScreenshotVariant.Dark)

    @Test
    fun bookmarked() = capture("bookmarked", ScreenshotVariant.Light, BookmarkSheetPreviewData.bookmarked)

    @Test
    fun amoled() = capture("amoled", ScreenshotVariant.Amoled)

    @Test
    fun highContrast() = capture("highContrast", ScreenshotVariant.HighContrast)

    @Test
    fun rtl() = capture("rtl", ScreenshotVariant.Rtl)

    @Test
    @Config(qualifiers = "w411dp-h1400dp-xxhdpi", fontScale = FONT_SCALE_200)
    fun fontScale200() = capture("fontScale200", ScreenshotVariant.Light)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        name: String,
        variant: ScreenshotVariant,
        state: BookmarkSheetUiState = BookmarkSheetPreviewData.new,
    ) {
        composeRule.setContent {
            ScreenshotTheme(variant) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    BookmarkSheetContent(state, onEvent = {})
                }
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, name))
    }

    private companion object {
        const val FONT_SCALE_200 = 2f
    }
}
