package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.core.domain.model.AccountWallpapers
import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.Wallpaper
import dev.kyralabs.pingogram.core.domain.model.WallpaperImage
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.wallpaper.ConversationWallpaper
import dev.kyralabs.pingogram.feature.chat.ui.wallpaper.LocalConversationWallpaper
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of a group conversation over a photo wallpaper while the photo is not drawn yet (it never loads here): the
 * messages' sheet shows its `surface` in the photo's place, never the window's color (`docs/PERFORMANCE.md`,
 * "Overdraw"), with the bars as over any Telegram wallpaper, in light and dark.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ChatWallpaperPhotoScreenshotTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun light() = capture(ScreenshotVariant.Light)

    @Test
    fun dark() = capture(ScreenshotVariant.Dark)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(variant: ScreenshotVariant) {
        composeRule.setContent {
            ScreenshotTheme(variant) {
                val wallpaper = ConversationWallpaper(account = AccountWallpapers(light = PHOTO, dark = PHOTO))
                CompositionLocalProvider(LocalConversationWallpaper provides wallpaper) {
                    ChatScreen(state = ChatPreviewData.groupChat, onEvent = {}, onBack = {})
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(SETTLED_MILLIS)
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, "photoLoading.${variant.goldenName}"))
    }

    private companion object {
        val PHOTO = Wallpaper.Photo(1, WallpaperImage(FileRef(1, 0)), isBlurred = false)
        const val SETTLED_MILLIS = 2_000L
    }
}
