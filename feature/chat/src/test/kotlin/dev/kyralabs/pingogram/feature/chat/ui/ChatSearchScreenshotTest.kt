package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatSearchPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.search.ChatSearchUiState
import dev.kyralabs.pingogram.feature.chat.ui.search.SearchSenderSheet
import dev.kyralabs.pingogram.feature.chat.ui.search.SenderSheet
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of the search in the conversation (`REQUIREMENTS.md` §5.9, `docs/DESIGN_SYSTEM.md` §6 "In-chat search") on a
 * compact phone, light, dark and right to left, and on an expanded window (`docs/TESTING.md`): "motion" found in a
 * group, its second of three results shown with the word marked in its bubble; the search limited to Grace's messages,
 * the first of 50 or more; a query that finds nothing; and the members' sheet. Brand colors.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ChatSearchScreenshotTest(
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
    fun rtl() = capture(ScreenshotVariant.Rtl)

    @Test
    @Config(qualifiers = EXPANDED)
    fun expanded() = capture(ScreenshotVariant.Light, "expanded")

    /** At 200% font scale (`REQUIREMENTS.md` §6.3), the state with the most text: it grows and never clips. */
    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun fontScale200() {
        assumeTrue(stateName == "found")
        capture(ScreenshotVariant.Light, "fontScale200")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        variant: ScreenshotVariant,
        name: String = variant.goldenName,
    ) {
        val (chat, search, sheet) = STATES.getValue(stateName)
        composeRule.setContent {
            ScreenshotTheme(variant) {
                ChatScreen(
                    state = chat,
                    onEvent = {},
                    onBack = {},
                    search = search,
                    searchQuery = TextFieldState(if (search.sender == null) ChatSearchPreviewData.QUERY else ""),
                )
                sheet?.let { SearchSenderSheet(it, TextFieldState(), onEvent = {}) }
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, "$stateName.$name"))
    }

    companion object {
        val STATES: Map<String, Triple<ChatUiState, ChatSearchUiState, SenderSheet?>> =
            with(ChatSearchPreviewData) {
                val plain = ChatPreviewData.groupChat
                mapOf(
                    "found" to Triple(groupChat, found, null),
                    "sender" to Triple(plain, withSender, null),
                    "noResults" to Triple(plain, noResults, null),
                    "senders" to Triple(plain, found, senders),
                )
            }

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun states(): List<Array<Any>> = STATES.keys.map { arrayOf(it) }
    }
}
