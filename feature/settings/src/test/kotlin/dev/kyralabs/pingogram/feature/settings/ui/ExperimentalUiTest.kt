package dev.kyralabs.pingogram.feature.settings.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.core.ui.component.LocalExperimentalUi
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings home's "Experimental UI" (`docs/adr/0035-experimental-ui.md`): a switch row in the standard look, the labs
 * card in the experimental one, each one toggleable for screen readers, and nothing until the setting is read.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ExperimentalUiTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val changes = mutableListOf<Boolean>()

    private fun setScreen(
        experimentalUi: Boolean?,
        experimentalLook: Boolean = false,
    ) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalExperimentalUi provides experimentalLook) {
                    SettingsScreen(
                        state = SettingsPreviewData.content,
                        onEvent = {},
                        onOpenPage = {},
                        experimentalUi = experimentalUi,
                        onExperimentalUiChange = { changes += it },
                    )
                }
            }
        }
    }

    private val isSwitch = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)

    @Test
    fun `nothing shows until the setting is read`() {
        setScreen(experimentalUi = null)

        composeRule.onAllNodesWithTag(EXPERIMENTAL_UI_TAG).assertCountEquals(0)
    }

    @Test
    fun `the standard look shows a switch row that turns the experimental look on`() {
        setScreen(experimentalUi = false)

        composeRule
            .onNodeWithTag(EXPERIMENTAL_UI_TAG)
            .performScrollTo()
            .assertIsOff()
            .assert(isSwitch)
            .performClick()

        assertEquals(listOf(true), changes)
    }

    @Test
    fun `the experimental look shows the labs card, one switch, which turns it off`() {
        setScreen(experimentalUi = true, experimentalLook = true)

        composeRule
            .onNodeWithTag(EXPERIMENTAL_UI_TAG)
            .assertIsOn()
            .assert(isSwitch)
            .performClick()

        assertEquals(listOf(false), changes)
        // One toggleable for the whole card: its switch takes no input of its own.
        composeRule.onAllNodes(isToggleable()).assertCountEquals(1)
    }

    @Test
    fun `the labs card is accessible`() {
        setScreen(experimentalUi = true, experimentalLook = true)

        composeRule.assertAccessible()
    }

    @Test
    fun `the row is accessible`() {
        setScreen(experimentalUi = false)
        composeRule.onNodeWithTag(EXPERIMENTAL_UI_TAG).performScrollTo()

        composeRule.assertAccessible()
    }
}
