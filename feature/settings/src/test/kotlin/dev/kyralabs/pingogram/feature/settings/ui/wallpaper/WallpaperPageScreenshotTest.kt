package dev.kyralabs.pingogram.feature.settings.ui.wallpaper

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.domain.model.ChatWallpaper
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.domain.model.Wallpaper
import dev.kyralabs.pingogram.core.domain.model.WallpaperChoice
import dev.kyralabs.pingogram.core.ui.theme.fillSeed
import dev.kyralabs.pingogram.feature.settings.ui.COMPACT
import dev.kyralabs.pingogram.feature.settings.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.settings.ui.FONT_SCALE_200
import dev.kyralabs.pingogram.feature.settings.ui.MEDIUM
import dev.kyralabs.pingogram.feature.settings.ui.ScreenshotVariant
import dev.kyralabs.pingogram.feature.settings.ui.captureScreen
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of the wallpaper page: the account's in light and dark theme (dark with its dimming), a chat's with a pick
 * waiting for Apply, the whole page in the pick's colors, while the wallpapers load and when they cannot, and the
 * audit's looks (AMOLED, high contrast, right to left, a medium window, twice the font size).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class WallpaperPageScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun accountLight() = capture("accountLight", ThemeMode.Light, account(dark = false))

    @Test
    fun accountDark() = capture("accountDark", ThemeMode.Dark, account(dark = true))

    @Test
    fun chatPickedDark() = capture("chatPickedDark", ThemeMode.Dark, ChatPicked)

    @Test
    fun loadingLight() = capture("loadingLight", ThemeMode.Light, WallpaperPageUiState(isChat = true, chat = Chat))

    @Test
    fun failedLight() =
        capture(
            "failedLight",
            ThemeMode.Light,
            WallpaperPageUiState(
                isChat = true,
                chat = Chat,
                picker = WallpaperPickerState(installed = InstalledWallpapers.Failed),
            ),
        )

    @Test
    fun amoled() = captureAudit(ScreenshotVariant.Amoled)

    @Test
    fun highContrast() = captureAudit(ScreenshotVariant.HighContrast)

    @Test
    fun rtl() = captureAudit(ScreenshotVariant.Rtl)

    @Test
    @Config(qualifiers = MEDIUM)
    fun medium() = captureAudit(ScreenshotVariant.Medium)

    @Test
    @Config(qualifiers = "w411dp-h1600dp-xxhdpi", fontScale = FONT_SCALE_200)
    fun fontScale200() = capture("fontScale200", ThemeMode.Light, ChatPicked)

    private fun capture(
        name: String,
        themeMode: ThemeMode,
        state: WallpaperPageUiState,
    ) = composeRule.captureScreen(javaClass, name, themeMode) { Screen(state) }

    private fun captureAudit(variant: ScreenshotVariant) =
        composeRule.captureScreen(javaClass, variant.goldenName, variant) { Screen(ChatPicked) }

    @Composable
    private fun Screen(state: WallpaperPageUiState) {
        WallpaperPageScreen(
            state = state,
            onEvent = {},
            onPickPhoto = {},
            onApply = {},
            onBack = {},
            colorsDispatcher = Dispatchers.Unconfined,
        )
    }

    private companion object {
        val Chat = WallpaperPageChat(5, "Meera Krishnan", null)
        val FILL = Wallpaper.Fill(3, persistentListOf(0xF4B183, 0x8FAADC), rotationDegrees = 45)
        val Installed = InstalledWallpapers.Loaded(persistentListOf(FILL))
        val ChatPicked =
            WallpaperPageUiState(
                isChat = true,
                chat = Chat,
                picker =
                    WallpaperPickerState(
                        forDarkTheme = true,
                        installed = Installed,
                        pending = WallpaperChoice.Existing(FILL),
                    ),
                seed = fillSeed(FILL.colors.map { it or (0xFF shl 24) }),
            )

        fun account(dark: Boolean) =
            WallpaperPageUiState(
                isChat = false,
                picker = WallpaperPickerState(forDarkTheme = dark, installed = Installed),
                saved = SavedWallpaper(themeColor = ChatWallpaper.PrimaryToTertiary),
            )
    }
}
