package dev.kyralabs.pingogram.feature.settings.ui.account

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.DpRect
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.ui.COMPACT
import dev.kyralabs.pingogram.feature.settings.ui.ComponentActivityRegistration
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The end of a step keeps its buttons still (`docs/DESIGN_SYSTEM.md` §5.1, `docs/plans/motion-expressive-plan.md`
 * C-1): the progress line's place is kept while nothing is on its way, so the button just tapped stays where it was
 * before, during and after Telegram answers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class StepActionsTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var working by mutableStateOf(false)

    private data class Step(
        override val isWorking: Boolean,
        override val floodWaitSeconds: Long? = null,
        override val floodWaitTotalSeconds: Long = 0,
    ) : FlowStepState

    @Test
    fun `the primary button stays where it is before, during and after a request`() {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
                    StepActions(state = Step(isWorking = working), primary = NEXT, onPrimary = {}, secondary = BACK)
                }
            }
        }
        val before = nextBounds()

        working = true
        composeRule.waitForIdle()
        val during = nextBounds()
        composeRule.onNodeWithContentDescription(BUSY_PREFIX, substring = true).assertExists()

        working = false
        composeRule.waitForIdle()
        val after = nextBounds()

        assertEquals(before, during)
        assertEquals(before, after)
    }

    @Test
    fun `the progress line is not heard while nothing is on its way`() {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                StepActions(state = Step(isWorking = false), primary = NEXT, onPrimary = {})
            }
        }

        composeRule.onNodeWithContentDescription(BUSY_PREFIX, substring = true).assertDoesNotExist()
    }

    private fun nextBounds(): DpRect = composeRule.onNodeWithText(NEXT).getUnclippedBoundsInRoot()

    private companion object {
        const val NEXT = "Next"
        const val BACK = "Back"
        const val BUSY_PREFIX = "Waiting for Telegram"
    }
}
