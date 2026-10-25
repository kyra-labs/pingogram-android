package dev.kyralabs.pingogram.feature.chat.ui.interactions

import androidx.compose.runtime.Composable
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.feature.chat.ui.MessageActionsBottomSheet
import dev.kyralabs.pingogram.feature.chat.ui.preview.InteractionsPreviewData

// Previews of the sheet of who saw a message and who reacted to it, a state each, and of the actions that open it.

@PingogramPreviews
@Composable
private fun SeenByPreview() {
    PingogramPreview { MessageInteractionsContent(InteractionsPreviewData.seen, onEvent = {}) }
}

@PingogramPreviews
@Composable
private fun AllReactionsPreview() {
    PingogramPreview { MessageInteractionsContent(InteractionsPreviewData.allReactions, onEvent = {}) }
}

@PingogramPreviews
@Composable
private fun OneReactionPreview() {
    PingogramPreview { MessageInteractionsContent(InteractionsPreviewData.oneReaction, onEvent = {}) }
}

@PingogramPreviews
@Composable
private fun InteractionsLoadingPreview() {
    PingogramPreview { MessageInteractionsContent(InteractionsPreviewData.loading, onEvent = {}) }
}

@PingogramPreviews
@Composable
private fun InteractionsEmptyPreview() {
    PingogramPreview { MessageInteractionsContent(InteractionsPreviewData.empty, onEvent = {}) }
}

@PingogramPreviews
@Composable
private fun InteractionsErrorPreview() {
    PingogramPreview { MessageInteractionsContent(InteractionsPreviewData.error, onEvent = {}) }
}

/** A group message's actions, "Seen by" and "Reactions" first. */
@PingogramPreviews
@Composable
private fun GroupMessageActionsPreview() {
    PingogramPreview {
        val actions = InteractionsPreviewData.groupActionsSheet.messageActions
        if (actions != null) MessageActionsBottomSheet(actions, onEvent = {})
    }
}
