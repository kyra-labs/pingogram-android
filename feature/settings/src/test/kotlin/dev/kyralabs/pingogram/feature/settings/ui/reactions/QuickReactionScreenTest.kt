package dev.kyralabs.pingogram.feature.settings.ui.reactions

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.kyralabs.pingogram.core.domain.model.DoubleTapAction
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

/** The double-tap page's choices (`REQUIREMENTS.md` FR-X15): each side's React, Reply and Nothing send their choice. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class QuickReactionScreenTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<QuickReactionEvent>()

    @Test
    fun `each side's buttons choose what a double tap does there`() {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                QuickReactionScreen(QuickReactionPreviewStates.content, onEvent = events::add, onBack = {})
            }
        }
        composeRule.assertAccessible()

        // The first row of choices is for others' messages, the second for the user's own.
        composeRule.onAllNodesWithText("Nothing")[0].performScrollTo().performClick()
        composeRule.onAllNodesWithText("React")[1].performScrollTo().performClick()

        assertEquals(
            listOf(
                QuickReactionEvent.SetIncoming(DoubleTapAction.Nothing),
                QuickReactionEvent.SetOutgoing(DoubleTapAction.React),
            ),
            events,
        )
    }
}
