package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.core.ui.sticker.LocalCustomEmoji
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.ui.CustomEmojiFakes.HEART
import dev.kyralabs.pingogram.feature.chat.ui.CustomEmojiFakes.LEAF
import dev.kyralabs.pingogram.feature.chat.ui.CustomEmojiFakes.STAR
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerText
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData.bubble
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData.content
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of custom emoji in the conversation (`docs/DESIGN_SYSTEM.md` §4, Custom emoji) on a compact phone, their
 * still pictures from [testCustomEmojiRenderer]: in incoming and outgoing bubbles (one in the middle of a second line,
 * a heart repainted in the bubble's text color), an emoji-only message of two custom emoji, a custom emoji reaction,
 * and a message being edited whose text field shows its custom emoji too; light, dark and right to left (a Hebrew
 * message). "loading" is the same chat before the pictures are there: the fallback emoji in their places.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class CustomEmojiScreenshotTest(
    private val stateName: String,
) {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun light() = capture(ScreenshotVariant.Light)

    @Test
    fun dark() {
        assumeTrue(stateName == "shown")
        capture(ScreenshotVariant.Dark)
    }

    @Test
    fun rtl() {
        assumeTrue(stateName == "shown")
        capture(ScreenshotVariant.Rtl)
    }

    /** At 200% font scale (`REQUIREMENTS.md` §6.3), the state with the most text: it grows and never clips. */
    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun fontScale200() {
        assumeTrue(stateName == "shown")
        capture(ScreenshotVariant.Light, "fontScale200")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        variant: ScreenshotVariant,
        name: String = variant.goldenName,
    ) {
        val renderer = testCustomEmojiRenderer(if (stateName == "loading") emptyMap() else CustomEmojiFakes.all)
        val composer = ComposerText(EDITED)
        composeRule.setContent {
            ScreenshotTheme(variant) {
                CompositionLocalProvider(LocalCustomEmoji provides renderer) {
                    ChatScreen(state = STATE, onEvent = {}, onBack = {}, composerText = composer)
                }
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, "$stateName.$name"))
    }

    companion object {
        private val EDITED = CustomEmojiFakes.text("Back in 5 ⭐", "⭐" to STAR)

        private const val GARDEN = "The garden is in full bloom this week 🍀 and the first ⭐ of the season came out"

        private val STATE =
            ChatUiState(
                header = ChatPreviewData.privateChat.header,
                composer = ChatPreviewData.canWrite,
                content =
                    content(
                        bubble(
                            id = 6,
                            text = "⭐🍀",
                            entities = CustomEmojiFakes.text("⭐🍀", "⭐" to STAR, "🍀" to LEAF).entities,
                            time = "9:46 AM",
                            isOutgoing = true,
                            status = OutgoingStatus.Read,
                            isEmojiOnly = true,
                        ),
                        bubble(
                            id = 5,
                            text = "Thanks so much ❤️",
                            entities = CustomEmojiFakes.text("Thanks so much ❤️", "❤️" to HEART).entities,
                            time = "9:45 AM",
                            isOutgoing = true,
                            status = OutgoingStatus.Read,
                        ),
                        bubble(
                            id = 4,
                            text = "מזל טוב ⭐ ליום ההולדת",
                            entities = CustomEmojiFakes.text("מזל טוב ⭐ ליום ההולדת", "⭐" to STAR).entities,
                            time = "9:44 AM",
                            sender = UiText.Plain("Ada"),
                        ),
                        bubble(
                            id = 3,
                            text = GARDEN,
                            entities = CustomEmojiFakes.text(GARDEN, "🍀" to LEAF, "⭐" to STAR).entities,
                            time = "9:43 AM",
                            sender = UiText.Plain("Ada"),
                            reactions =
                                ChatPreviewData.reactions(
                                    ReactionChip(ReactionLabel.CustomEmoji(STAR), count = 2, isChosen = false),
                                ),
                        ),
                    ),
            )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun states(): List<Array<Any>> = listOf(arrayOf("shown"), arrayOf("loading"))
    }
}
