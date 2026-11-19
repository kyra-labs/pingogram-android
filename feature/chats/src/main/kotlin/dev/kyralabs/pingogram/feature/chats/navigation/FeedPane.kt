package dev.kyralabs.pingogram.feature.chats.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.kyralabs.pingogram.feature.chats.ui.feed.FeedRoute

/**
 * The Channel feed (`REQUIREMENTS.md` FR-X5) in the Chats tab's list pane, where it docks on two panes
 * (`docs/DESIGN_SYSTEM.md` §6 "Channel feed", frame 7b): [onBack] returns to the chat list, a post opens through
 * [onOpenPost] (in the detail pane beside it), and a post's "Forward" through [onForward].
 */
@Composable
fun FeedPane(
    onBack: () -> Unit,
    onOpenPost: (chatId: Long, messageId: Long) -> Unit,
    onForward: (chatId: Long, messageIds: List<Long>) -> Unit,
    modifier: Modifier = Modifier,
) {
    FeedRoute(onBack = onBack, onOpenPost = onOpenPost, onForward = onForward, modifier = modifier)
}
