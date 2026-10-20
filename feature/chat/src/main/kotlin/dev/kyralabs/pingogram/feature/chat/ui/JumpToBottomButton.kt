package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.domain.model.SendOptions
import dev.kyralabs.pingogram.feature.chat.R
import kotlinx.coroutines.launch
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The jump to the newest message (`docs/DESIGN_SYSTEM.md` §6): a `FloatingActionButton` (56 dp; Material 3 Expressive
 * no longer recommends the small FAB) in `secondaryContainer` / `onSecondaryContainer`, Material's secondary FAB style,
 * as it is not the screen's primary action. The unread count shows in a [Badge] on its corner, and screen readers hear
 * it with the label. It grows in and out on the motion scheme's fast spatial spring.
 */
@Composable
internal fun JumpToBottomButton(
    visible: Boolean,
    unreadCount: Int,
    motionScheme: MotionScheme,
    onClick: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(motionScheme.fastSpatialSpec()) + fadeIn(motionScheme.fastEffectsSpec()),
        exit = scaleOut(motionScheme.fastSpatialSpec()) + fadeOut(motionScheme.fastEffectsSpec()),
    ) {
        val unread =
            if (unreadCount > 0) pluralStringResource(R.plurals.chat_unread_count, unreadCount, unreadCount) else null
        val label = stringResource(R.string.chat_jump_to_bottom)
        BadgedBox(
            badge = {
                if (unreadCount > 0) {
                    Badge { Text(unreadCountText(unreadCount)) }
                }
            },
        ) {
            FloatingActionButton(
                onClick = onClick,
                modifier = Modifier.semantics { contentDescription = listOfNotNull(label, unread).joinToString(", ") },
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Icon(painter = painterResource(UiR.drawable.ic_keyboard_arrow_down), contentDescription = null)
            }
        }
    }
}

/**
 * Shows the chat's newest message: scrolls [listState] to it ([rememberScrollToNewest]) when the items of [content]
 * reach it ([ChatContent.Content.reachedEnd]), else asks for the newest messages ([ChatEvent.ShowNewest]), which come
 * with their anchor.
 */
@Composable
internal fun rememberJumpToNewest(
    listState: LazyListState,
    content: ChatContent,
    onEvent: (ChatEvent) -> Unit,
): () -> Unit {
    val scrollToNewest = rememberScrollToNewest(listState)
    val currentReachedEnd by rememberUpdatedState((content as? ChatContent.Content)?.reachedEnd != false)
    val currentOnEvent by rememberUpdatedState(onEvent)
    return remember(scrollToNewest) {
        { if (currentReachedEnd) scrollToNewest() else currentOnEvent(ChatEvent.ShowNewest) }
    }
}

/**
 * Scrolls [listState] to the newest message ([scrollToNewest]) on the motion scheme's default spatial spring, in a
 * scope of this composition.
 */
@Composable
internal fun rememberScrollToNewest(listState: LazyListState): () -> Unit {
    val scope = rememberCoroutineScope()
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    return remember<() -> Unit>(listState, scope, spec) { { scope.launch { listState.scrollToNewest(spec) } } }
}

/** Scrolls to the newest message: snaps close to it when far, then glides the rest on [spec]. */
internal suspend fun LazyListState.scrollToNewest(spec: AnimationSpec<Float>) {
    if (firstVisibleItemIndex > JUMP_GLIDE_ENTRIES) scrollToItem(JUMP_GLIDE_ENTRIES)
    val visible = layoutInfo.visibleItemsInfo
    val averageSize = if (visible.isEmpty()) 0 else visible.sumOf { it.size } / visible.size
    // Backwards: towards the start of the reverse layout, the newest message.
    animateScrollBy(-(firstVisibleItemIndex * averageSize + firstVisibleItemScrollOffset).toFloat(), spec)
    scrollToItem(0)
}

/** The count in the jump button's badge, capped at "999+" so the badge stays small. */
@Composable
private fun unreadCountText(count: Int): String =
    if (count > MAX_UNREAD_SHOWN) {
        stringResource(R.string.chat_unread_badge_overflow, MAX_UNREAD_SHOWN)
    } else {
        stringResource(R.string.chat_unread_badge, count)
    }

/** How many entries from the newest message the jump button glides; it snaps there first when farther. */
private const val JUMP_GLIDE_ENTRIES = 15

private const val MAX_UNREAD_SHOWN = 999

/**
 * What sending does to the list of [content]: a message sent now goes to the bottom of the list, which [scrollToNewest]
 * brings into view. Scheduled ones do not.
 */
internal fun scrollingToSent(
    content: ChatContent,
    scrollToNewest: () -> Unit,
): (SendOptions) -> Unit =
    { options -> if (options.scheduledAt == null && content is ChatContent.Content) scrollToNewest() }
