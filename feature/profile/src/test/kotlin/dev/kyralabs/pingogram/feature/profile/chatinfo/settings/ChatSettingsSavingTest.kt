package dev.kyralabs.pingogram.feature.profile.chatinfo.settings

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.DpRect
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.profile.ui.COMPACT
import dev.kyralabs.pingogram.feature.profile.ui.ComponentActivityRegistration
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A group's settings page keeps still while a change is saved (`docs/plans/motion-expressive-plan.md` C-3, M-090):
 * the saving line shows over the top of the page, so no group moves when it comes and goes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ChatSettingsSavingTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var saving by mutableStateOf(false)

    @Test
    fun `saving a change moves no group`() {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                ChatSettingsScreen(
                    state = ChatSettingsPreviewData.supergroup.copy(isSaving = saving),
                    onEvent = {},
                    onBack = {},
                )
            }
        }
        val before = groupBounds()

        saving = true
        composeRule.waitForIdle()
        val during = groupBounds()
        val lines = composeRule.onAllNodesWithContentDescription(SAVING).fetchSemanticsNodes().size

        saving = false
        composeRule.waitForIdle()
        val after = groupBounds()

        assertEquals(1, lines)
        assertEquals(before, during)
        assertEquals(before, after)
    }

    private fun groupBounds(): DpRect = composeRule.onNodeWithText(REACTIONS).getUnclippedBoundsInRoot()

    private companion object {
        const val REACTIONS = "Reactions"
        const val SAVING = "Saving"
    }
}
