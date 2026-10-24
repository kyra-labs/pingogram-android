package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.ui.text.UiText
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A bubble's taps ([bubbleTaps]): a tap acts at once, a double tap reacts and leaves the time as it was. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class BubbleTapsTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<ChatEvent>()
    private var doubleTaps = 0

    private fun setBubble(
        toggleTime: Boolean,
        doubleTap: Boolean,
    ) {
        val onDoubleTap: (() -> Unit)? = if (doubleTap) ({ doubleTaps++ }) else null
        composeRule.setContent {
            Box(
                Modifier
                    .size(BUBBLE_SIZE)
                    .testTag(BUBBLE)
                    .bubbleTaps(MESSAGE, toggleTime, onDoubleTap) { events += it },
            )
        }
    }

    @Test
    fun `a tap shows the time at once, without waiting for a second one`() {
        setBubble(toggleTime = true, doubleTap = true)
        composeRule.mainClock.autoAdvance = false

        composeRule.onNodeWithTag(BUBBLE).performTouchInput { click() }
        composeRule.mainClock.advanceTimeByFrame()

        assertEquals(listOf(ChatEvent.ToggleTime(MESSAGE)), events)
        assertEquals(0, doubleTaps)
    }

    @Test
    fun `a double tap reacts once and leaves the time as it was`() {
        setBubble(toggleTime = true, doubleTap = true)

        composeRule.onNodeWithTag(BUBBLE).performTouchInput { doubleClick() }
        composeRule.waitForIdle()

        assertEquals(listOf(ChatEvent.ToggleTime(MESSAGE), ChatEvent.ToggleTime(MESSAGE)), events)
        assertEquals(1, doubleTaps)
    }

    @Test
    fun `a bubble that shows its time anyway only reacts to a double tap`() {
        setBubble(toggleTime = false, doubleTap = true)

        composeRule.onNodeWithTag(BUBBLE).performTouchInput { click() }
        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        composeRule.onNodeWithTag(BUBBLE).performTouchInput { doubleClick() }
        composeRule.waitForIdle()

        assertEquals(emptyList<ChatEvent>(), events)
        assertEquals(1, doubleTaps)
    }

    @Test
    fun `without the setting two quick taps toggle the time twice`() {
        setBubble(toggleTime = true, doubleTap = false)

        composeRule.onNodeWithTag(BUBBLE).performTouchInput { doubleClick() }
        composeRule.waitForIdle()

        assertEquals(listOf(ChatEvent.ToggleTime(MESSAGE), ChatEvent.ToggleTime(MESSAGE)), events)
    }

    @Test
    fun `a long press is no tap`() {
        setBubble(toggleTime = true, doubleTap = true)

        composeRule.onNodeWithTag(BUBBLE).performTouchInput { longClick() }
        composeRule.waitForIdle()

        assertEquals(emptyList<ChatEvent>(), events)
        assertEquals(0, doubleTaps)
    }

    @Test
    fun `the quick reaction goes to the message on the sides the settings allow`() {
        var incoming: (() -> Unit)? = null
        var outgoing: (() -> Unit)? = null
        var failed: (() -> Unit)? = null
        composeRule.setContent {
            val toggles = rememberReactionToggleState()
            CompositionLocalProvider(
                LocalDoubleTapReaction provides DoubleTapReaction(HEART, incoming = true, outgoing = false),
            ) {
                incoming = rememberDoubleTapReaction(bubble(isOutgoing = false), toggles) { events += it }
                outgoing = rememberDoubleTapReaction(bubble(isOutgoing = true), toggles) { events += it }
                failed =
                    rememberDoubleTapReaction(
                        bubble(isOutgoing = false).copy(status = OutgoingStatus.Failed),
                        toggles,
                    ) { events += it }
            }
        }

        composeRule.runOnIdle { incoming!!.invoke() }

        assertEquals(listOf(ChatEvent.ToggleReaction(MESSAGE, HEART)), events)
        assertNull(outgoing)
        assertNull(failed)
    }

    @Test
    fun `nothing reacts while no conversation provides the quick reaction`() {
        var onDoubleTap: (() -> Unit)? = {}
        composeRule.setContent {
            onDoubleTap = rememberDoubleTapReaction(bubble(isOutgoing = false), rememberReactionToggleState()) {}
        }
        composeRule.waitForIdle()

        assertNull(onDoubleTap)
    }

    private fun bubble(isOutgoing: Boolean): ChatItem.Bubble {
        val formatted = FormattedText("Hi", persistentListOf())
        return ChatItem.Bubble(
            id = MESSAGE,
            isOutgoing = isOutgoing,
            position = GroupPosition.Single,
            sender = UiText.Plain("Ada"),
            senderId = 2,
            showsSenderName = false,
            avatar = null,
            hasAvatarColumn = false,
            time = "9:41 AM",
            isEdited = false,
            status = if (isOutgoing) OutgoingStatus.Sent else null,
            forwardedFrom = null,
            reply = null,
            content = MessageContent.Text(formatted, linkPreview = null),
            contentLabel = null,
            text = formatted,
            spoilersRevealed = false,
            linkPreview = null,
            reactions = persistentListOf(),
            date = UiText.Plain("Today"),
        )
    }

    private companion object {
        const val BUBBLE = "bubble"
        const val MESSAGE = 7L
        val BUBBLE_SIZE = 120.dp
        val HEART = ReactionLabel.Emoji("❤")

        /** Longer than the double-tap timeout, so the taps around it are not one double tap. */
        const val SETTLE_MILLIS = 1_000L
    }
}
