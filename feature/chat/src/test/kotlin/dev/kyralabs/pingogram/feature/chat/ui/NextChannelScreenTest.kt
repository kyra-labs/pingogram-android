package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.preview.NextChannelPreviewData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Pulling a read channel up past its newest post (`docs/DESIGN_SYSTEM.md` §6 "Next unread channel"): far enough it
 * opens the next unread channel with a haptic at the threshold, a short pull opens nothing, the indicator says what
 * letting go does, and screen readers open it through the list's custom action; without a next channel nothing pulls.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class NextChannelScreenTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<ChatEvent>()
    private val haptics = mutableListOf<HapticFeedbackType>()
    private val next = NextChannelPreviewData.next

    private fun string(
        id: Int,
        vararg args: Any,
    ) = RuntimeEnvironment.getApplication().getString(id, *args)

    private fun setScreen(nextChannel: NextChannel? = next) {
        val feedback =
            object : HapticFeedback {
                override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                    haptics += hapticFeedbackType
                }
            }
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalHapticFeedback provides feedback) {
                    ChatScreen(
                        state = ChatPreviewData.channel,
                        onEvent = { events += it },
                        onBack = {},
                        nextChannel = nextChannel,
                    )
                }
            }
        }
    }

    /** Drags the list up by [distance] dp in small steps, then lets go if [release]. */
    private fun pullUp(
        distance: Int,
        release: Boolean = true,
    ) = pullNextChannel(composeRule, distance, release)

    @Test
    fun `a long pull opens the next channel with a haptic at the threshold`() {
        setScreen()

        pullUp(LONG_PULL)

        assertEquals(
            listOf(ChatEvent.OpenNextChannel(next.chatId)),
            events.filterIsInstance<ChatEvent.OpenNextChannel>(),
        )
        assertEquals(listOf(HapticFeedbackType.TextHandleMove), haptics)
    }

    @Test
    fun `a short pull opens nothing`() {
        setScreen()

        pullUp(SHORT_PULL)

        assertTrue(events.filterIsInstance<ChatEvent.OpenNextChannel>().isEmpty())
        assertTrue(haptics.isEmpty())
    }

    @Test
    fun `the indicator says letting go opens the channel once past the threshold`() {
        setScreen()

        pullUp(SHORT_PULL, release = false)
        composeRule.onNodeWithText(string(R.string.chat_next_channel_pull), useUnmergedTree = true).assertExists()
        pullNextChannel(composeRule, LONG_PULL - SHORT_PULL, release = false, start = false)
        composeRule.onNodeWithText(string(R.string.chat_next_channel_release), useUnmergedTree = true).assertExists()
    }

    @Test
    fun `screen readers open the next channel through the list's custom action`() {
        setScreen()
        val label = string(R.string.chat_next_channel_action, next.title)

        val action =
            composeRule
                .onNodeWithTag(MESSAGE_LIST_TEST_TAG)
                .fetchSemanticsNode()
                .config[SemanticsActions.CustomActions]
                .single { it.label == label }
        composeRule.runOnIdle { action.action() }

        assertEquals(
            listOf(ChatEvent.OpenNextChannel(next.chatId)),
            events.filterIsInstance<ChatEvent.OpenNextChannel>(),
        )
    }

    @Test
    fun `without a next channel nothing pulls`() {
        setScreen(nextChannel = null)

        pullUp(LONG_PULL)

        assertTrue(events.filterIsInstance<ChatEvent.OpenNextChannel>().isEmpty())
        composeRule.onNodeWithText(next.title, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `the conversation with its pull stays accessible`() {
        setScreen()

        composeRule.assertAccessible()
    }

    private companion object {
        const val SHORT_PULL = 80
        const val LONG_PULL = 280
    }
}

/**
 * Drags the conversation's list up by [distance] dp in steps of 8 dp, one a frame, from its center ([start]) or on from
 * where the last drag left the finger, then lets go if [release].
 */
internal fun pullNextChannel(
    composeRule: ComposeTestRule,
    distance: Int,
    release: Boolean,
    start: Boolean = true,
) {
    composeRule.onNodeWithTag(MESSAGE_LIST_TEST_TAG).performTouchInput {
        if (start) down(center)
        val step = PULL_STEP.dp.toPx()
        repeat(distance / PULL_STEP) {
            moveBy(Offset(0f, -step))
            advanceEventTime(PULL_FRAME_MILLIS)
        }
        if (release) up()
    }
    composeRule.waitForIdle()
}

private const val PULL_STEP = 8
private const val PULL_FRAME_MILLIS = 16L
