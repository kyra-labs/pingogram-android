package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.feature.chat.ui.bot.BotCommandsBottomSheet
import dev.kyralabs.pingogram.feature.chat.ui.bot.BotCommandsSheet
import dev.kyralabs.pingogram.feature.chat.ui.bot.CommandSuggestions
import dev.kyralabs.pingogram.feature.chat.ui.bot.ComposerBotButton
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerText
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerUiState
import dev.kyralabs.pingogram.feature.chat.ui.content.PreviewImages
import dev.kyralabs.pingogram.feature.chat.ui.preview.BotPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of the bots in the conversation (`docs/TESTING.md`, `REQUIREMENTS.md` FR-C5) on a compact phone, light and
 * dark: the bot chat's composer with "/" at the pill's start and "+" at its end; the suggestions while "/st" is typed;
 * a group's suggestions of two bots, with their avatars; the commands sheet, and a group's; the empty bot chat with
 * what the bot does and "Start". The suggestions also right to left; the empty chat and the suggestions in an expanded
 * window; the sheet at 200% font scale. Brand colors.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class BotScreenshotTest(
    private val stateName: String,
) {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun light() = capture(ScreenshotVariant.Light)

    @Test
    fun dark() = capture(ScreenshotVariant.Dark)

    @Test
    fun rtl() {
        assumeTrue(stateName == "suggestions")
        capture(ScreenshotVariant.Rtl)
    }

    @Test
    @Config(qualifiers = EXPANDED)
    fun expanded() {
        assumeTrue(stateName == "start" || stateName == "suggestions")
        capture(ScreenshotVariant.Light, "expanded")
    }

    /** At 200% font scale the rows grow instead of clipping (`REQUIREMENTS.md` §6.3). */
    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun fontScale200() {
        assumeTrue(stateName == "sheet")
        capture(ScreenshotVariant.Light, "fontScale200")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        variant: ScreenshotVariant,
        name: String = variant.goldenName,
    ) {
        val shown = STATES.getValue(stateName)
        composeRule.setContent {
            ScreenshotTheme(variant) {
                // The preview images in place of TDLib's files (PreviewImages), as in the previews.
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    PreviewImages {
                        ChatScreen(
                            state = shown.state,
                            onEvent = {},
                            onBack = {},
                            composerText = remember { ComposerText(FormattedText(shown.typed, persistentListOf())) },
                            commandSuggestions = shown.suggestions,
                        )
                        shown.sheet?.let { BotCommandsBottomSheet(sheet = it, onEvent = {}) }
                    }
                }
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, "$stateName.$name"))
    }

    /** What a golden shows: the conversation, what is typed, the suggestions for it and the sheet over it. */
    class Shown(
        val state: ChatUiState,
        val typed: String = "",
        val suggestions: CommandSuggestions? = null,
        val sheet: BotCommandsSheet? = null,
    )

    companion object {
        private const val FONT_SCALE_200 = 2f

        private val groupChat =
            ChatPreviewData.groupChat.copy(
                composer =
                    (ChatPreviewData.groupChat.composer as ComposerUiState.Available)
                        .copy(bot = ComposerBotButton.Commands),
            )

        val STATES: Map<String, Shown> =
            with(BotPreviewData) {
                mapOf(
                    "composer" to Shown(botChat),
                    "suggestions" to Shown(botChat, typed = "/st", suggestions = suggestions),
                    "groupSuggestions" to Shown(groupChat, typed = "/s", suggestions = groupSuggestions),
                    "sheet" to Shown(botChat, sheet = sheet),
                    "groupSheet" to Shown(groupChat, sheet = groupSheet),
                    "start" to Shown(emptyBotChat),
                )
            }

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun states(): List<Array<Any>> = STATES.keys.map { arrayOf(it) }
    }
}
