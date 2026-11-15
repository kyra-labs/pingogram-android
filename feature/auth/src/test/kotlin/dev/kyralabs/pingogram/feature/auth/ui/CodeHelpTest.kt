package dev.kyralabs.pingogram.feature.auth.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.kyralabs.pingogram.feature.auth.ui.preview.AuthPreviewData
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The code step's "Didn't get the code?" (board 23): its dialog asks for a new code the other way once the countdown
 * ended, and leads to another number; "Wrong number?" sits under the description.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class CodeHelpTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<AuthEvent>()

    private fun show(state: AuthUiState) {
        composeRule.setContent {
            ScreenshotTheme(ScreenshotVariant.Light) { AuthScreen(state = state, onEvent = { events += it }) }
        }
    }

    @Test
    fun `once the countdown ended, the dialog sends a new code the other way`() {
        show(AuthPreviewData.codeResend)

        composeRule.onNodeWithText("Didn't get the code?").performClick()
        // The same way also shows under the field; this is the dialog's.
        composeRule.onNode(hasText("Call me with the code") and hasAnyAncestor(isDialog())).performClick()

        assertEquals(listOf<AuthEvent>(AuthEvent.ResendCode), events)
    }

    @Test
    fun `during the countdown the dialog only leads to another number`() {
        show(AuthPreviewData.code)

        composeRule.onNodeWithText("Didn't get the code?").performClick()
        composeRule.onNode(hasText("OK") and hasAnyAncestor(isDialog())).performClick()
        composeRule.onNodeWithText("Wrong number?").performClick()

        assertEquals(listOf<AuthEvent>(AuthEvent.ChangePhoneNumber), events)
    }
}
