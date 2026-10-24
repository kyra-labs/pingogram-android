package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.feature.chat.ui.poll.PollVotersBottomSheet
import dev.kyralabs.pingogram.feature.chat.ui.poll.StopPollAlert
import dev.kyralabs.pingogram.feature.chat.ui.preview.PollPreviewData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of polls (`docs/TESTING.md`, `docs/DESIGN_SYSTEM.md` §6 "Polls", "Poll voters"), every state in the looks
 * the audit asks for (§12): light, dark, true black, high contrast, right to left, a medium window and 200% font
 * scale. A group's conversation with polls to vote in (one tap; answers checked before "Vote"), with results (a public
 * poll with "View votes", a quiz answered wrong with its explanation), and in other states (a vote on its way, a poll
 * only members may vote in, a closed poll); the voters' sheet loaded, loading and failed; the question before a poll
 * is stopped. Brand colors.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class PollScreenshotTest(
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
    fun amoled() = capture(ScreenshotVariant.Amoled)

    @Test
    fun highContrast() = capture(ScreenshotVariant.HighContrast)

    @Test
    fun rtl() = capture(ScreenshotVariant.Rtl)

    @Test
    @Config(qualifiers = MEDIUM)
    fun medium() = capture(ScreenshotVariant.Medium)

    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun fontScale200() = capture(ScreenshotVariant.Light, "fontScale200")

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        variant: ScreenshotVariant,
        name: String = variant.goldenName,
    ) {
        composeRule.setContent {
            ScreenshotTheme(variant) {
                when (stateName) {
                    "voting" -> {
                        ChatScreen(state = PollPreviewData.voting, onEvent = {}, onBack = {})
                    }

                    "results" -> {
                        ChatScreen(state = PollPreviewData.results, onEvent = {}, onBack = {})
                    }

                    "states" -> {
                        ChatScreen(state = PollPreviewData.states, onEvent = {}, onBack = {})
                    }

                    "voters" -> {
                        ChatScreen(state = PollPreviewData.results, onEvent = {}, onBack = {})
                        PollVotersBottomSheet(PollPreviewData.voters, onEvent = {})
                    }

                    "votersLoading" -> {
                        ChatScreen(state = PollPreviewData.results, onEvent = {}, onBack = {})
                        PollVotersBottomSheet(PollPreviewData.votersLoading, onEvent = {})
                    }

                    "votersFailed" -> {
                        ChatScreen(state = PollPreviewData.results, onEvent = {}, onBack = {})
                        PollVotersBottomSheet(PollPreviewData.votersFailed, onEvent = {})
                    }

                    "stop" -> {
                        ChatScreen(state = PollPreviewData.results, onEvent = {}, onBack = {})
                        StopPollAlert(onEvent = {})
                    }
                }
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, "$stateName.$name"))
    }

    companion object {
        private const val FONT_SCALE_200 = 2f

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun states(): List<Array<Any>> =
            listOf("voting", "results", "states", "voters", "votersLoading", "votersFailed", "stop").map { arrayOf(it) }
    }
}
