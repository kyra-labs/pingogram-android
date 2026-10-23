package dev.kyralabs.pingogram.feature.settings.ui.qr

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.settings.ui.COMPACT
import dev.kyralabs.pingogram.feature.settings.ui.ComponentActivityRegistration
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Linking a device logs it in only on "Log in"; "Cancel" and "Done" leave the page. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class QrLoginScreenTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<QrLoginEvent>()
    private var left = 0

    private fun setContent(state: QrLoginUiState) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) { QrLoginScreen(state, { events += it }, { left++ }) }
        }
    }

    @Test
    fun `log in confirms, and nothing else does`() {
        setContent(QrLoginUiState.Confirm())
        assertEquals(emptyList<QrLoginEvent>(), events)

        composeRule.onNodeWithText("Log in").performClick()

        assertEquals(listOf(QrLoginEvent.Confirm), events)
    }

    @Test
    fun `log in waits while Telegram answers`() {
        setContent(QrLoginUiState.Confirm(isConfirming = true))

        composeRule.onNodeWithText("Log in").assertIsNotEnabled()
    }

    @Test
    fun `cancel leaves without confirming`() {
        setContent(QrLoginUiState.Confirm())

        composeRule.onNodeWithText("Cancel").performClick()

        assertEquals(1, left)
        assertEquals(emptyList<QrLoginEvent>(), events)
    }

    @Test
    fun `the new session shows, and done leaves`() {
        setContent(QrLoginUiState.Done(PreviewLinkedSession))

        composeRule.onNodeWithText("MacBook Pro").assertExists()
        composeRule.onNodeWithText("Done").performClick()

        assertEquals(1, left)
    }
}
