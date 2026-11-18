package dev.kyralabs.pingogram.feature.settings.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A page's master switch as a card ([SettingsHeroSwitch]): screen readers hear the whole card as one switch, its title
 * and summary in it, and a tap anywhere on it turns it on or off with the toggle haptics.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class SettingsHeroSwitchTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var checked by mutableStateOf(false)
    private val performedHaptics = mutableListOf<HapticFeedbackType>()
    private val haptics =
        object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                performedHaptics += hapticFeedbackType
            }
        }

    private fun setCard() {
        composeRule.setContent {
            PingogramTheme(themeMode = ThemeMode.Light, dynamicColor = false) {
                CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                    SettingsHeroSwitch(
                        title = TITLE,
                        summary = SUMMARY,
                        checked = checked,
                        onCheckedChange = { checked = it },
                    )
                }
            }
        }
    }

    private val card =
        hasText(TITLE) and hasText(SUMMARY) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)

    @Test
    fun `the whole card is one switch that a tap turns on and off`() {
        setCard()

        composeRule.onAllNodes(isToggleable()).assertCountEquals(1)
        composeRule.onNode(card).assertIsOff().performClick()
        composeRule.onNode(card).assertIsOn().performClick()

        composeRule.onNode(card).assertIsOff()
        assertEquals(listOf(HapticFeedbackType.ToggleOn, HapticFeedbackType.ToggleOff), performedHaptics)
    }

    @Test
    fun `the card is accessible`() {
        setCard()

        composeRule.assertAccessible()
    }

    private companion object {
        const val TITLE = "Use proxy"
        const val SUMMARY = "Connect to Telegram through a proxy server"
    }
}
