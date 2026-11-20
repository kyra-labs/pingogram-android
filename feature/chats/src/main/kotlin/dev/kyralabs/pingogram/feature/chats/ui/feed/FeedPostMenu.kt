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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableSet
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * A feed card with its menu (`docs/DESIGN_SYSTEM.md` §6 "Channel feed", frame 6): a long press (the `LongPress`
 * haptic), a right click, the card's More button or the Menu key (Shift+F10) lifts the card, its shadow and scale
 * rising on the spatial spring, shows the post's [reactions] above it once they are read ([FeedReactionBar]), and
 * opens an expressive menu below it: Open in channel, Forward, Copy link, Bookmark, and "Mark as read" while the post
 * is unread or "Mark channel as unread" once read. Screen readers get the same items as the card's custom actions. A
 * press that stops the list's fling only stops it, as the folder tabs' does.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FeedPostWithMenu(
    post: FeedPost,
    onEvent: (FeedEvent) -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    reactions: ImmutableList<String>? = null,
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
    val openMenu = {
        open = true
        currentOnEvent(FeedEvent.ShowReactions(post.key))
    }
    Box(modifier) {
        FeedPostCard(
            post = post,
            selected = selected,
            onOpen = { onEvent(FeedEvent.Open(post.chatId, post.messageIds.first())) },
            onMore = openMenu,
            modifier =
                Modifier
                    .graphicsLayer {
                        val raised = lift.value
                        shadowElevation = raised * MenuDefaults.ShadowElevation.toPx()
                        scaleX = 1f + raised * (LIFT_SCALE - 1f)
                        scaleY = scaleX
                        this.shape = shape
                    }.opensMenuOnLongPress(LocalHapticFeedback.current, openMenu)
                    .opensMenuOnKey(openMenu)
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
        if (open && !reactions.isNullOrEmpty()) {
            PostReactions(post, reactions, onDismiss = { open = false }) { emoji ->
                onEvent(FeedEvent.React(post.key, emoji))
            }
        }
        PostMenu(
            open,
            actions,
            labels,
            onDismiss = { open = false },
        ) { action -> onEvent(FeedEvent.Act(post.key, action)) }
    }
}

/** The [reactions] bar above [post]'s card, the user's own chosen; a tap closes it ([onDismiss]) and reacts. */
@Composable
private fun PostReactions(
    post: FeedPost,
    reactions: ImmutableList<String>,
    onDismiss: () -> Unit,
    onReact: (String) -> Unit,
) {
    val chosen =
        post.reactions
            .filter { it.isChosen }
            .mapNotNull { it.emoji }
            .toImmutableSet()
    FeedReactionBar(
        emojis = reactions,
        chosen = chosen,
        onReact = { emoji ->
            onDismiss()
            onReact(emoji)
        },
        onDismiss = onDismiss,
    )
}

/** The menu below the card while [open]: one group of [actions], labelled [labels]; an item closes it ([onDismiss]). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PostMenu(
    open: Boolean,
    actions: List<FeedPostAction>,
    labels: List<String>,
    onDismiss: () -> Unit,
    onAct: (FeedPostAction) -> Unit,
) {
    DropdownMenuPopup(expanded = open, onDismissRequest = onDismiss) {
        DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 0, count = 1)) {
            actions.forEachIndexed { index, action ->
                DropdownMenuItem(
                    onClick = {
                        onDismiss()
                        onAct(action)
                    },
                    text = { Text(labels[index]) },
                    shape = MenuDefaults.itemShape(index, actions.size).shape,
                    leadingIcon = { Icon(painterResource(action.icon), contentDescription = null) },
                )
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

/**
 * Calls [onOpen] when the Menu key, or Shift+F10, is released on the card or a button in it, as a message's actions
 * open from the keyboard (`docs/DESIGN_SYSTEM.md` §9 "Keyboard & mouse"); on release, so it does not reach the menu.
 */
private fun Modifier.opensMenuOnKey(onOpen: () -> Unit): Modifier =
    onPreviewKeyEvent { event ->
        val isMenuKey = event.key == Key.Menu || (event.key == Key.F10 && event.isShiftPressed)
        if (isMenuKey && event.type == KeyEventType.KeyUp) onOpen()
        isMenuKey
    }

/** How much a lifted card grows. */
private const val LIFT_SCALE = 1.02f
