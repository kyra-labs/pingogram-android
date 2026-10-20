package dev.kyralabs.pingogram.feature.auth.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
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
 * The country is a read-only text field (`docs/DESIGN_SYSTEM.md` §6): a tap, or a screen reader's click, opens the
 * country picker; nothing can be typed into it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class CountryFieldTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<AuthEvent>()

    @Test
    fun `a tap opens the country picker`() {
        show()

        composeRule.onNodeWithText(AuthPreviewData.unitedKingdom.name).performClick()

        assertEquals(listOf(AuthEvent.OpenCountryPicker), events)
    }

    @Test
    fun `screen readers open the country picker with a click`() {
        show()

        composeRule
            .onNodeWithText(AuthPreviewData.unitedKingdom.name)
            .assertHasClickAction()
            .performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(listOf(AuthEvent.OpenCountryPicker), events)
    }

    private fun show() {
        composeRule.setContent {
            PingogramTheme(themeMode = ThemeMode.Light, dynamicColor = false) {
                AuthScreen(state = AuthPreviewData.phoneNumber, onEvent = { events += it })
            }
        }
        composeRule.waitForIdle()
    }
}
