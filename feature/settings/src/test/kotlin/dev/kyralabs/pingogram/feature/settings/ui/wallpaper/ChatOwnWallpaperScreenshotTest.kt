package dev.kyralabs.pingogram.feature.settings.ui.wallpaper

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.domain.model.ChatWallpaperOverride
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.domain.model.Wallpaper
import dev.kyralabs.pingogram.feature.settings.ui.COMPACT
import dev.kyralabs.pingogram.feature.settings.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.settings.ui.captureScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of a chat's own wallpaper page: with Telegram Premium, the photo picker and "Only for me" / "For both";
 * with a colors wallpaper of the chat's own in dark theme, dimmed 30% by its slider, and Reset.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ChatOwnWallpaperScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun premiumLight() = capture("premiumLight", ThemeMode.Light)

    @Test
    fun premiumDark() = capture("premiumDark", ThemeMode.Dark)

    @Test
    fun ownDimmedDark() =
        capture(
            "ownDimmedDark",
            ThemeMode.Dark,
            ChatOwnWallpaperUiState(own = ChatWallpaperOverride(FILL, darkThemeDimming = 30), dimming = 30),
        )

    private fun capture(
        name: String,
        themeMode: ThemeMode,
        state: ChatOwnWallpaperUiState = ChatOwnWallpaperUiState(canSetForBoth = true),
    ) = composeRule.captureScreen(javaClass, name, themeMode) {
        ChatOwnWallpaperScreen(
            state = state,
            actions = WallpaperActions(),
            onForBoth = {},
            onReset = {},
            onBack = {},
        )
    }

    private companion object {
        val FILL = Wallpaper.Fill(3, listOf(0xF4B183, 0x8FAADC), rotationDegrees = 45)
    }
}
