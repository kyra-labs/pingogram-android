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
 * Goldens of the bookmarks' tag editor (the owner's board 34c): the tags light and dark, one renamed with its colors,
 * a new one, the question before deleting, none yet, AMOLED, high contrast, right to left and 200% font.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class BookmarkTagsScreenshotTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun light() = capture("light", ScreenshotVariant.Light)

    @Test
    fun dark() = capture("dark", ScreenshotVariant.Dark)

    @Test
    fun renaming() = capture("renaming", ScreenshotVariant.Light, BookmarkTagsPreviewData.renaming)

    @Test
    fun adding() = capture("adding", ScreenshotVariant.Dark, BookmarkTagsPreviewData.adding)

    @Test
    fun deleting() = capture("deleting", ScreenshotVariant.Light, BookmarkTagsPreviewData.deleting)

    @Test
    fun empty() = capture("empty", ScreenshotVariant.Light, BookmarkTagsPreviewData.empty)

    @Test
    fun amoled() = capture("amoled", ScreenshotVariant.Amoled)

    @Test
    fun highContrast() = capture("highContrast", ScreenshotVariant.HighContrast)

    @Test
    fun rtl() = capture("rtl", ScreenshotVariant.Rtl, BookmarkTagsPreviewData.renaming)

    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun fontScale200() = capture("fontScale200", ScreenshotVariant.Light, BookmarkTagsPreviewData.renaming)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        name: String,
        variant: ScreenshotVariant,
        state: BookmarkTagsUiState = BookmarkTagsPreviewData.content,
    ) {
        composeRule.setContent {
            ScreenshotTheme(variant) { BookmarkTagsScreen(state, onEvent = {}, onBack = {}) }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, name))
    }

    private companion object {
        const val FONT_SCALE_200 = 2f
    }
}
