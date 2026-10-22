package dev.kyralabs.pingogram.feature.chat.ui.bots

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
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.BotButtonEvent
import dev.kyralabs.pingogram.feature.chat.ui.forward.ChatSearchField
import dev.kyralabs.pingogram.feature.chat.ui.forward.NoTargets
import dev.kyralabs.pingogram.feature.chat.ui.forward.TargetList
import dev.kyralabs.pingogram.feature.chat.ui.preview.BotButtonsPreviewData
import kotlinx.collections.immutable.persistentSetOf

/**
 * The chat picker of a switch-inline button (`docs/DESIGN_SYSTEM.md` §6 "Bot buttons"): a [ModalBottomSheet] with the
 * forward sheet's parts, "Choose a chat" (`titleLargeEmphasized`), what goes into the picked chat's composer
 * ("@gif cats", `bodyMedium` `onSurfaceVariant`), the search field and the chats the bot allows, Saved Messages first.
 * One tap picks a chat ([BotButtonEvent.PickChat]): it opens with the query in its composer, nothing sent. Dragging it
 * down, the scrim and the back gesture (Material's predictive back) dismiss it ([BotButtonEvent.DismissChats]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SwitchInlineChatsSheet(
    sheet: SwitchInlineSheet,
    onEvent: (BotButtonEvent) -> Unit,
) {
    val sheetState =
        rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val currentOnEvent by rememberUpdatedState(onEvent)
    val query = rememberTextFieldState()
    LaunchedEffect(query) {
        snapshotFlow { query.text.toString() }.collect { currentOnEvent(BotButtonEvent.SearchChats(it)) }
    }
    ModalBottomSheet(onDismissRequest = { currentOnEvent(BotButtonEvent.DismissChats) }, sheetState = sheetState) {
        SwitchInlineChatsContent(sheet, query, onEvent)
    }
}

/** What the picker shows: the title, the query, the search field and the chats. */
@Composable
internal fun ColumnScope.SwitchInlineChatsContent(
    sheet: SwitchInlineSheet,
    query: TextFieldState,
    onEvent: (BotButtonEvent) -> Unit,
    listState: LazyListState = rememberLazyListState(),
) {
    val spacing = MaterialTheme.spacing
    Column(Modifier.fillMaxWidth().padding(horizontal = spacing.large).padding(bottom = spacing.medium)) {
        Text(
            text = stringResource(R.string.bot_chats_title),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLargeEmphasized,
        )
        Text(
            text = stringResource(R.string.bot_chats_query, sheet.query.trim().bidiIsolated()),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium.userContent,
        )
    }
    ChatSearchField(query)
    Box(Modifier.weight(1f).fillMaxWidth()) {
        if (sheet.targets.isEmpty()) {
            NoTargets(
                isLoading = sheet.isLoading,
                isSearching = sheet.isSearching,
                error = sheet.searchError,
                noChats = stringResource(R.string.bot_chats_none),
            )
        } else {
            TargetList(
                targets = sheet.targets,
                selectedIds = persistentSetOf(),
                isSearching = sheet.isSearching,
                listState = listState,
                onToggle = { onEvent(BotButtonEvent.PickChat(it)) },
                onLoadMore = { onEvent(BotButtonEvent.LoadMoreChats) },
            )
        }
    }
}

@PingogramPreviews
@Composable
private fun SwitchInlineChatsPreview() {
    PingogramPreview {
        Column {
            SwitchInlineChatsContent(BotButtonsPreviewData.chatPicker, remember { TextFieldState() }, onEvent = {})
        }
    }
}
