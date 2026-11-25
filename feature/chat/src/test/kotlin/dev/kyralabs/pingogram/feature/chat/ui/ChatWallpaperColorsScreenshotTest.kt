package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.core.domain.model.AccountWallpapers
import dev.kyralabs.pingogram.core.domain.model.Wallpaper
import dev.kyralabs.pingogram.core.ui.theme.WallpaperColorsTheme
import dev.kyralabs.pingogram.core.ui.theme.isDarkTheme
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.wallpaper.ConversationWallpaper
import dev.kyralabs.pingogram.feature.chat.ui.wallpaper.LocalConversationWallpaper
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of a group conversation in its wallpaper's colors (`docs/adr/0041-chat-colors-from-the-wallpaper.md`): a
 * warm fill whose seed recolors the bubbles, links, date chips, composer and buttons, in light, dark, AMOLED black,
 * high contrast, right to left and at 200% font, drawn over the wallpaper: blurred behind the top bar, around the
 * rounded message list and the floating composer.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ChatWallpaperColorsScreenshotTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun light() = capture(ScreenshotVariant.Light)

    @Test
    fun dark() = capture(ScreenshotVariant.Dark)

    @Test
    fun amoled() = capture(ScreenshotVariant.Amoled)

    @Test
    fun highContrast() = capture(ScreenshotVariant.HighContrast)

    @Test
    fun rtl() = capture(ScreenshotVariant.Rtl)

    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun fontScale200() = capture(ScreenshotVariant.Light, "fontScale200")

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        variant: ScreenshotVariant,
        name: String = variant.goldenName,
    ) {
        composeRule.setContent {
            ScreenshotTheme(variant) {
                val wallpaper = ConversationWallpaper(account = AccountWallpapers(light = WARM_FILL, dark = WARM_FILL))
                CompositionLocalProvider(LocalConversationWallpaper provides wallpaper) {
                    WallpaperColorsTheme(wallpaper.forTheme(isDarkTheme()).seed, dispatcher = Dispatchers.Unconfined) {
                        ChatScreen(state = ChatPreviewData.groupChat, onEvent = {}, onBack = {})
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(SETTLED_MILLIS)
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, "warmFill.$name"))
    }

    private companion object {
        val WARM_FILL = Wallpaper.Fill(1, persistentListOf(0xB4744B, 0xE8C9A0), rotationDegrees = 45)
        const val SETTLED_MILLIS = 2_000L
    }
}
