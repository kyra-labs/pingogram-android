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
 * The Chat appearance page's events: the wallpapers are a radio group whose pick ticks, and the sliders are named,
 * state their value and move through their steps (`docs/DESIGN_SYSTEM.md` §5, §9).
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
    private val wallpapers = mutableListOf<ChatWallpaper>()
    private val performedHaptics = mutableListOf<HapticFeedbackType>()
    private val haptics =
        object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                performedHaptics += hapticFeedbackType
            }
        }

    private val wallpaperEvents = mutableListOf<String>()
    private val wallpaperActions =
        WallpaperActions(
            onTheme = { wallpaperEvents += "theme $it" },
            onPick = { wallpaperEvents += "pick ${it.id}" },
            onPickPhoto = { wallpaperEvents += "photos" },
            onApply = { wallpaperEvents += "apply" },
            onCancel = { wallpaperEvents += "cancel" },
            onRetry = { wallpaperEvents += "retry" },
            onBlurred = { wallpaperEvents += "blur $it" },
            onRemove = { wallpaperEvents += "remove" },
            onDimming = { wallpaperEvents += "dim $it" },
        )

    private fun setScreen(
        appearance: ChatAppearance = ChatAppearance(),
        picker: WallpaperPickerState = WallpaperPickerState(forDarkTheme = false),
        account: AccountWallpapers = AccountWallpapers(),
    ) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false, chat = appearance) {
                CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                    ChatAppearanceScreen(
                        state = ChatAppearanceUiState.Content(appearance, account, picker),
                        onTextScale = { scales += it },
                        onBubbleCorners = { corners += it },
                        onWallpaper = { wallpapers += it },
                        onBack = {},
                        wallpaperActions = wallpaperActions,
                    )
                }
            }
        }
    }

    private fun string(id: Int) = RuntimeEnvironment.getApplication().getString(id)

    @Test
    fun `the wallpapers are radio buttons, the chosen one selected, and a pick ticks`() {
        setScreen(ChatAppearance(wallpaper = ChatWallpaper.Secondary))

        composeRule
            .onNodeWithContentDescription(string(R.string.settings_chat_wallpaper_secondary))
            .performScrollTo()
            .assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        composeRule
            .onNodeWithContentDescription(string(R.string.settings_chat_wallpaper_primary_tertiary))
            .performScrollTo()
            .performClick()

        assertEquals(listOf(ChatWallpaper.PrimaryToTertiary), wallpapers)
        assertEquals(listOf(HapticFeedbackType.SegmentTick), performedHaptics)
    }

    @Test
    fun `the installed wallpapers and the photo picker are offered, and a pick previews`() {
        val loaded = InstalledWallpapers.Loaded(persistentListOf(PHOTO, PATTERN))
        setScreen(picker = WallpaperPickerState(forDarkTheme = false, installed = loaded))

        composeRule
            .onNodeWithContentDescription(string(R.string.settings_chat_wallpaper_photo))
            .performScrollTo()
            .performClick()
        val pattern = RuntimeEnvironment.getApplication().getString(R.string.settings_chat_wallpaper_pattern, 2)
        composeRule
            .onNodeWithContentDescription(pattern)
            .performScrollTo()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .performClick()

        assertEquals(listOf("photos", "pick 2"), wallpaperEvents)
    }

    @Test
    fun `a photo from the phone says it is uploaded, and waits for Apply or Cancel`() {
        setScreen(picker = WallpaperPickerState(forDarkTheme = true, pending = WallpaperChoice.NewPhoto("/x.jpg")))

        composeRule.onNodeWithText(string(R.string.settings_chat_wallpaper_upload_note)).performScrollTo()
        composeRule.onNodeWithText(string(R.string.settings_chat_wallpaper_cancel)).performScrollTo().performClick()
        composeRule.onNodeWithText(string(R.string.settings_chat_wallpaper_apply)).performScrollTo().performClick()

        assertEquals(listOf("cancel", "apply"), wallpaperEvents)
    }

    @Test
    fun `a picked photo can be blurred, a pattern cannot`() {
        setScreen(picker = WallpaperPickerState(forDarkTheme = false, pending = WallpaperChoice.Existing(PHOTO)))

        composeRule
            .onNodeWithText(string(R.string.settings_chat_wallpaper_blur))
            .performScrollTo()
            .performClick()

        assertEquals(listOf("blur true"), wallpaperEvents)
    }

    @Test
    fun `the account's wallpaper can be removed, and dark theme's dimmed`() {
        setScreen(
            appearance = ChatAppearance(wallpaperDimming = 30),
            picker = WallpaperPickerState(forDarkTheme = true),
            account = AccountWallpapers(dark = PHOTO),
        )

        composeRule
            .onNodeWithText(string(R.string.settings_chat_wallpaper_remove))
            .performScrollTo()
            .performClick()
        composeRule
            .onNodeWithContentDescription(string(R.string.settings_chat_wallpaper_dimming))
            .performScrollTo()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "30%"))
            .performSemanticsAction(SemanticsActions.SetProgress) { it(LAST_DIMMING_STEP) }

        assertEquals(listOf("remove", "dim 100"), wallpaperEvents)
    }

    @Test
    fun `installed wallpapers that cannot load offer to try again`() {
        setScreen(picker = WallpaperPickerState(forDarkTheme = false, installed = InstalledWallpapers.Failed))

        composeRule
            .onNodeWithText(string(R.string.settings_chat_wallpaper_installed_failed))
            .performScrollTo()
            .performClick()

        assertEquals(listOf("retry"), wallpaperEvents)
    }

    @Test
    fun `the picker opens on the theme the page shows in`() {
        setScreen(picker = WallpaperPickerState())

        composeRule.waitForIdle()
        assertEquals(listOf("theme false"), wallpaperEvents)
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
        const val LAST_DIMMING_STEP = 10f

        val PHOTO = Wallpaper.Photo(1, WallpaperImage(FileRef(10, 0)), isBlurred = false)
        val PATTERN = Wallpaper.Pattern(2, WallpaperImage(FileRef(20, 0), isVector = true), 50, false)

        /** The index of 130% among 85%, 90%, …, 130%. */
        const val LAST_TEXT_STEP = 9f
    }
}
