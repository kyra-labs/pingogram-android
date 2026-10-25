package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.core.domain.model.ConnectionStatus
import dev.kyralabs.pingogram.core.ui.component.LoadingIndicatorDelay
import dev.kyralabs.pingogram.feature.chat.ui.preview.BubblePreviewData
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.preview.SponsoredPreviewData
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of every conversation state on a compact phone (`docs/TESTING.md`), light and dark: a private chat with
 * grouping, every outgoing state, the unread divider and date pills; a group with names, avatars, replies, a forward,
 * reactions and a link preview; a channel, also with a sponsored message below its newest post; service messages;
 * every kind of formatting; groups of bubbles on both sides with a photo among them; emoji-only messages and reaction
 * badges (one, two, more than fit); loading, empty, error and not found. Brand colors, so goldens do not depend on a
 * wallpaper.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ChatScreenScreenshotTest(
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
    fun amoled() = captureAudit(ScreenshotVariant.Amoled)

    @Test
    fun highContrast() = captureAudit(ScreenshotVariant.HighContrast)

    @Test
    fun rtl() = captureAudit(ScreenshotVariant.Rtl)

    @Test
    @Config(qualifiers = MEDIUM)
    fun medium() = captureAudit(ScreenshotVariant.Medium)

    /** At 200% font scale (`REQUIREMENTS.md` §6.3), the state with the most text: it grows and never clips. */
    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun fontScale200() {
        assumeTrue(stateName == "groupChat")
        capture(ScreenshotVariant.Light, "fontScale200")
    }

    /** While TDLib connects, the top bar says so in place of the status (audit A11). */
    @Test
    fun connectingLight() {
        assumeTrue(stateName == "privateChat" || stateName == "groupChat")
        capture(ScreenshotVariant.Light, "connectingLight", ConnectionStatus.Connecting)
    }

    @Test
    fun connectingDark() {
        assumeTrue(stateName == "privateChat")
        capture(ScreenshotVariant.Dark, "connectingDark", ConnectionStatus.WaitingForNetwork)
    }

    /** The audit's other looks (`docs/DESIGN_SYSTEM.md` §12) for one representative state only. */
    private fun captureAudit(variant: ScreenshotVariant) {
        assumeTrue(stateName == "groupChat")
        capture(variant)
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        variant: ScreenshotVariant,
        name: String = variant.goldenName,
        connection: ConnectionStatus = ConnectionStatus.Ready,
    ) {
        val state = STATES.getValue(stateName)
        composeRule.setContent {
            ScreenshotTheme(variant) {
                ChatScreen(state = state, onEvent = {}, onBack = {}, connection = connection)
            }
        }
        composeRule.waitForIdle()
        // The loading indicator shows once loading takes a moment; the golden shows it then.
        if (state.content == ChatContent.Loading) {
            composeRule.mainClock.advanceTimeBy(LoadingIndicatorDelay.inWholeMilliseconds)
            composeRule.waitForIdle()
        }
        captureScreenRoboImage(goldenPath(javaClass, "$stateName.$name"))
    }

    companion object {
        val STATES =
            mapOf(
                "privateChat" to ChatPreviewData.privateChat,
                "groupChat" to ChatPreviewData.groupChat,
                "channel" to ChatPreviewData.channel,
                "channelSponsored" to SponsoredPreviewData.channel,
                "serviceMessages" to ChatPreviewData.serviceMessages,
                "formattedText" to ChatPreviewData.formattedText,
                "groupedBubbles" to BubblePreviewData.groupedBubbles,
                "emojiAndReactions" to BubblePreviewData.emojiAndReactions,
                "loading" to ChatPreviewData.loading,
                "empty" to ChatPreviewData.empty,
                "error" to ChatPreviewData.error,
                "notFound" to ChatPreviewData.notFound,
            )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun states(): List<Array<Any>> = STATES.keys.map { arrayOf(it) }
    }
}
