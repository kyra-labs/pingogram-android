package dev.kyralabs.pingogram.feature.media.profilemusic

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
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
 * Goldens of profile music (`docs/TESTING.md`, boards 13a, 13b): the card on a profile in light, dark, AMOLED, high
 * contrast, right to left, 200% font scale and a 700 dp window, the user's own card without songs and a song that
 * cannot be played; the playlist of the user's own profile in light and dark, another user's, and a refused change.
 * Brand colors; covers show their placeholders.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ProfileMusicScreenshotTest {
    @Test
    fun cardLight() = card(ProfileMusicPreviewData.other)

    @Test
    fun cardDark() = card(ProfileMusicPreviewData.other, ThemeMode.Dark)

    @Test
    fun cardAmoled() = card(ProfileMusicPreviewData.other, ThemeMode.Dark, amoled = true)

    @Test
    fun cardHighContrast() = card(ProfileMusicPreviewData.other, contrast = Contrast.High)

    @Test
    fun cardRtl() = card(ProfileMusicPreviewData.other, layoutDirection = LayoutDirection.Rtl)

    @Test
    @Config(fontScale = 2f)
    fun cardFontScale200() = card(ProfileMusicPreviewData.other)

    @Test
    @Config(qualifiers = "w700dp-h891dp-xxhdpi")
    fun cardMedium() = card(ProfileMusicPreviewData.other)

    @Test
    fun ownEmptyLight() = card(ProfileMusicPreviewData.ownEmpty)

    @Test
    fun cannotPlayLight() = card(ProfileMusicPreviewData.cannotPlay)

    @Test
    fun playlistLight() = sheet(ProfileMusicPreviewData.own)

    @Test
    fun playlistDark() = sheet(ProfileMusicPreviewData.own, ThemeMode.Dark)

    @Test
    @Config(fontScale = 2f)
    fun playlistFontScale200() = sheet(ProfileMusicPreviewData.own)

    @Test
    fun playlistOtherLight() = sheet(ProfileMusicPreviewData.other)

    @Test
    fun refusedLight() = sheet(ProfileMusicPreviewData.refused)

    private fun card(
        content: ProfileMusicUiState.Content,
        themeMode: ThemeMode = ThemeMode.Light,
        amoled: Boolean = false,
        contrast: Contrast = Contrast.Standard,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    ) = capture(themeMode, amoled, contrast, layoutDirection) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            val progress = remember { mutableFloatStateOf(PREVIEW_PROGRESS) }
            ProfileMusicContent(content, progress, {}, {}, Modifier.padding(MaterialTheme.spacing.medium))
        }
    }

    private fun sheet(
        content: ProfileMusicUiState.Content,
        themeMode: ThemeMode = ThemeMode.Light,
    ) = capture(themeMode) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            ProfileMusicSheetContent(content, pickerMissing = false, onEvent = {}, onAdd = {})
        }
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
