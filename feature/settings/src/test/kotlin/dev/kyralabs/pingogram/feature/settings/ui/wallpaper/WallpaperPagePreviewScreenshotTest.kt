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
import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.domain.model.Wallpaper
import dev.kyralabs.pingogram.core.domain.model.WallpaperChoice
import dev.kyralabs.pingogram.core.domain.model.WallpaperImage
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.core.ui.theme.fillSeed
import dev.kyralabs.pingogram.feature.settings.ui.COMPACT
import dev.kyralabs.pingogram.feature.settings.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.settings.ui.FONT_SCALE_200
import dev.kyralabs.pingogram.feature.settings.ui.captureScreen
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of the wallpaper page's preview: a chat's, with its name, on a fill of its own (dimmed in dark theme), a
 * photo and a pattern, in the wallpaper's colors (a pattern keeps the theme's); and the account's on a theme-color
 * wallpaper picked over the one set. The photo itself does not load here, so its theme-color fallback shows under the
 * photo's colors. The preview is one description, no control.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class WallpaperPagePreviewScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun fillLight() = capture("fillLight", ThemeMode.Light, CHAT)

    @Test
    fun fillDimmedDark() = capture("fillDimmedDark", ThemeMode.Dark, CHAT)

    @Test
    fun photoLight() = capture("photoLight", ThemeMode.Light, CHAT_PHOTO)

    @Test
    fun photoDark() = capture("photoDark", ThemeMode.Dark, CHAT_PHOTO)

    @Test
    fun patternLight() = capture("patternLight", ThemeMode.Light, CHAT_PATTERN)

    @Test
    fun patternDark() = capture("patternDark", ThemeMode.Dark, CHAT_PATTERN)

    @Test
    @Config(qualifiers = "w411dp-h1400dp-xxhdpi", fontScale = FONT_SCALE_200)
    fun fontScale200() = capture("fontScale200", ThemeMode.Light, CHAT)

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
                WallpaperPagePreview(state, colorsDispatcher = Dispatchers.Unconfined)
            }
        }
        composeRule.assertAccessible()
    }

    private companion object {
        val FILL = Wallpaper.Fill(3, listOf(0xF4B183, 0x8FAADC), rotationDegrees = 45)
        val PHOTO = Wallpaper.Photo(1, WallpaperImage(FileRef(10, 0)), isBlurred = false)
        val PATTERN = Wallpaper.Pattern(2, WallpaperImage(FileRef(11, 0)), intensity = 50, isInverted = false)
        val CHAT =
            WallpaperPageUiState(
                isChat = true,
                chat = WallpaperPageChat(5, "Meera Krishnan", null),
                picker = WallpaperPickerState(pending = WallpaperChoice.Existing(FILL)),
                saved = SavedWallpaper(dimming = 40),
                seed = fillSeed(FILL.colors.map { it or (0xFF shl 24) }),
            )

        /** A photo whose seed was found: a deep teal. */
        val CHAT_PHOTO = CHAT.copy(picker = picked(PHOTO), seed = 0xFF1F6F6B.toInt())
        val CHAT_PATTERN = CHAT.copy(picker = picked(PATTERN), seed = null)

        fun picked(wallpaper: Wallpaper) = WallpaperPickerState(pending = WallpaperChoice.Existing(wallpaper))
    }
}
