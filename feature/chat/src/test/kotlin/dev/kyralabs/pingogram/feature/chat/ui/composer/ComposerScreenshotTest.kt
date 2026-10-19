package dev.kyralabs.pingogram.feature.chat.ui.composer

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.ui.COMPACT
import dev.kyralabs.pingogram.feature.chat.ui.ChatScreen
import dev.kyralabs.pingogram.feature.chat.ui.ChatUiState
import dev.kyralabs.pingogram.feature.chat.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chat.ui.EXPANDED
import dev.kyralabs.pingogram.feature.chat.ui.goldenPath
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of the composer in the conversation on a compact phone, light and dark (`docs/TESTING.md`): empty, with
 * three lines of text (the field grows, and the send button is in), with every kind of formatting it applies, and the
 * label where the user cannot write; the formatted one also on an expanded window, and the long text at 200% font
 * scale. Brand colors.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ComposerScreenshotTest(
    private val stateName: String,
) {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun light() = capture("light", ThemeMode.Light)

    @Test
    fun dark() = capture("dark", ThemeMode.Dark)

    @Test
    @Config(qualifiers = EXPANDED)
    fun expanded() {
        // One state is enough for the wide layout.
        assumeTrue(stateName == "formatted")
        capture("expanded", ThemeMode.Light)
    }

    /** At 200% font scale the field and the send button grow instead of clipping (`REQUIREMENTS.md` §6.3). */
    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun fontScale200() {
        assumeTrue(stateName == "withText")
        capture("fontScale200", ThemeMode.Light)
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        variant: String,
        themeMode: ThemeMode,
    ) {
        val (state, text) = STATES.getValue(stateName)
        composeRule.setContent {
            PingogramTheme(themeMode = themeMode, dynamicColor = false) {
                ChatScreen(state = state, onEvent = {}, onBack = {}, composerText = ComposerText(text))
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, "$stateName.$variant"))
    }

    companion object {
        private const val FONT_SCALE_200 = 2f

        private val longText =
            FormattedText(
                "Running ten minutes late, the ferry was slow again.\n" +
                    "Save me a seat by the window?\nI'll bring the tickets.",
                persistentListOf(),
            )

        private val formattedText =
            "Bold, italic, underlined, struck, code, a spoiler and a link".let { text ->
                fun TextEntityType.over(part: String) = TextEntity(text.indexOf(part), part.length, this)
                FormattedText(
                    text,
                    persistentListOf(
                        TextEntityType.Bold.over("Bold"),
                        TextEntityType.Italic.over("italic"),
                        TextEntityType.Underline.over("underlined"),
                        TextEntityType.Strikethrough.over("struck"),
                        TextEntityType.Code.over("code"),
                        TextEntityType.Spoiler.over("a spoiler"),
                        TextEntityType.TextUrl("https://example.org").over("a link"),
                    ),
                )
            }

        val STATES: Map<String, Pair<ChatUiState, FormattedText>> =
            mapOf(
                "empty" to (ChatPreviewData.privateChat to FormattedText.Empty),
                "withText" to (ChatPreviewData.privateChat to longText),
                "formatted" to (ChatPreviewData.privateChat to formattedText),
                "cannotWrite" to (ChatPreviewData.cannotWrite to FormattedText.Empty),
            )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun states(): List<Array<Any>> = STATES.keys.map { arrayOf(it) }
    }
}
