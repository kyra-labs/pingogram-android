package dev.kyralabs.pingogram.feature.auth.ui

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.auth.R
import dev.kyralabs.pingogram.feature.auth.ui.preview.AuthPreviewData
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The two-pane login steps of a phone in landscape (`docs/DESIGN_SYSTEM.md` §7): the primary action is in view without
 * scrolling, and the headline sits in a pane of its own at the start, beside the form.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = LANDSCAPE)
class AuthAdaptiveLayoutTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `the phone number step shows Next without scrolling`() {
        show(AuthPreviewData.phoneNumber)

        composeRule.onNodeWithText(string(R.string.auth_next)).assertIsDisplayed().assertFullyInWindow()
        composeRule.onNodeWithText(string(R.string.auth_qr_login)).assertIsDisplayed().assertFullyInWindow()
    }

    @Test
    fun `the code step shows Next without scrolling`() {
        show(AuthPreviewData.code)

        composeRule.onNodeWithText(string(R.string.auth_next)).assertIsDisplayed().assertFullyInWindow()
        composeRule.onNodeWithText(string(R.string.auth_change_number)).assertIsDisplayed().assertFullyInWindow()
    }

    @Test
    fun `the headline is in the start pane and Next at the end of the window`() {
        show(AuthPreviewData.phoneNumber)
        val width =
            composeRule
                .onRoot()
                .fetchSemanticsNode()
                .size.width

        val headline = composeRule.onNode(isHeading()).fetchSemanticsNode()
        val next = composeRule.onNodeWithText(string(R.string.auth_next)).fetchSemanticsNode()

        assertTrue(headline.positionInRoot.x + headline.size.width <= width / 2)
        assertTrue(next.positionInRoot.x >= width / 2)
    }

    private fun show(state: AuthUiState) {
        composeRule.setContent {
            PingogramTheme(themeMode = ThemeMode.Light, dynamicColor = false) {
                AuthScreen(state = state, onEvent = {})
            }
        }
        composeRule.waitForIdle()
    }

    /** The whole node lies within the window, not only the part a scrolling parent leaves visible. */
    private fun SemanticsNodeInteraction.assertFullyInWindow(): SemanticsNodeInteraction {
        val root = composeRule.onRoot().fetchSemanticsNode().size
        val node = fetchSemanticsNode()
        val top = node.positionInRoot.y
        val bottom = top + node.size.height
        assertTrue("top $top, bottom $bottom, window height ${root.height}", top >= 0f && bottom <= root.height)
        return this
    }

    private fun string(
        @StringRes id: Int,
    ): String = RuntimeEnvironment.getApplication().getString(id)
}
