package dev.kyralabs.pingogram.feature.chats.ui.catchup

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.LayoutDirection
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chats.ui.COMPACT
import dev.kyralabs.pingogram.feature.chats.ui.ComponentActivityRegistration
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Swiping a card (owner decision 2026-11-15): toward the end marks the chat as read, toward the start skips it,
 * mirrored right to left; a short swipe does neither, and screen readers do both through the card's custom actions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class CatchUpSwipeTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<CatchUpEvent>()

    private val card get() = composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.PaneTitle))

    private fun setScreen(direction: LayoutDirection = LayoutDirection.Ltr) {
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                PingogramTheme(dynamicColor = false) {
                    CatchUpScreen(CatchUpPreviewData.cards, onEvent = { events += it }, onBack = {})
                }
            }
        }
    }

    @Test
    fun `swiping right marks the chat as read`() {
        setScreen()

        card.performTouchInput { swipeRight() }
        composeRule.waitForIdle()

        assertEquals(listOf<CatchUpEvent>(CatchUpEvent.MarkAsRead), events)
    }

    @Test
    fun `swiping left skips the chat`() {
        setScreen()

        card.performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        assertEquals(listOf<CatchUpEvent>(CatchUpEvent.Skip), events)
    }

    @Test
    fun `right to left, swiping left marks the chat as read`() {
        setScreen(LayoutDirection.Rtl)

        card.performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        assertEquals(listOf<CatchUpEvent>(CatchUpEvent.MarkAsRead), events)
    }

    @Test
    fun `a short swipe springs back and does nothing`() {
        setScreen()

        card.performTouchInput { swipeRight(startX = centerX, endX = centerX + width / 10f, durationMillis = 600) }
        composeRule.waitForIdle()

        assertEquals(emptyList<CatchUpEvent>(), events)
    }

    @Test
    fun `screen readers mark as read and skip through the card's actions`() {
        setScreen()
        val actions = card.fetchSemanticsNode().config[SemanticsActions.CustomActions]

        actions.forEach { it.action() }

        assertEquals(listOf("Mark as read", "Skip"), actions.map { it.label })
        assertEquals(listOf(CatchUpEvent.MarkAsRead, CatchUpEvent.Skip), events)
    }
}
