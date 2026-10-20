package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerText
import dev.kyralabs.pingogram.feature.chat.ui.forward.ForwardBottomSheet
import dev.kyralabs.pingogram.feature.chat.ui.forward.ForwardSheet
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.preview.ForwardPreviewData
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of the forward sheet over the conversation on a compact phone, light and dark (`docs/TESTING.md`): nothing
 * picked yet; two chats picked with the captions hidden and a comment; an album of three messages without captions.
 * The picked sheet also at 200% font scale. Brand colors.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ForwardScreenshotTest(
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

    /** At 200% font scale the rows and the options grow instead of clipping (`REQUIREMENTS.md` §6.3). */
    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun fontScale200() {
        assumeTrue(stateName == "picked")
        capture("fontScale200", ThemeMode.Light)
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        variant: String,
        themeMode: ThemeMode,
    ) {
        val (sheet, comment) = STATES.getValue(stateName)
        composeRule.setContent {
            PingogramTheme(themeMode = themeMode, dynamicColor = false) {
                ChatScreen(state = ChatPreviewData.privateChat, onEvent = {}, onBack = {})
                ForwardBottomSheet(sheet = sheet, comment = ComposerText(comment), onEvent = {})
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, "$stateName.$variant"))
    }

    companion object {
        private const val FONT_SCALE_200 = 2f

        val STATES: Map<String, Pair<ForwardSheet, FormattedText>> =
            mapOf(
                "nothingPicked" to (ForwardPreviewData.sheet to FormattedText.Empty),
                "picked" to (ForwardPreviewData.withSelection to FormattedText("Look at this", persistentListOf())),
                "album" to (ForwardPreviewData.album to FormattedText.Empty),
            )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun states(): List<Array<Any>> = STATES.keys.map { arrayOf(it) }
    }
}
