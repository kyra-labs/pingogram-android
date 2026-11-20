package dev.kyralabs.pingogram.feature.media.storyalbum

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.github.takahirom.roborazzi.captureRoboImage
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.Contrast
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.media.ui.COMPACT
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of a profile's Stories tab (`docs/TESTING.md`, owner decision 2026-11-20): the album covers with their
 * counts over the stories in light, dark, high contrast, right to left and at 200% font scale; then "No stories yet"
 * and a failure (loading is left out: its indicator morphs). Brand colors; pictures show their placeholders.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ProfileStoriesScreenshotTest {
    @Test
    fun storiesLight() = capture(StoryAlbumPreviewData.profileStories)

    @Test
    fun storiesDark() = capture(StoryAlbumPreviewData.profileStories, ThemeMode.Dark)

    @Test
    fun storiesHighContrast() = capture(StoryAlbumPreviewData.profileStories, contrast = Contrast.High)

    @Test
    fun storiesRtl() = capture(StoryAlbumPreviewData.profileStories, layoutDirection = LayoutDirection.Rtl)

    @Test
    @Config(fontScale = 2f)
    fun storiesFontScale200() = capture(StoryAlbumPreviewData.profileStories)

    @Test
    fun emptyLight() = capture(ProfileStoriesUiState.Empty)

    @Test
    fun errorLight() = capture(ProfileStoriesUiState.Error(DomainError.Network))

    private fun capture(
        state: ProfileStoriesUiState,
        themeMode: ThemeMode = ThemeMode.Light,
        contrast: Contrast = Contrast.Standard,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    ) = captureRoboImage {
        CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
            PingogramTheme(
                themeMode = themeMode,
                dynamicColor = false,
                contrast = contrast,
            ) { ProfileStoriesGrid(state) }
        }
    }
}
