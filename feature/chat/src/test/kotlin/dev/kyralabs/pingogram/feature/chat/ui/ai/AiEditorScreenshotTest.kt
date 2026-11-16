package dev.kyralabs.pingogram.feature.chat.ui.ai

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.feature.chat.ui.COMPACT
import dev.kyralabs.pingogram.feature.chat.ui.ChatScreen
import dev.kyralabs.pingogram.feature.chat.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chat.ui.ScreenshotTheme
import dev.kyralabs.pingogram.feature.chat.ui.ScreenshotVariant
import dev.kyralabs.pingogram.feature.chat.ui.goldenPath
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of the AI editor's sheet over a conversation on a compact phone (`docs/DESIGN_SYSTEM.md` §6 "AI text
 * editor", `docs/TESTING.md`): the first use's explanation, a fix with its changes marked, a rewrite with emoji, a
 * translation under way, Telegram's limit, and a shared style's sheet; light and dark, the rewrite also AMOLED, high
 * contrast, right to left and at 200% font. Brand colors.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class AiEditorScreenshotTest(
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
    fun amoled() {
        assumeTrue(stateName == "rewrite")
        capture(ScreenshotVariant.Amoled)
    }

    @Test
    fun highContrast() {
        assumeTrue(stateName == "rewrite")
        capture(ScreenshotVariant.HighContrast)
    }

    @Test
    fun rtl() {
        assumeTrue(stateName == "rewrite")
        capture(ScreenshotVariant.Rtl)
    }

    /** At 200% font scale the sheet scrolls and its buttons wrap (`REQUIREMENTS.md` §6.3). */
    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun fontScale200() {
        assumeTrue(stateName == "rewrite")
        capture(ScreenshotVariant.Light, "fontScale200")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        variant: ScreenshotVariant,
        name: String = variant.goldenName,
    ) {
        composeRule.setContent {
            ScreenshotTheme(variant) {
                ChatScreen(state = ChatPreviewData.privateChat, onEvent = {}, onBack = {})
                when (stateName) {
                    "consent" -> AiEditorSheet(AiEditorUiState.Consent, onEvent = {})
                    "style" -> TextStyleSheet(AiEditorPreviewData.style, onEvent = {}, onClose = {})
                    else -> AiEditorSheet(EDITORS.getValue(stateName), onEvent = {})
                }
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, "$stateName.$name"))
    }

    companion object {
        private const val FONT_SCALE_200 = 2f

        private val EDITORS: Map<String, AiEditorUiState.Editor> =
            mapOf(
                "fixed" to AiEditorPreviewData.fixed,
                "rewrite" to AiEditorPreviewData.rewrite,
                "translating" to AiEditorPreviewData.translating,
                "limit" to AiEditorPreviewData.limit,
            )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun states(): List<Array<Any>> = (listOf("consent", "style") + EDITORS.keys).map { arrayOf(it) }
    }
}
