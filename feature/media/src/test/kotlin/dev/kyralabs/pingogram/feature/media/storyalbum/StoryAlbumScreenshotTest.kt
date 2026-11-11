package dev.kyralabs.pingogram.feature.media.storyalbum

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.github.takahirom.roborazzi.captureRoboImage
import dev.kyralabs.pingogram.core.domain.model.Contrast
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.media.ui.COMPACT
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of story albums (`docs/TESTING.md`): a profile's albums section and an opened album in light, dark,
 * AMOLED, high contrast, right to left, 200% font scale and a 700 dp window; then the album's next page failing, an
 * empty album and a failure (the next page loading is left out: its indicator morphs). Brand colors; pictures show
 * their placeholders.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class StoryAlbumScreenshotTest {
    @Test
    fun albumsLight() = albums()

    @Test
    fun albumsDark() = albums(ThemeMode.Dark)

    @Test
    fun albumsRtl() = albums(layoutDirection = LayoutDirection.Rtl)

    @Test
    @Config(fontScale = 2f)
    fun albumsFontScale200() = albums()

    @Test
    fun albumLight() = album(StoryAlbumPreviewData.album)

    @Test
    fun albumDark() = album(StoryAlbumPreviewData.album, ThemeMode.Dark)

    @Test
    fun albumAmoled() = album(StoryAlbumPreviewData.album, ThemeMode.Dark, amoled = true)

    @Test
    fun albumHighContrast() = album(StoryAlbumPreviewData.album, contrast = Contrast.High)

    @Test
    fun albumRtl() = album(StoryAlbumPreviewData.album, layoutDirection = LayoutDirection.Rtl)

    @Test
    @Config(fontScale = 2f)
    fun albumFontScale200() = album(StoryAlbumPreviewData.album)

    @Test
    @Config(qualifiers = "w700dp-h891dp-xxhdpi")
    fun albumMedium() = album(StoryAlbumPreviewData.album)

    @Test
    fun moreFailedLight() = album(StoryAlbumPreviewData.moreFailed)

    @Test
    fun emptyLight() = album(StoryAlbumPreviewData.empty)

    @Test
    fun errorLight() = album(StoryAlbumPreviewData.error)

    private fun albums(
        themeMode: ThemeMode = ThemeMode.Light,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    ) = capture(themeMode, layoutDirection = layoutDirection) {
        Surface {
            StoryAlbumsContent(StoryAlbumPreviewData.albums, {}, Modifier.padding(MaterialTheme.spacing.medium))
        }
    }

    private fun album(
        state: StoryAlbumUiState,
        themeMode: ThemeMode = ThemeMode.Light,
        amoled: Boolean = false,
        contrast: Contrast = Contrast.Standard,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    ) = capture(themeMode, amoled, contrast, layoutDirection) {
        StoryAlbumScreen(state, onEvent = {}, onBack = {}, onOpenStory = {})
    }

    private fun capture(
        themeMode: ThemeMode,
        amoled: Boolean = false,
        contrast: Contrast = Contrast.Standard,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
        content: @Composable () -> Unit,
    ) = captureRoboImage {
        CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
            PingogramTheme(
                themeMode = themeMode,
                dynamicColor = false,
                amoled = amoled,
                contrast = contrast,
                content = content,
            )
        }
    }
}
