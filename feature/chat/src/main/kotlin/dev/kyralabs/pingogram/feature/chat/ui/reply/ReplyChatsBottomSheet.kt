package dev.kyralabs.pingogram.feature.chat.ui.reply

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ReplyEvent
import dev.kyralabs.pingogram.feature.chat.ui.forward.ChatSearchField
import dev.kyralabs.pingogram.feature.chat.ui.forward.NoTargets
import dev.kyralabs.pingogram.feature.chat.ui.forward.TargetList
import dev.kyralabs.pingogram.feature.chat.ui.preview.ReplyPreviewData
import kotlinx.collections.immutable.persistentSetOf

/**
 * The chat picker of "Reply in another chat" (`docs/DESIGN_SYSTEM.md` §6 "Message actions"): a [ModalBottomSheet]
 * with the forward sheet's parts, as the bot buttons' "Choose a chat": "Reply in another chat"
 * (`titleLargeEmphasized`), one line of the message it replies to (`bodyMedium` `onSurfaceVariant`), the search field
 * and the chats where the user can write, Saved Messages first. One tap picks a chat ([ReplyEvent.PickChat]): it opens
 * with the reply in its composer, nothing sent. Dragging it down, the scrim and the back gesture (Material's predictive
 * back) dismiss it ([ReplyEvent.DismissChats]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReplyChatsBottomSheet(
    sheet: ReplyChatsSheet,
    onEvent: (ReplyEvent) -> Unit,
) {
    val sheetState =
        rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val currentOnEvent by rememberUpdatedState(onEvent)
    val query = rememberTextFieldState()
    LaunchedEffect(query) {
        snapshotFlow { query.text.toString() }.collect { currentOnEvent(ReplyEvent.SearchChats(it)) }
    }
    ModalBottomSheet(onDismissRequest = { currentOnEvent(ReplyEvent.DismissChats) }, sheetState = sheetState) {
        ReplyChatsContent(sheet, query, onEvent)
    }
}

/** What the picker shows: the title, the message, the search field and the chats. */
@Composable
internal fun ColumnScope.ReplyChatsContent(
    sheet: ReplyChatsSheet,
    query: TextFieldState,
    onEvent: (ReplyEvent) -> Unit,
    listState: LazyListState = rememberLazyListState(),
) {
    val spacing = MaterialTheme.spacing
    Column(Modifier.fillMaxWidth().padding(horizontal = spacing.large).padding(bottom = spacing.medium)) {
        Text(
            text = stringResource(R.string.reply_chats_title),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLargeEmphasized,
        )
        sheet.preview?.let { preview ->
            Text(
                text = stringResource(R.string.reply_chats_message, preview.resolve().bidiIsolated()),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.userContent,
            )
        }
    }
    ChatSearchField(query)
    Box(Modifier.weight(1f).fillMaxWidth()) {
        if (sheet.targets.isEmpty()) {
            NoTargets(
                isLoading = sheet.isLoading,
                isSearching = sheet.isSearching,
                error = sheet.searchError,
                noChats = stringResource(R.string.reply_chats_none),
            )
        } else {
            TargetList(
                targets = sheet.targets,
                selectedIds = persistentSetOf(),
                isSearching = sheet.isSearching,
                listState = listState,
                onToggle = { onEvent(ReplyEvent.PickChat(it)) },
                onLoadMore = { onEvent(ReplyEvent.LoadMoreChats) },
            )
        }
    }
}

@PingogramPreviews
@Composable
private fun ReplyChatsPreview() {
    PingogramPreview {
        Column {
            ReplyChatsContent(ReplyPreviewData.chatPicker, remember { TextFieldState() }, onEvent = {})
        }
    }
}

@PingogramPreviews
@Composable
private fun ReplyChatsLoadingPreview() {
    PingogramPreview {
        Column {
            ReplyChatsContent(ReplyPreviewData.chatPickerLoading, remember { TextFieldState() }, onEvent = {})
        }
    }
}

@PingogramPreviews
@Composable
private fun ReplyChatsEmptyPreview() {
    PingogramPreview {
        Column {
            ReplyChatsContent(ReplyPreviewData.chatPickerEmpty, remember { TextFieldState() }, onEvent = {})
        }
    }
}
