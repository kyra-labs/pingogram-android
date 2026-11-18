package dev.kyralabs.pingogram.feature.chats.ui.feed

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.feature.chats.R
import dev.kyralabs.pingogram.feature.chats.ui.awaitLongPress
import dev.kyralabs.pingogram.feature.chats.ui.consumeUntilUp
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * A feed card with its menu (`docs/DESIGN_SYSTEM.md` §6 "Channel feed", frame 6): a long press (the `LongPress`
 * haptic) or a right click lifts the card, its shadow and scale rising on the spatial spring, and opens an expressive
 * menu below it: Open in channel, Forward, Copy link, Bookmark, and "Mark as read" while the post is unread or "Mark
 * channel as unread" once read. Screen readers get the same items as the card's custom actions. A press that stops
 * the list's fling only stops it, as the folder tabs' does.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FeedPostWithMenu(
    post: FeedPost,
    onEvent: (FeedEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val actions = post.menuActions()
    val labels = actions.map { stringResource(it.label) }
    val currentOnEvent by rememberUpdatedState(onEvent)
    val lift =
        animateFloatAsState(
            if (open) 1f else 0f,
            MaterialTheme.motionScheme.defaultSpatialSpec(),
            label = "feedPostLift",
        )
    val shape = MaterialTheme.shapes.extraLarge
    Box(modifier) {
        FeedPostCard(
            post = post,
            onOpen = { onEvent(FeedEvent.Open(post.chatId, post.messageIds.first())) },
            modifier =
                Modifier
                    .graphicsLayer {
                        val raised = lift.value
                        shadowElevation = raised * MenuDefaults.ShadowElevation.toPx()
                        scaleX = 1f + raised * (LIFT_SCALE - 1f)
                        scaleY = scaleX
                        this.shape = shape
                    }.opensMenuOnLongPress(LocalHapticFeedback.current) { open = true }
                    .semantics {
                        customActions =
                            actions.mapIndexed { index, action ->
                                CustomAccessibilityAction(labels[index]) {
                                    currentOnEvent(FeedEvent.Act(post.key, action))
                                    true
                                }
                            }
                    },
        )
        DropdownMenuPopup(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 0, count = 1)) {
                actions.forEachIndexed { index, action ->
                    DropdownMenuItem(
                        onClick = {
                            open = false
                            onEvent(FeedEvent.Act(post.key, action))
                        },
                        text = { Text(labels[index]) },
                        shape = MenuDefaults.itemShape(index, actions.size).shape,
                        leadingIcon = { Icon(painterResource(action.icon), contentDescription = null) },
                    )
                }
            }
        }
    }
}

/** What [this] post's menu offers, in order. */
private fun FeedPost.menuActions(): List<FeedPostAction> =
    listOf(
        FeedPostAction.Open,
        FeedPostAction.Forward,
        FeedPostAction.CopyLink,
        FeedPostAction.Bookmark,
        if (isUnread) FeedPostAction.MarkAsRead else FeedPostAction.MarkChannelUnread,
    )

@get:StringRes
private val FeedPostAction.label: Int
    get() =
        when (this) {
            FeedPostAction.Open -> R.string.feed_open_in_channel
            FeedPostAction.Forward -> R.string.feed_action_forward
            FeedPostAction.CopyLink -> R.string.feed_action_copy_link
            FeedPostAction.Bookmark -> R.string.feed_action_bookmark
            FeedPostAction.MarkAsRead -> R.string.feed_action_mark_read
            FeedPostAction.MarkChannelUnread -> R.string.feed_action_mark_channel_unread
        }

@get:DrawableRes
private val FeedPostAction.icon: Int
    get() =
        when (this) {
            FeedPostAction.Open -> R.drawable.ic_open_in_new
            FeedPostAction.Forward -> UiR.drawable.ic_forward
            FeedPostAction.CopyLink -> UiR.drawable.ic_link
            FeedPostAction.Bookmark -> R.drawable.ic_bookmark
            FeedPostAction.MarkAsRead -> UiR.drawable.ic_mark_chat_read
            FeedPostAction.MarkChannelUnread -> UiR.drawable.ic_mark_chat_unread
        }

/**
 * Calls [onOpen] once the pointer has stayed down within the touch slop for the long-press timeout (with the
 * `LongPress` haptic), or at once for a mouse's secondary button, then consumes the rest of the gesture so the card
 * takes no tap. It watches the initial pass, and a press the list's scrolling already consumed (one that stops a
 * fling) opens nothing.
 */
private fun Modifier.opensMenuOnLongPress(
    haptics: HapticFeedback,
    onOpen: () -> Unit,
): Modifier =
    pointerInput(haptics) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (down.isConsumed) return@awaitEachGesture
            if (currentEvent.buttons.isSecondaryPressed) {
                down.consume()
                onOpen()
                consumeUntilUp()
            } else if (awaitLongPress(down)) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onOpen()
                consumeUntilUp()
            }
        }
    }

/** How much a lifted card grows. */
private const val LIFT_SCALE = 1.02f
