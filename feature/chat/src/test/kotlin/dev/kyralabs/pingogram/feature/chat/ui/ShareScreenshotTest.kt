package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerText
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.preview.SharePreviewData
import dev.kyralabs.pingogram.feature.chat.ui.share.ShareBottomSheet
import dev.kyralabs.pingogram.feature.chat.ui.share.ShareSheet
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of the share sheet (`REQUIREMENTS.md` FR-N2, direct share) over the conversation, light and dark
 * (`docs/TESTING.md`): a shared link with nothing picked; one chat picked, which the button opens; three photos to two
 * chats with a comment. On a compact phone, and the link on an expanded window, where the sheet keeps its maximum
 * width. Brand colors.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ShareScreenshotTest(
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
    fun expandedLight() {
        assumeTrue(stateName == "link")
        capture("expandedLight", ThemeMode.Light)
    }

    @Test
    @Config(qualifiers = EXPANDED)
    fun expandedDark() {
        assumeTrue(stateName == "link")
        capture("expandedDark", ThemeMode.Dark)
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
                ShareBottomSheet(sheet = sheet, comment = ComposerText(comment), onEvent = {})
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, "$stateName.$variant"))
    }

    companion object {
        val STATES: Map<String, Pair<ShareSheet, FormattedText>> =
            mapOf(
                "link" to (SharePreviewData.link to FormattedText.Empty),
                "oneChat" to (SharePreviewData.oneChat to FormattedText.Empty),
                "photosToTwoChats" to
                    (SharePreviewData.photosToTwoChats to FormattedText("For the album", persistentListOf())),
            )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun states(): List<Array<Any>> = STATES.keys.map { arrayOf(it) }
    }
}
