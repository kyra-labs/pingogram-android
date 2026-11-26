package dev.kyralabs.pingogram.feature.settings.ui.mutedwords

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.settings.ui.COMPACT
import dev.kyralabs.pingogram.feature.settings.ui.ComponentActivityRegistration
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Muted words' page (FR-X21): the switch, "Add" and a word's chip send their events. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class MutedWordsScreenTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<MutedWordsEvent>()

    @Test
    fun `the switch turns muting off, Add adds the word typed, and a chip removes its word`() {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                MutedWordsScreen(MutedWordsPreviewStates.words, onEvent = events::add, onBack = {})
            }
        }
        composeRule.assertAccessible()

        composeRule.onNodeWithText("Collapse messages with muted words").performClick()
        composeRule.onNodeWithText("Add word").performTextInput("giveaway prize")
        composeRule.onNodeWithText("Add").performClick()
        composeRule.onNodeWithText("crypto").performScrollTo().performClick()

        assertEquals(
            listOf(
                MutedWordsEvent.SetEnabled(false),
                MutedWordsEvent.Add("giveaway prize"),
                MutedWordsEvent.Remove("crypto"),
            ),
            events,
        )
    }
}
