package dev.kyralabs.pingogram.feature.settings.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import dev.kyralabs.pingogram.core.domain.model.AccountWallpapers
import dev.kyralabs.pingogram.core.domain.model.BubbleCorners
import dev.kyralabs.pingogram.core.domain.model.ChatAppearance
import dev.kyralabs.pingogram.core.domain.model.ChatWallpaper
import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.Wallpaper
import dev.kyralabs.pingogram.core.domain.model.WallpaperChoice
import dev.kyralabs.pingogram.core.domain.model.WallpaperImage
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.wallpaper.InstalledWallpapers
import dev.kyralabs.pingogram.feature.settings.ui.wallpaper.WallpaperActions
import dev.kyralabs.pingogram.feature.settings.ui.wallpaper.WallpaperPickerState
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The Chat appearance page's events: "Wallpaper" opens the wallpaper page, and the sliders are named, state their value
 * and move through their steps (`docs/DESIGN_SYSTEM.md` §5, §9).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ChatAppearanceScreenTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val scales = mutableListOf<Float>()
    private val corners = mutableListOf<BubbleCorners>()
    private var wallpaperOpened = 0
    private val performedHaptics = mutableListOf<HapticFeedbackType>()
    private val haptics =
        object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                performedHaptics += hapticFeedbackType
            }
        }

    private fun setScreen(
        appearance: ChatAppearance = ChatAppearance(),
        account: AccountWallpapers = AccountWallpapers(),
    ) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false, chat = appearance) {
                CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                    ChatAppearanceScreen(
                        state = ChatAppearanceUiState.Content(appearance, account),
                        onTextScale = { scales += it },
                        onBubbleCorners = { corners += it },
                        onOpenWallpaper = { wallpaperOpened++ },
                        onBack = {},
                    )
                }
            }
        }
    }

    private fun string(id: Int) = RuntimeEnvironment.getApplication().getString(id)

    @Test
    fun `Wallpaper says what the conversation shows and opens the wallpaper page`() {
        setScreen(ChatAppearance(wallpaper = ChatWallpaper.Secondary))

        composeRule
            .onNodeWithText(string(R.string.settings_chat_wallpaper_secondary))
            .performScrollTo()
        composeRule.onNodeWithText(string(R.string.settings_wallpaper_title)).performScrollTo().performClick()

        assertEquals(1, wallpaperOpened)
    }

    @Test
    fun `with a Telegram wallpaper of the account's, Wallpaper says which kind`() {
        setScreen(account = AccountWallpapers(light = PHOTO, dark = PHOTO))

        composeRule.onNodeWithText(string(R.string.settings_wallpaper_summary_photo)).performScrollTo()
    }

    @Test
    fun `the text size slider states its percentage and saves the step it is set to`() {
        setScreen(ChatAppearance(textScale = 1.1f))

        val slider = composeRule.onNodeWithContentDescription(string(R.string.settings_chat_text_size))
        slider.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "110%"))
        // The last step: 130%.
        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(LAST_TEXT_STEP) }

        assertEquals(listOf(ChatAppearance.MAX_TEXT_SCALE), scales)
        assertEquals(listOf(HapticFeedbackType.SegmentTick), performedHaptics)
    }

    @Test
    fun `the corners slider names the roundness and steps through the shape scale`() {
        setScreen()

        val slider = composeRule.onNodeWithContentDescription(string(R.string.settings_chat_bubble_corners))
        slider.assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                string(R.string.settings_chat_corners_extra_large),
            ),
        )
        slider.assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo,
                ProgressBarRangeInfo(3f, 0f..4f, steps = 3),
            ),
        )
        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }

        assertEquals(listOf(BubbleCorners.Medium), corners)
    }

    private companion object {
        val PHOTO = Wallpaper.Photo(1, WallpaperImage(FileRef(10, 0)), isBlurred = false)

        /** The index of 130% among 85%, 90%, …, 130%. */
        const val LAST_TEXT_STEP = 9f
    }
}
