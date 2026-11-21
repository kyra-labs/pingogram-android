package dev.kyralabs.pingogram.feature.settings.ui.wallpaper

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.kyralabs.pingogram.core.domain.model.ChatWallpaperOverride
import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.Wallpaper
import dev.kyralabs.pingogram.core.domain.model.WallpaperImage
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.COMPACT
import dev.kyralabs.pingogram.feature.settings.ui.ComponentActivityRegistration
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** A chat's own wallpaper page: "for both" only with Telegram Premium, and Reset where the chat has one. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ChatOwnWallpaperScreenTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<String>()

    private fun setScreen(state: ChatOwnWallpaperUiState) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                ChatOwnWallpaperScreen(
                    state = state,
                    actions = WallpaperActions(),
                    onForBoth = { events += "both $it" },
                    onReset = { events += "reset" },
                    onBack = {},
                )
            }
        }
    }

    private fun string(id: Int) = RuntimeEnvironment.getApplication().getString(id)

    @Test
    fun `with Telegram Premium it can be set for both people`() {
        setScreen(ChatOwnWallpaperUiState(canSetForBoth = true))

        composeRule.onNodeWithText(string(R.string.settings_chat_wallpaper_for_both)).performScrollTo().performClick()

        assertEquals(listOf("both true"), events)
    }

    @Test
    fun `without Telegram Premium it is only for the user`() {
        setScreen(ChatOwnWallpaperUiState(canSetForBoth = false))

        composeRule.onNodeWithText(string(R.string.settings_chat_wallpaper_for_both)).assertDoesNotExist()
    }

    @Test
    fun `the chat's own wallpaper can be reset`() {
        setScreen(ChatOwnWallpaperUiState(own = ChatWallpaperOverride(PHOTO, 0)))

        composeRule.onNodeWithText(string(R.string.settings_chat_wallpaper_reset)).performScrollTo().performClick()

        assertEquals(listOf("reset"), events)
    }

    private companion object {
        val PHOTO = Wallpaper.Photo(1, WallpaperImage(FileRef(10, 0)), isBlurred = false)
    }
}
