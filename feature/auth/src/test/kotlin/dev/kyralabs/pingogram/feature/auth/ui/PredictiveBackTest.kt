package dev.kyralabs.pingogram.feature.auth.ui

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedDispatcher
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.auth.ui.preview.AuthPreviewData
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * System back within the login flow follows the predictive back gesture (`docs/DESIGN_SYSTEM.md` §5) and sends the
 * same [AuthEvent.Back] as before: once when the gesture completes, never when it is cancelled.
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

    private fun backGesture(gesture: (OnBackPressedDispatcher) -> Unit) {
        composeRule.runOnUiThread {
            val dispatcher = composeRule.activity.onBackPressedDispatcher
            dispatcher.dispatchOnBackStarted(backEvent(progress = 0f))
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
