package dev.kyralabs.pingogram.feature.media.storyalbum

import com.github.takahirom.roborazzi.captureRoboImage
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.media.ui.COMPACT
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of picking stories for a new album (`docs/TESTING.md`, owner decision 2026-11-20), two picked, in light and
 * dark and at 200% font scale. Brand colors; pictures show their placeholders.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class StoryAlbumPickerScreenshotTest {
    @Test
    fun pickingLight() = capture()

    @Test
    fun pickingDark() = capture(ThemeMode.Dark)

    @Test
    @Config(fontScale = 2f)
    fun pickingFontScale200() = capture()

    private fun capture(themeMode: ThemeMode = ThemeMode.Light) =
        captureRoboImage {
            PingogramTheme(themeMode = themeMode, dynamicColor = false) {
                StoryAlbumPickerScreen(StoryAlbumPreviewData.picking, onEvent = {}, onClose = {})
            }
        }
}
