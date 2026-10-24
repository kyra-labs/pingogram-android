package dev.kyralabs.pingogram.feature.chat.ui.scheduled

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.ui.component.LoadingIndicatorDelay
import dev.kyralabs.pingogram.feature.chat.ui.COMPACT
import dev.kyralabs.pingogram.feature.chat.ui.ChatScreen
import dev.kyralabs.pingogram.feature.chat.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chat.ui.MEDIUM
import dev.kyralabs.pingogram.feature.chat.ui.ScreenshotTheme
import dev.kyralabs.pingogram.feature.chat.ui.ScreenshotVariant
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerText
import dev.kyralabs.pingogram.feature.chat.ui.goldenPath
import dev.kyralabs.pingogram.feature.chat.ui.preview.ScheduledPreviewData
import kotlinx.collections.immutable.persistentListOf
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.TimeZone

/**
 * Goldens of the scheduled messages (`docs/DESIGN_SYSTEM.md` §6 "Scheduled messages", "Scheduled messages bar") on a
 * compact phone, every state in every look the audit asks for (`docs/DESIGN_SYSTEM.md` §12): light, dark, AMOLED, high
 * contrast, right to left, a 700 dp window and 200% font scale. The states: the list with five messages over three
 * sections, one message, editing one in the composer, loading, empty, failed; over the list the actions sheet, the
 * delete confirmation and the pickers' first step; and the conversation with its bar. Brand colors.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ScheduledMessagesScreenshotTest(
    private val stateName: String,
) {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var zone: TimeZone? = null

    /** The pickers work in the device's time zone: UTC, so the golden shows the same day everywhere. */
    @Before
    fun useUtc() {
        zone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restoreZone() {
        TimeZone.setDefault(zone)
    }

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

    /** At 200% font scale the bubbles, the bar and the sheets grow instead of clipping (`REQUIREMENTS.md` §6.3). */
    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun fontScale200() = capture(ScreenshotVariant.Light, "fontScale200")

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        variant: ScreenshotVariant,
        name: String = variant.goldenName,
    ) {
        composeRule.setContent { ScreenshotTheme(variant) { Shown(stateName) } }
        composeRule.waitForIdle()
        // The loading indicator shows once loading takes a moment; the golden shows it then.
        if (stateName == "loading") {
            composeRule.mainClock.advanceTimeBy(LoadingIndicatorDelay.inWholeMilliseconds)
            composeRule.waitForIdle()
        }
        captureScreenRoboImage(goldenPath(javaClass, "$stateName.$name"))
    }

    companion object {
        private const val FONT_SCALE_200 = 2f

        /** "Now" for the pickers: 2030-03-01, before the message's time, in a month far from any day the test runs. */
        private val PICKER_NOW = Instant.parse("2030-03-01T09:00:00Z")

        /** The message's time in the pickers: 2030-04-15 at 8:50 AM UTC. */
        private const val PICKER_SEND_DATE = 1_902_473_400L

        private val SCREENS =
            with(ScheduledPreviewData) {
                mapOf(
                    "content" to content,
                    "one" to one,
                    "editing" to editing,
                    "loading" to loading,
                    "empty" to empty,
                    "error" to error,
                    "actions" to withActions,
                    "deleting" to deleting,
                    "rescheduling" to content.copy(reschedule = RescheduleDialog(14, PICKER_SEND_DATE)),
                )
            }

        /** The screen of [stateName] with what shows over it, as the route shows them; or the conversation's bar. */
        @Composable
        private fun Shown(stateName: String) {
            if (stateName == "conversationBar") {
                ChatScreen(
                    state = ScheduledPreviewData.conversation,
                    onEvent = {},
                    onBack = {},
                    onOpenScheduledMessages = {},
                )
                return
            }
            val state = SCREENS.getValue(stateName)
            val text = if (stateName == "editing") ScheduledPreviewData.EDITED_TEXT else ""
            ScheduledMessagesScreen(
                state = state,
                onEvent = {},
                onBack = {},
                composerText = ComposerText(FormattedText(text, persistentListOf())),
            )
            state.actions?.let { ScheduledActionsBottomSheet(it, onEvent = {}) }
            state.deleteDialog?.let { ScheduledDeleteAlert(onConfirm = {}, onDismiss = {}) }
            state.reschedule?.let {
                RescheduleDialogHost(it, onReschedule = { _, _ -> }, onDismiss = {}, now = { PICKER_NOW })
            }
        }

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun states(): List<Array<Any>> = (SCREENS.keys + "conversationBar").map { arrayOf(it) }
    }
}
