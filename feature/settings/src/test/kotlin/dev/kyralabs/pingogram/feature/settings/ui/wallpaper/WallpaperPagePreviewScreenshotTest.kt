package dev.kyralabs.pingogram.feature.settings.ui.wallpaper

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.domain.model.ChatWallpaper
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.domain.model.Wallpaper
import dev.kyralabs.pingogram.core.domain.model.WallpaperChoice
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.feature.settings.ui.COMPACT
import dev.kyralabs.pingogram.feature.settings.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.settings.ui.captureScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of the wallpaper page's preview: a chat's, with its name, on a fill of its own dimmed in dark theme; and the
 * account's on a theme-color wallpaper picked over the one set. The preview is one description, no control.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class WallpaperPagePreviewScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun chatLight() = capture("chatLight", ThemeMode.Light, CHAT)

    @Test
    fun chatDimmedDark() = capture("chatDimmedDark", ThemeMode.Dark, CHAT)

    @Test
    fun accountThemeColorDark() =
        capture(
            "accountThemeColorDark",
            ThemeMode.Dark,
            WallpaperPageUiState(
                isChat = false,
                saved = SavedWallpaper(themeColor = ChatWallpaper.Secondary),
                draft = WallpaperDraft(themeColor = ChatWallpaper.PrimaryToTertiary),
            ),
        )

    private fun capture(
        name: String,
        themeMode: ThemeMode,
        state: WallpaperPageUiState,
    ) {
        composeRule.captureScreen(javaClass, name, themeMode) {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                WallpaperPagePreview(state)
            }
        }
        composeRule.assertAccessible()
    }

    private companion object {
        val FILL = Wallpaper.Fill(3, listOf(0xF4B183, 0x8FAADC), rotationDegrees = 45)
        val CHAT =
            WallpaperPageUiState(
                isChat = true,
                chat = WallpaperPageChat(5, "Meera Krishnan", null),
                picker = WallpaperPickerState(pending = WallpaperChoice.Existing(FILL)),
                saved = SavedWallpaper(dimming = 40),
            )
    }
}
