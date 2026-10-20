package dev.kyralabs.pingogram.feature.auth.ui

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedDispatcher
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.auth.R
import dev.kyralabs.pingogram.feature.auth.ui.preview.AuthPreviewData
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * System back within the login flow follows the predictive back gesture (`docs/DESIGN_SYSTEM.md` §5) and sends the
 * same [AuthEvent.Back] as before: once when the gesture completes, never when it is cancelled. While it runs, the
 * step back leads to comes in with the gesture's progress, and leaves again when it is cancelled.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class PredictiveBackTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<AuthEvent>()

    @Test
    fun `a completed back gesture goes back one step`() {
        show(AuthPreviewData.code)

        backGesture { dispatcher ->
            dispatcher.dispatchOnBackProgressed(backEvent(progress = HALF))
            dispatcher.onBackPressed()
        }

        assertEquals(listOf(AuthEvent.Back), events)
    }

    @Test
    fun `a cancelled back gesture stays on the step`() {
        show(AuthPreviewData.code)

        backGesture { dispatcher ->
            dispatcher.dispatchOnBackProgressed(backEvent(progress = HALF))
            dispatcher.dispatchOnBackCancelled()
        }

        assertEquals(emptyList<AuthEvent>(), events)
    }

    @Test
    fun `the step back leads to comes in with the gesture and leaves when it is cancelled`() {
        show(AuthPreviewData.code.copy(backStep = AuthPreviewData.phoneNumber(isChangingNumber = true)))
        val backTitle = composeRule.activity.getString(R.string.auth_phone_change_title)

        backGesture { dispatcher -> dispatcher.dispatchOnBackProgressed(backEvent(progress = HALF)) }
        val during = composeRule.onAllNodesWithText(backTitle).fetchSemanticsNodes().size
        backGesture(start = false) { dispatcher -> dispatcher.dispatchOnBackCancelled() }
        val after = composeRule.onAllNodesWithText(backTitle).fetchSemanticsNodes().size

        assertEquals(1, during)
        assertEquals(0, after)
        assertEquals(emptyList<AuthEvent>(), events)
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.auth_code_title)).assertExists()
    }

    @Test
    fun `back on the welcome screen is left to the app`() {
        show(AuthPreviewData.welcome)

        val handled = composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.hasEnabledCallbacks() }

        assertEquals(false, handled)
    }

    private fun show(state: AuthUiState) {
        composeRule.setContent {
            PingogramTheme(themeMode = ThemeMode.Light, dynamicColor = false) {
                AuthScreen(state = state, onEvent = { events += it })
            }
        }
        composeRule.waitForIdle()
    }

    private fun backGesture(
        start: Boolean = true,
        gesture: (OnBackPressedDispatcher) -> Unit,
    ) {
        composeRule.runOnUiThread {
            val dispatcher = composeRule.activity.onBackPressedDispatcher
            if (start) dispatcher.dispatchOnBackStarted(backEvent(progress = 0f))
            gesture(dispatcher)
        }
        composeRule.waitForIdle()
    }

    private fun backEvent(progress: Float) =
        BackEventCompat(touchX = 0f, touchY = 0f, progress = progress, swipeEdge = BackEventCompat.EDGE_LEFT)

    private companion object {
        const val HALF = 0.5f
    }
}
