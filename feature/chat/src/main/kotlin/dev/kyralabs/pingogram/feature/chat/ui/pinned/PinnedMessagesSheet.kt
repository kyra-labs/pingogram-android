package dev.kyralabs.pingogram.feature.chat.ui.pinned

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatEvent
import dev.kyralabs.pingogram.feature.chat.ui.PinnedEvent
import dev.kyralabs.pingogram.feature.chat.ui.insetContentPadding
import dev.kyralabs.pingogram.feature.chat.ui.preview.PinnedPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.sheetRowInsets

/**
 * Every pinned message of the chat (`REQUIREMENTS.md` FR-C9), a [ModalBottomSheet] opened from the bar's list button:
 * "Pinned messages" in `titleLargeEmphasized`, then the messages oldest first, as the conversation shows them, as list
 * items inset like the chat list's rows (`docs/DESIGN_SYSTEM.md` §4), the sender over two lines of the message; the one
 * the bar shows takes the list item's selected container (`secondaryContainer`, `large`), and the list opens at it. A
 * tap shows that message in the conversation ([PinnedEvent.ShowFromList]) and closes the sheet; dragging it down, the
 * scrim and back close it ([PinnedEvent.DismissList]). The list scrolls where the window is too short.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PinnedMessagesSheet(
    sheet: PinnedSheet,
    onEvent: (ChatEvent) -> Unit,
) {
    val currentOnEvent by rememberUpdatedState(onEvent)
    ModalBottomSheet(onDismissRequest = { currentOnEvent(PinnedEvent.DismissList) }) {
        PinnedSheetContent(sheet, currentOnEvent)
    }
}

@Composable
private fun PinnedSheetContent(
    sheet: PinnedSheet,
    onEvent: (ChatEvent) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    Column(Modifier.padding(bottom = spacing.large)) {
        Text(
            text = stringResource(R.string.pinned_sheet_title),
            style = MaterialTheme.typography.titleLargeEmphasized,
            modifier =
                Modifier
                    .padding(horizontal = spacing.large, vertical = spacing.small)
                    .semantics { heading() },
        )
        val shownIndex = sheet.rows.indexOfFirst { it.messageId == sheet.shownId }.coerceAtLeast(0)
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = shownIndex)
        LazyColumn(Modifier.selectableGroup(), state = listState) {
            items(sheet.rows, key = { it.messageId }, contentType = { CONTENT_TYPE_PINNED }) { row ->
                PinnedMessageRow(
                    row = row,
                    selected = row.messageId == sheet.shownId,
                    onClick = { onEvent(PinnedEvent.ShowFromList(row.messageId)) },
                )
            }
        }
    }
}

/** One pinned message: its sender (none where the chat itself posts) over two lines of it. */
@Composable
private fun PinnedMessageRow(
    row: PinnedRow,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val insets = sheetRowInsets()
    val preview = row.preview.resolve()
    val sender = row.sender?.resolve()
    ListItem(
        selected = selected,
        onClick = onClick,
        modifier = Modifier.padding(insets),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        contentPadding = insetContentPadding(insets),
        supportingContent =
            sender?.let {
                {
                    Text(
                        text = preview,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = LocalTextStyle.current.userContent,
                    )
                }
            },
    ) {
        Text(
            text = sender ?: preview,
            maxLines = if (sender == null) 2 else 1,
            overflow = TextOverflow.Ellipsis,
            style = LocalTextStyle.current.userContent,
        )
    }
}

private const val CONTENT_TYPE_PINNED = "pinned"

@PingogramPreviews
@Composable
private fun PinnedMessagesSheetPreview() {
    PingogramPreview { PinnedSheetContent(PinnedPreviewData.sheet, onEvent = {}) }
}
