package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.feature.chat.ui.preview.TranslationPreviewData
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of message translation (`REQUIREMENTS.md` FR-C3, `docs/TESTING.md`) on a compact phone, light and dark: a
 * chat in Spanish with the translate bar offering English, the same chat translated (the newest message on its way),
 * the actions of a message with "Translate", and a message translated from Hebrew beside one left in Hebrew, also
 * right to left; the translated chat on an expanded window too, and searched: the result shown is a translated message,
 * which marks the word found in its translation. Brand colors.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class TranslationScreenshotTest(
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
        assumeTrue(stateName == "rightToLeft" || stateName == "translated")
        capture(ScreenshotVariant.Rtl)
    }

    @Test
    @Config(qualifiers = EXPANDED)
    fun expandedLight() {
        assumeTrue(stateName == "translated")
        capture(ScreenshotVariant.Light, "expandedLight")
    }

    @Test
    @Config(qualifiers = EXPANDED)
    fun expandedDark() {
        assumeTrue(stateName == "translated")
        capture(ScreenshotVariant.Dark, "expandedDark")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        variant: ScreenshotVariant,
        name: String = variant.goldenName,
    ) {
        val state = STATES.getValue(stateName)
        composeRule.setContent {
            ScreenshotTheme(variant) {
                if (stateName == "searched") {
                    ChatScreen(
                        state = state,
                        onEvent = {},
                        onBack = {},
                        search = TranslationPreviewData.searchFound,
                        searchQuery = TextFieldState(TranslationPreviewData.SEARCH_QUERY),
                    )
                } else {
                    ChatScreen(state = state, onEvent = {}, onBack = {})
                }
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, "$stateName.$name"))
    }

    companion object {
        val STATES: Map<String, ChatUiState> =
            mapOf(
                "offered" to TranslationPreviewData.offered,
                "translated" to TranslationPreviewData.translated,
                "actions" to TranslationPreviewData.offered.copy(messageActions = TranslationPreviewData.actions),
                "rightToLeft" to TranslationPreviewData.rightToLeft,
                "searched" to TranslationPreviewData.searched,
            )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun states(): List<Array<Any>> = STATES.keys.map { arrayOf(it) }
    }
}
