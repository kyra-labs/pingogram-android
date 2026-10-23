package dev.kyralabs.pingogram.feature.chats.ui.stories

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chats.ui.COMPACT
import dev.kyralabs.pingogram.feature.chats.ui.ComponentActivityRegistration
import kotlinx.collections.immutable.ImmutableList
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The story rings row: a ring opens its chat's stories, one button for screen readers; more load near its end. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class StoryRingsTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val opened = mutableListOf<Long>()
    private var nearEnd = 0

    private fun setRings(rings: ImmutableList<StoryRing> = StoryRingsPreviewData.rings) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                StoryRingsRow(rings = rings, onOpen = { opened += it }, onNearEnd = { nearEnd++ })
            }
        }
    }

    @Test
    fun `a ring opens its chat's stories`() {
        setRings()

        composeRule.onNodeWithContentDescription("Ada Lovelace, 3 stories, not seen").performClick()

        assertEquals(listOf(2L), opened)
    }

    @Test
    fun `each ring is one button for screen readers, the user's own named as theirs`() {
        setRings()

        composeRule
            .onNodeWithContentDescription("Your story, 1 story, not seen")
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        composeRule.onNodeWithContentDescription("Pingogram News, 2 stories, seen").assertHasClickAction()
        composeRule.assertAccessible()
    }

    @Test
    fun `a row whose end is on screen asks for more chats once`() {
        setRings()
        composeRule.waitForIdle()

        assertEquals(1, nearEnd)
    }
}
