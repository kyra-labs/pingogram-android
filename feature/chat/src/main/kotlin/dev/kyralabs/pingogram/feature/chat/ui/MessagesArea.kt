package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.transition.sharedMediaViewport
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerUiState
import dev.kyralabs.pingogram.feature.chat.ui.drop.ChatDropZone
import dev.kyralabs.pingogram.feature.chat.ui.drop.chatDropTarget
import dev.kyralabs.pingogram.feature.chat.ui.drop.rememberChatDropState

/**
 * The messages, between the bars and the composer. A photo, video or GIF going to or from the media viewer stays
 * within them; files dragged from another app drop on all of them, where the chat takes attachments (board 35a).
 */
@Suppress("LongParameterList") // The state and the list's parts, and the overlay drawn above them.
@Composable
internal fun ColumnScope.MessagesArea(
    state: ChatUiState,
    onEvent: (ChatEvent) -> Unit,
    listState: LazyListState,
    bubbleResources: BubbleResources,
    contentPadding: PaddingValues,
    nextChannel: NextChannel?,
    overlay: @Composable () -> Unit,
) {
    val drop = rememberChatDropState()
    val takesFiles = (state.composer as? ComposerUiState.Available)?.attachOptions?.isNotEmpty() == true
    Box(
        Modifier
            .weight(1f)
            .sharedMediaViewport(contentPadding)
            .chatDropTarget(takesFiles, drop) { uris, isMedia ->
                onEvent(if (isMedia) ChatEvent.AttachMedia(uris) else ChatEvent.AttachFiles(uris))
            },
    ) {
        ChatBody(state, onEvent, listState, bubbleResources, contentPadding.withTypingRoom(state.typing), nextChannel)
        MessagesBottom(state.typing, listState, contentPadding)
        overlay()
        ChatDropZone(
            drop,
            state.header
                ?.title
                ?.resolve()
                .orEmpty(),
        )
    }
}
