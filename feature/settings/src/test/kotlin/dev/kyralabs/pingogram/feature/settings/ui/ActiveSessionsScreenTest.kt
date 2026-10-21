package dev.kyralabs.pingogram.feature.settings.ui

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData
import kotlinx.collections.immutable.persistentSetOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Terminating sessions on the Active sessions page, and confirming new logins. The screen is stateless: these check
 * its events.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ActiveSessionsScreenTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<ActiveSessionsEvent>()

    private val sessions = SettingsPreviewData.sessions.sessions as SessionsState.Loaded

    private fun setScreen(state: ActiveSessionsUiState = SettingsPreviewData.sessions) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                ActiveSessionsScreen(state = state, onEvent = { events += it }, onBack = {})
            }
        }
    }

    private fun string(
        @StringRes id: Int,
        vararg args: Any,
    ) = RuntimeEnvironment.getApplication().getString(id, *args)

    private fun dialogButton(
        @StringRes id: Int,
    ) = composeRule.onNode(hasText(string(id)) and hasAnyAncestor(isDialog()))

    @Test
    fun `tapping another session asks to terminate it`() {
        setScreen()

        composeRule
            .onNodeWithText("MacBook Pro")
            .performScrollTo()
            .assertHasClickAction()
            .performClick()

        assertEquals(listOf(ActiveSessionsEvent.Terminate(sessions.others.first())), events)
    }

    @Test
    fun `this device cannot be terminated from its row`() {
        setScreen()

        composeRule.onNodeWithText("Google Pixel 8").assertHasNoClickAction()
    }

    @Test
    fun `the confirmation terminates or dismisses`() {
        setScreen(SettingsPreviewData.sessions.copy(dialog = TerminateDialog.Session(3, UiText.Plain("MacBook Pro"))))

        composeRule
            .onNodeWithText(
                string(R.string.settings_terminate_session_dialog_text, "MacBook Pro".bidiIsolated()).bidiIsolated(),
            ).assertExists()
        dialogButton(R.string.settings_terminate_session_dialog_confirm).performClick()
        dialogButton(R.string.settings_dialog_dismiss).performClick()

        assertEquals(listOf(ActiveSessionsEvent.ConfirmTermination, ActiveSessionsEvent.DismissTermination), events)
    }

    @Test
    fun `a Hebrew device name stays isolated in the confirmation`() {
        setScreen(SettingsPreviewData.sessions.copy(dialog = TerminateDialog.Session(3, UiText.Plain("הטלפון של שרה"))))

        // First-strong isolates around the name and around the sentence: the name keeps its right-to-left order
        // without pulling "will be logged out" before it.
        composeRule
            .onNodeWithText("\u2068\u2068הטלפון של שרה\u2069 will be logged out of your Telegram account.\u2069")
            .assertExists()
    }

    @Test
    fun `terminate all other sessions asks for confirmation`() {
        setScreen()

        composeRule.onNodeWithText(string(R.string.settings_terminate_all)).performClick()

        assertEquals(listOf(ActiveSessionsEvent.TerminateAllOthers), events)
    }

    @Test
    fun `the confirmation to terminate all terminates`() {
        setScreen(SettingsPreviewData.sessions.copy(dialog = TerminateDialog.AllOthers))

        dialogButton(R.string.settings_terminate_all_dialog_confirm).performClick()

        assertEquals(listOf(ActiveSessionsEvent.ConfirmTermination), events)
    }

    @Test
    fun `a new login is confirmed or terminated`() {
        setScreen()

        composeRule.onNodeWithText(string(R.string.settings_session_confirm)).performScrollTo().performClick()
        composeRule.onNodeWithText(string(R.string.settings_session_not_me)).performScrollTo().performClick()

        val newLogin = sessions.unconfirmed.single()
        assertEquals(
            listOf(ActiveSessionsEvent.ConfirmLogin(newLogin.id), ActiveSessionsEvent.Terminate(newLogin)),
            events,
        )
    }

    @Test
    fun `a session being terminated waits with progress`() {
        setScreen(SettingsPreviewData.sessions.copy(busySessionIds = persistentSetOf(3)))

        composeRule.onNodeWithText("MacBook Pro").performScrollTo().assertIsNotEnabled()
        composeRule.onNode(hasContentDescription(string(R.string.settings_session_busy))).assertExists()
    }

    @Test
    fun `with only this device, nothing can be terminated`() {
        setScreen(SettingsPreviewData.onlyThisDevice)

        composeRule.onNodeWithText(string(R.string.settings_terminate_all)).assertDoesNotExist()
    }

    @Test
    fun `an error offers to try again`() {
        setScreen(ActiveSessionsUiState(sessions = SessionsState.Failed(DomainError.Network)))

        composeRule.onNodeWithText(string(R.string.settings_retry)).performClick()

        assertEquals(listOf(ActiveSessionsEvent.Retry), events)
    }
}
