package dev.kyralabs.pingogram.feature.auth.ui

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.auth.R
import dev.kyralabs.pingogram.feature.auth.ui.preview.AuthPreviewData
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import dev.kyralabs.pingogram.core.ui.R as CoreUiR

/** The password fields' visibility toggle (FR-A4) and their accessibility (`docs/DESIGN_SYSTEM.md` §9). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class PasswordVisibilityTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `the password is masked until the toggle shows it, and hidden again`() {
        show(AuthPreviewData.password)

        val masked = passwordField().editableText()
        toggle().performClick()
        val shown = passwordField().editableText()
        toggle().performClick()

        assertEquals("secret".map { MASK }.joinToString(""), masked)
        assertEquals("secret", shown)
        assertEquals(masked, passwordField().editableText())
    }

    @Test
    fun `the toggle is a labelled checkbox with a 48 dp target`() {
        show(AuthPreviewData.password)

        toggle()
            .assertContentDescriptionEquals(string(R.string.auth_password_show))
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
            .assertTouchWidthIsEqualTo(MIN_TOUCH_TARGET)
            .assertTouchHeightIsEqualTo(MIN_TOUCH_TARGET)
            .assertIsOff()
            .performClick()
            .assertIsOn()
    }

    @Test
    fun `each new password field has its own toggle`() {
        show(AuthPreviewData.newPassword)

        val toggles = composeRule.onAllNodesWithContentDescription(string(R.string.auth_password_show))
        toggles[1].performClick()

        assertEquals(2, toggles.fetchSemanticsNodes().size)
        toggles[0].assertIsOff()
        toggles[1].assertIsOn()
    }

    @Test
    fun `a wrong password is reported as the field's error, and the title is a heading`() {
        show(AuthPreviewData.passwordError)

        passwordField().assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Error, string(CoreUiR.string.error_password_invalid)),
        )
        composeRule.onNode(isHeading()).assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.Text,
                listOf(AnnotatedString(string(R.string.auth_password_title))),
            ),
        )
    }

    private fun show(state: AuthUiState) {
        composeRule.setContent {
            PingogramTheme(themeMode = ThemeMode.Light, dynamicColor = false) {
                AuthScreen(state = state, onEvent = {})
            }
        }
        composeRule.waitForIdle()
    }

    private fun passwordField(): SemanticsNodeInteraction = composeRule.onNode(hasSetTextAction())

    private fun toggle(): SemanticsNodeInteraction =
        composeRule.onNodeWithContentDescription(string(R.string.auth_password_show))

    private fun SemanticsNodeInteraction.editableText(): String =
        fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    private fun string(
        @StringRes id: Int,
    ): String = RuntimeEnvironment.getApplication().getString(id)

    private companion object {
        val MIN_TOUCH_TARGET = 48.dp

        /** PasswordVisualTransformation's default mask. */
        const val MASK = '\u2022'
    }
}
