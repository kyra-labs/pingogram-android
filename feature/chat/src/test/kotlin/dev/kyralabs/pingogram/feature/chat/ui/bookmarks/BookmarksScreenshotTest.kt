package dev.kyralabs.pingogram.feature.chat.ui.bookmarks

import androidx.activity.ComponentActivity
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
 * Goldens of the Bookmarks page (the owner's boards 34b and 34d): all bookmarks light and dark, one tag's, empty,
 * AMOLED, high contrast, right to left, 200% font and a 700 dp width. Brand colors.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class BookmarksScreenshotTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun light() = capture("light", ScreenshotVariant.Light)

    @Test
    fun dark() = capture("dark", ScreenshotVariant.Dark)

    @Test
    fun filtered() = capture("filtered", ScreenshotVariant.Light, BookmarksPreviewData.filtered)

    @Test
    fun searching() = capture("searching", ScreenshotVariant.Light, BookmarksPreviewData.searching)

    @Test
    fun noMatches() = capture("noMatches", ScreenshotVariant.Light, BookmarksPreviewData.noMatches)

    @Test
    fun searchingRtl() = capture("searchingRtl", ScreenshotVariant.Rtl, BookmarksPreviewData.searching)

    @Test
    fun empty() = capture("empty", ScreenshotVariant.Light, BookmarksUiState.Empty)

    @Test
    fun amoled() = capture("amoled", ScreenshotVariant.Amoled)

    @Test
    fun highContrast() = capture("highContrast", ScreenshotVariant.HighContrast)

    @Test
    fun rtl() = capture("rtl", ScreenshotVariant.Rtl)

    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun fontScale200() = capture("fontScale200", ScreenshotVariant.Light)

    @Test
    @Config(qualifiers = "w700dp-h891dp-xxhdpi")
    fun width700() = capture("width700", ScreenshotVariant.Light)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        name: String,
        variant: ScreenshotVariant,
        state: BookmarksUiState = BookmarksPreviewData.content,
    ) {
        composeRule.setContent {
            ScreenshotTheme(variant) { BookmarksScreen(state, onEvent = {}, onBack = {}) }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, name))
    }

    private companion object {
        const val FONT_SCALE_200 = 2f
    }
}
