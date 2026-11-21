package dev.kyralabs.pingogram.feature.settings.ui.wallpaper

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.Wallpaper
import dev.kyralabs.pingogram.core.domain.model.WallpaperChoice
import dev.kyralabs.pingogram.core.domain.model.WallpaperImage
import dev.kyralabs.pingogram.core.testing.assertAccessible
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

/**
 * The wallpaper page's controls: the title names the chat, Apply waits for a change (for a chat it asks whom for, both
 * people only with Telegram Premium), "Reset to default", the theme, the dimming in dark theme, Blur for a photo, and
 * "Choose a photo" with the note that a photo is uploaded.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class WallpaperPageScreenTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<Any>()

    private fun setScreen(state: WallpaperPageUiState) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                WallpaperPageScreen(
                    state = state,
                    onEvent = { events += it },
                    onPickPhoto = { events += "photos" },
                    onApply = { events += "apply $it" },
                    onBack = {},
                )
            }
        }
    }

    private fun string(
        id: Int,
        vararg args: Any,
    ) = RuntimeEnvironment.getApplication().getString(id, *args)

    @Test
    fun `a chat's page is named after it, and Apply waits for a change`() {
        setScreen(WallpaperPageUiState(isChat = true, chat = WallpaperPageChat(5, "Meera", null)))

        composeRule.onNodeWithText(string(R.string.settings_wallpaper_title_chat, "Meera")).assertExists()
        composeRule.onNodeWithText(string(R.string.settings_chat_wallpaper_apply)).assertIsNotEnabled()
        composeRule.onNodeWithText(string(R.string.settings_wallpaper_reset)).assertIsNotEnabled()
        composeRule.assertAccessible()
    }

    @Test
    fun `the account's Apply applies at once, and Reset to default previews the default`() {
        setScreen(
            WallpaperPageUiState(
                isChat = false,
                picker = WallpaperPickerState(pending = WallpaperChoice.Existing(PATTERN)),
            ),
        )

        composeRule.onNodeWithText(string(R.string.settings_chat_wallpaper_apply)).assertIsEnabled().performClick()
        composeRule.onNodeWithText(string(R.string.settings_wallpaper_reset)).performScrollTo().performClick()

        assertEquals(listOf("apply false", WallpaperPageEvent.Reset), events)
    }

    @Test
    fun `a chat's Apply asks whom for, and without Telegram Premium both is locked`() {
        setScreen(ChatPicked)

        composeRule.onNodeWithText(string(R.string.settings_chat_wallpaper_apply)).performClick()
        composeRule.onNodeWithText(string(R.string.settings_wallpaper_requires_premium)).assertExists()
        composeRule.onNodeWithText(string(R.string.settings_wallpaper_apply_for_both, "Meera")).assertIsNotEnabled()
        composeRule.assertAccessible()
        composeRule.onNodeWithText(string(R.string.settings_wallpaper_apply_for_me)).performClick()

        assertEquals(listOf("apply false"), events)
        composeRule.onNodeWithText(string(R.string.settings_wallpaper_apply_for_me)).assertDoesNotExist()
    }

    @Test
    fun `with Telegram Premium a chat's wallpaper can be applied for both people`() {
        setScreen(ChatPicked.copy(canSetForBoth = true))

        composeRule.onNodeWithText(string(R.string.settings_chat_wallpaper_apply)).performClick()
        composeRule.onNodeWithText(string(R.string.settings_wallpaper_requires_premium)).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.settings_wallpaper_apply_for_both, "Meera")).performClick()

        assertEquals(listOf("apply true"), events)
    }

    @Test
    fun `the account's page picks the theme, and dims in dark theme`() {
        setScreen(
            WallpaperPageUiState(
                isChat = false,
                picker = WallpaperPickerState(forDarkTheme = true),
                saved = SavedWallpaper(dimming = 30),
            ),
        )

        composeRule.onNodeWithText(string(R.string.settings_chat_wallpaper_light)).performScrollTo().performClick()
        composeRule
            .onNodeWithContentDescription(string(R.string.settings_chat_wallpaper_dimming))
            .performScrollTo()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "30%"))
            .performSemanticsAction(SemanticsActions.SetProgress) { it(LAST_DIMMING_STEP) }

        assertEquals(
            listOf(
                WallpaperPageEvent.Picker(WallpaperEvent.ShowTheme(dark = false)),
                WallpaperPageEvent.SetDimming(100),
            ),
            events,
        )
        composeRule.assertAccessible()
    }

    @Test
    fun `light theme has no dimming, and a chat's page no theme`() {
        setScreen(WallpaperPageUiState(isChat = true, picker = WallpaperPickerState(forDarkTheme = false)))

        composeRule.onNodeWithContentDescription(string(R.string.settings_chat_wallpaper_dimming)).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.settings_chat_wallpaper_light)).assertDoesNotExist()
    }

    @Test
    fun `the photo set can be blurred`() {
        setScreen(WallpaperPageUiState(isChat = true, saved = SavedWallpaper(wallpaper = PHOTO)))

        composeRule.onNodeWithText(string(R.string.settings_chat_wallpaper_blur)).performScrollTo().performClick()

        assertEquals(listOf(WallpaperPageEvent.Picker(WallpaperEvent.SetBlurred(true))), events)
    }

    @Test
    fun `a photo from the phone opens the picker and says it is uploaded`() {
        setScreen(
            WallpaperPageUiState(
                isChat = false,
                picker = WallpaperPickerState(pending = WallpaperChoice.NewPhoto("/photo.jpg")),
            ),
        )

        composeRule.onNodeWithText(string(R.string.settings_wallpaper_choose_photo)).performScrollTo().performClick()
        composeRule.onNodeWithText(string(R.string.settings_chat_wallpaper_upload_note)).performScrollTo()

        assertEquals(listOf("photos"), events)
        composeRule.assertAccessible()
    }

    private companion object {
        const val LAST_DIMMING_STEP = 10f
        val ChatPicked
            get() =
                WallpaperPageUiState(
                    isChat = true,
                    chat = WallpaperPageChat(5, "Meera", null),
                    picker = WallpaperPickerState(pending = WallpaperChoice.Existing(PATTERN)),
                )
        val PHOTO = Wallpaper.Photo(1, WallpaperImage(FileRef(10, 0)), isBlurred = false)
        val PATTERN = Wallpaper.Pattern(2, WallpaperImage(FileRef(11, 0)), intensity = 50, isInverted = false)
    }
}
