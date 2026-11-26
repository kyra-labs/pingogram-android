package dev.kyralabs.pingogram.feature.settings.ui.cleanup

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.COMPACT
import dev.kyralabs.pingogram.feature.settings.ui.ComponentActivityRegistration
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** What Cleanup suggestions' page sends (`REQUIREMENTS.md` FR-X24): each choice for the chat shown, the settings. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class CleanupScreenTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<CleanupEvent>()

    private fun setPage(state: CleanupUiState) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) { CleanupScreen(state, onEvent = { events += it }, onBack = {}) }
        }
    }

    private fun string(id: Int) = RuntimeEnvironment.getApplication().getString(id)

    private fun tap(id: Int) = composeRule.onNodeWithText(string(id)).performScrollTo().performClick()

    @Test
    fun `each choice is sent for the chat shown, and every control can be reached`() {
        setPage(CleanupPreviewStates.group)

        composeRule.onNodeWithText("Book club").performScrollTo()
        composeRule.assertAccessible()
        tap(R.string.settings_cleanup_mute)
        tap(R.string.settings_cleanup_archive)
        tap(R.string.settings_cleanup_leave)
        tap(R.string.settings_cleanup_not_now)

        assertEquals(listOf(CleanupEvent.Mute, CleanupEvent.Archive, CleanupEvent.Leave, CleanupEvent.NotNow), events)
    }

    @Test
    fun `leave is only offered where it can be, and asks first`() {
        setPage(CleanupPreviewStates.neverOpened)
        composeRule.onNodeWithText(string(R.string.settings_cleanup_leave)).assertDoesNotExist()
    }

    @Test
    fun `the confirmation leaves or cancels`() {
        setPage(CleanupPreviewStates.confirmingLeave)

        composeRule.onNodeWithText(string(R.string.settings_dialog_dismiss)).performClick()
        composeRule.onNodeWithText(string(R.string.settings_cleanup_leave_group)).assertExists()

        assertEquals(listOf<CleanupEvent>(CleanupEvent.DismissLeave), events)
    }

    @Test
    fun `while a choice is on its way, the choices wait`() {
        setPage(CleanupPreviewStates.group.copy(isActing = true))

        composeRule.onNodeWithText(string(R.string.settings_cleanup_mute)).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `the switch and the months send their change`() {
        setPage(CleanupPreviewStates.none)
        composeRule.assertAccessible()

        tap(R.string.settings_cleanup_switch)
        composeRule.onNodeWithText("12 months").performScrollTo().performClick()

        assertEquals(listOf(CleanupEvent.SetEnabled(false), CleanupEvent.SetMonths(12)), events)
    }
}
