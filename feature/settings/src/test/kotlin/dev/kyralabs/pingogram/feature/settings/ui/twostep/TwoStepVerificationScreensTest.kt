package dev.kyralabs.pingogram.feature.settings.ui.twostep

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.COMPACT
import dev.kyralabs.pingogram.feature.settings.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import dev.kyralabs.pingogram.feature.settings.ui.twostep.TwoStepVerificationFlowEvent as Event

/** What the 2-step verification page and its flows do when tapped. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class TwoStepVerificationScreensTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val opened = mutableListOf<TwoStepVerificationFlow>()
    private val pageEvents = mutableListOf<TwoStepVerificationEvent>()
    private val flowEvents = mutableListOf<Event>()
    private var backs = 0

    private fun string(
        @StringRes id: Int,
    ) = RuntimeEnvironment.getApplication().getString(id)

    private fun setPage(state: TwoStepVerificationUiState) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                TwoStepVerificationScreen(state, onEvent = { pageEvents += it }, onOpen = { opened += it }, onBack = {})
            }
        }
    }

    private fun setFlow(state: TwoStepVerificationFlowUiState) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                TwoStepVerificationFlowScreen(state, onEvent = { flowEvents += it }, onBack = { backs++ })
            }
        }
    }

    @Test
    fun `the rows open their flows, and turning off asks first`() {
        setPage(SettingsPreviewData.twoStepOn)

        composeRule.onNodeWithText(string(R.string.settings_two_step_change_password)).performClick()
        composeRule.onNodeWithText(string(R.string.settings_two_step_change_recovery_email)).performClick()
        composeRule.onNodeWithText(string(R.string.settings_two_step_turn_off)).performClick()
        assertEquals(2, opened.size)
        composeRule.onNodeWithText(string(R.string.settings_two_step_turn_off_title)).assertExists()
        // The dialog's confirm button: the last node saying "Turn off", after the row.
        composeRule.onAllNodesWithText(string(R.string.settings_two_step_turn_off)).onLast().performClick()

        assertEquals(
            listOf(
                TwoStepVerificationFlow.ChangePassword,
                TwoStepVerificationFlow.ChangeRecoveryEmail,
                TwoStepVerificationFlow.RemovePassword,
            ),
            opened,
        )
    }

    @Test
    fun `a waiting email and reset can be cancelled`() {
        setPage(SettingsPreviewData.twoStepPending)

        composeRule.onNodeWithText(string(R.string.settings_two_step_enter_email_code)).performClick()
        composeRule.onNodeWithText(string(R.string.settings_two_step_cancel_email)).performClick()
        composeRule.onNodeWithText(string(R.string.settings_two_step_cancel_reset)).performClick()

        assertEquals(listOf(TwoStepVerificationFlow.ConfirmRecoveryEmail), opened)
        assertEquals(
            listOf(TwoStepVerificationEvent.CancelRecoveryEmail, TwoStepVerificationEvent.CancelReset),
            pageEvents,
        )
    }

    @Test
    fun `the current password submits what was typed, and forgot password asks for recovery`() {
        setFlow(
            SettingsPreviewData.twoStepFlow(
                TwoStepVerificationFlow.ChangePassword,
                TwoStepVerificationStep.CurrentPassword,
            ),
        )

        composeRule.onNodeWithText(string(R.string.settings_two_step_label_password)).performTextInput("secret")
        composeRule.onNodeWithText(string(R.string.settings_two_step_next)).performClick()
        composeRule.onNodeWithText(string(R.string.settings_two_step_forgot_password)).performClick()

        assertTrue(Event.SubmitCurrentPassword("secret") in flowEvents)
        assertEquals(Event.ForgotPassword, flowEvents.last())
    }

    @Test
    fun `back returns to the step before while there is one`() {
        setFlow(SettingsPreviewData.twoStepFlow(TwoStepVerificationFlow.SetPassword, TwoStepVerificationStep.Hint))

        composeRule.onNodeWithContentDescription(string(R.string.settings_back)).performClick()

        assertEquals(listOf(Event.Back), flowEvents)
        assertEquals(0, backs)
    }

    @Test
    fun `the reset asks for confirmation`() {
        setFlow(SettingsPreviewData.twoStepFlow(TwoStepVerificationFlow.ChangePassword, TwoStepVerificationStep.Reset))

        composeRule.onNodeWithText(string(R.string.settings_two_step_reset_password)).performClick()
        assertTrue(flowEvents.isEmpty())
        composeRule.onNodeWithText(string(R.string.settings_two_step_reset_confirm)).performClick()

        assertEquals(listOf(Event.ConfirmReset), flowEvents)
    }
}
