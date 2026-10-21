package dev.kyralabs.pingogram.feature.chat.ui

import android.content.Context
import android.view.accessibility.AccessibilityManager
import android.widget.Magnifier
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.foundation.ComposeFoundationFlags
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

/**
 * The floating message menu and the swipe to reply (`docs/DESIGN_SYSTEM.md` §6 "Message actions"): a long press lifts
 * the message with its reactions and menu, unless TalkBack explores by touch or the window is short, when the sheet
 * opens; a second long press selects the lifted text; a swipe past its distance replies, a short one does not, in both
 * layout directions. The screen answers [ChatEvent.ShowMessageActions] with an actions sheet, as the view model does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
@OptIn(ExperimentalFoundationApi::class)
class MessageActionsMenuTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<ChatEvent>()

    private var state by mutableStateOf(
        ChatPreviewData.privateChat.copy(shortcuts = MessageShortcuts(canReply = true, canCopy = true)),
    )

    private val toolbar = RecordingTextToolbar()

    private var newContextMenu = false

    @Before
    fun useTextToolbar() {
        // The text toolbar, which the test can see, rather than the new context menu.
        newContextMenu = ComposeFoundationFlags.isNewContextMenuEnabled
        ComposeFoundationFlags.isNewContextMenuEnabled = false
    }

    @After
    fun restoreFlags() {
        ComposeFoundationFlags.isNewContextMenuEnabled = newContextMenu
    }

    private fun setScreen(layoutDirection: LayoutDirection = LayoutDirection.Ltr) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                CompositionLocalProvider(
                    LocalTextToolbar provides toolbar,
                    LocalLayoutDirection provides layoutDirection,
                ) {
                    ChatScreen(state = state, onEvent = ::onEvent, onBack = {})
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun onEvent(event: ChatEvent) {
        events += event
        state =
            when (event) {
                is ChatEvent.ShowMessageActions -> {
                    state.copy(
                        messageActions =
                            ChatPreviewData.ownMessageActions.copy(
                                messageId = event.messageId,
                                reactionsMessageId = event.messageId,
                            ),
                    )
                }

                ChatEvent.DismissMessageActions, is ChatEvent.Copy, is ChatEvent.ToggleReaction -> {
                    state.copy(messageActions = null)
                }

                else -> {
                    state
                }
            }
    }

    @Test
    fun `a long press lifts the message with its reactions and menu`() {
        setScreen()

        composeRule.onNodeWithContentDescription(ADA_LAST).performTouchInput { longClick() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(LIFTED_MESSAGE_TAG).assertExists()
        composeRule.onNodeWithContentDescription(string(R.string.message_actions_close)).assertExists()
        composeRule.onNodeWithContentDescription(string(R.string.message_reaction_add, "👍")).assertExists()
        composeRule.onNodeWithText(string(R.string.message_action_reply)).assertExists()
    }

    @Test
    fun `an action of the menu closes it and goes to the message`() {
        setScreen()
        composeRule.onNodeWithContentDescription(ADA_LAST).performTouchInput { longClick() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(string(R.string.message_action_copy)).performClick()
        composeRule.waitForIdle()

        assertEquals(ChatEvent.Copy(11), events.last())
        composeRule.onNodeWithTag(LIFTED_MESSAGE_TAG).assertDoesNotExist()
    }

    @Test
    fun `a tap beside the menu closes it`() {
        setScreen()
        composeRule.onNodeWithContentDescription(ADA_LAST).performTouchInput { longClick() }
        composeRule.waitForIdle()

        // Near the top of the scrim, far from the lifted message near the bottom.
        composeRule.onNodeWithContentDescription(string(R.string.message_actions_close)).performTouchInput {
            click(Offset(centerX, height * SCRIM_TAP_Y))
        }
        composeRule.waitForIdle()

        assertEquals(ChatEvent.DismissMessageActions, events.last())
        composeRule.onNodeWithTag(LIFTED_MESSAGE_TAG).assertDoesNotExist()
    }

    @Test
    fun `with TalkBack exploring by touch, a long press opens the sheet instead`() {
        val accessibility =
            RuntimeEnvironment.getApplication().getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        shadowOf(accessibility).setEnabled(true)
        shadowOf(accessibility).setTouchExplorationEnabled(true)
        setScreen()

        composeRule.onNodeWithContentDescription(ADA_LAST).performTouchInput { longClick() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(LIFTED_MESSAGE_TAG).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.message_action_reply)).assertExists()
    }

    @Test
    @Config(qualifiers = SHORT_WINDOW)
    fun `in a short window, a long press opens the sheet instead`() {
        setScreen()

        composeRule.onNodeWithContentDescription(ADA_LAST).performTouchInput { longClick() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(LIFTED_MESSAGE_TAG).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.message_action_reply)).assertExists()
    }

    @Test
    // Selecting shows the platform magnifier, whose window Robolectric cannot draw.
    @Config(shadows = [NoMagnifier::class])
    fun `a second long press selects the lifted message's text`() {
        setScreen()
        composeRule.onNodeWithContentDescription(ADA_LAST).performTouchInput { longClick() }
        composeRule.waitForIdle()

        composeRule
            .onNode(hasText("See you there!") and hasAnyAncestor(hasTestTag(LIFTED_MESSAGE_TAG)))
            .performTouchInput { longClick() }
        composeRule.waitForIdle()

        assertEquals(TextToolbarStatus.Shown, toolbar.status)
        assertTrue("Copy offered", toolbar.canCopy)
    }

    @Test
    fun `a swipe toward the end past its distance replies`() {
        setScreen()

        composeRule.onNodeWithContentDescription(ADA_LAST).performTouchInput {
            swipeRight(startX = left + SWIPE_START.toPx(), endX = left + (SWIPE_START + LONG_SWIPE).toPx())
        }
        composeRule.waitForIdle()

        assertEquals(listOf(ChatEvent.Reply(11)), events.filterIsInstance<ChatEvent.Reply>())
        assertEquals(emptyList<ChatEvent>(), events.filterIsInstance<ChatEvent.ShowMessageActions>())
    }

    @Test
    fun `a short swipe does not reply`() {
        setScreen()

        composeRule.onNodeWithContentDescription(ADA_LAST).performTouchInput {
            swipeRight(startX = left + SWIPE_START.toPx(), endX = left + (SWIPE_START + SHORT_SWIPE).toPx())
        }
        composeRule.waitForIdle()

        assertEquals(emptyList<ChatEvent>(), events.filterIsInstance<ChatEvent.Reply>())
    }

    @Test
    fun `right to left, a swipe toward the end goes left`() {
        setScreen(LayoutDirection.Rtl)

        composeRule.onNodeWithContentDescription(ADA_LAST).performTouchInput {
            swipeLeft(startX = right - SWIPE_START.toPx(), endX = right - (SWIPE_START + LONG_SWIPE).toPx())
        }
        composeRule.waitForIdle()

        assertEquals(listOf(ChatEvent.Reply(11)), events.filterIsInstance<ChatEvent.Reply>())
    }

    @Test
    fun `a message being sent cannot be swiped to reply`() {
        setScreen()

        composeRule.onNodeWithContentDescription(PENDING, substring = true).performTouchInput {
            swipeRight(startX = left + SWIPE_START.toPx(), endX = left + (SWIPE_START + LONG_SWIPE).toPx())
        }
        composeRule.waitForIdle()

        assertEquals(emptyList<ChatEvent>(), events.filterIsInstance<ChatEvent.Reply>())
    }

    private fun string(
        @StringRes id: Int,
        vararg args: Any,
    ): String = RuntimeEnvironment.getApplication().getString(id, *args)

    /** A text toolbar that remembers whether it shows, and whether it offers to copy. */
    private class RecordingTextToolbar : TextToolbar {
        override var status = TextToolbarStatus.Hidden
            private set
        var canCopy = false
            private set

        override fun showMenu(
            rect: Rect,
            onCopyRequested: (() -> Unit)?,
            onPasteRequested: (() -> Unit)?,
            onCutRequested: (() -> Unit)?,
            onSelectAllRequested: (() -> Unit)?,
        ) {
            status = TextToolbarStatus.Shown
            canCopy = onCopyRequested != null
        }

        override fun hide() {
            status = TextToolbarStatus.Hidden
        }
    }

    private companion object {
        /** Ada's newest message in [ChatPreviewData.privateChat], as screen readers hear it. */
        const val ADA_LAST = "Ada, 9:46 AM: See you there!"

        /** The message being sent in [ChatPreviewData.privateChat]. */
        const val PENDING = "You, 9:41 AM: Sending the slides now"

        /** A phone in landscape: shorter than the floating menu needs. */
        const val SHORT_WINDOW = "w891dp-h411dp-xxhdpi"

        /** How far down the screen a tap on the scrim lands, as a fraction of its height. */
        const val SCRIM_TAP_Y = 0.1f

        /** Where swipes start from the row's start edge, and how far they go. */
        val SWIPE_START = 24.dp
        val LONG_SWIPE = 160.dp
        val SHORT_SWIPE = 32.dp
    }
}

/** The platform magnifier, doing nothing: Robolectric has no surface for its window. */
@Implements(Magnifier::class)
@Suppress("UnusedParameter") // The signatures of the methods it stands in for.
class NoMagnifier {
    @Implementation
    fun show(
        sourceCenterX: Float,
        sourceCenterY: Float,
    ) = Unit

    @Implementation
    fun show(
        sourceCenterX: Float,
        sourceCenterY: Float,
        magnifierCenterX: Float,
        magnifierCenterY: Float,
    ) = Unit

    @Implementation
    fun update() = Unit

    @Implementation
    fun dismiss() = Unit
}
