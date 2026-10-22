package dev.kyralabs.pingogram.feature.chat.ui.share

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.domain.model.AttachmentKind
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerField
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerText
import dev.kyralabs.pingogram.feature.chat.ui.composer.LinkDialog
import dev.kyralabs.pingogram.feature.chat.ui.forward.ChatSearchField
import dev.kyralabs.pingogram.feature.chat.ui.forward.NoTargets
import dev.kyralabs.pingogram.feature.chat.ui.forward.TargetList
import dev.kyralabs.pingogram.feature.chat.ui.preview.SharePreviewData
import kotlinx.coroutines.launch

/**
 * The share sheet (`REQUIREMENTS.md` FR-N2, direct share; `docs/DESIGN_SYSTEM.md` §6 "Share sheet"): a
 * [ModalBottomSheet] with the forward sheet's parts, for what another app shared or a `t.me/share` link. Its title,
 * what is shared below it (the start of the text, how many photos and videos or files), the search field and the
 * chats, Saved Messages first, each toggled by a tap as in the forward sheet. Once a chat is picked, the bottom grows
 * in on the default spatial spring:
 *
 * - **One chat**: why to open it (`bodyMedium` `onSurfaceVariant`) and a default "Open chat" button, which closes the
 *   sheet and opens the chat with the share in its composer, where the user sends it.
 * - **Several**: the comment field (the composer's, formatting included) and a default "Send" button, which sends the
 *   share to them all, after a `Confirm` haptic, as the sheet hides.
 *
 * The two crossfade on the effects springs, the bottom resizing on the default spatial spring. Dragging the sheet
 * down, the scrim and the back gesture (Material's predictive back) dismiss it ([ShareEvent.Dismiss]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShareBottomSheet(
    sheet: ShareSheet,
    comment: ComposerText,
    onEvent: (ShareEvent) -> Unit,
) {
    val sheetState =
        rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val currentOnEvent by rememberUpdatedState(onEvent)
    val currentSheet by rememberUpdatedState(sheet)
    val send: () -> Unit =
        remember(sheetState, scope, haptics) {
            {
                val selected = currentSheet.selectedIds.size
                if (selected > 0) {
                    if (selected > 1) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    scope.launch { sheetState.hide() }.invokeOnCompletion { currentOnEvent(ShareEvent.Send) }
                }
            }
        }
    val query = rememberTextFieldState()
    LaunchedEffect(query) {
        snapshotFlow { query.text.toString() }.collect { currentOnEvent(ShareEvent.QueryChanged(it)) }
    }
    var linking by remember { mutableStateOf<TextRange?>(null) }
    ModalBottomSheet(onDismissRequest = { currentOnEvent(ShareEvent.Dismiss) }, sheetState = sheetState) {
        ShareContent(
            sheet = sheet,
            query = query,
            comment = comment,
            onEvent = onEvent,
            onSend = send,
            onLink = { linking = it },
        )
    }
    linking?.let { selection ->
        LinkDialog(
            currentUrl = comment.linkAt(selection),
            onDismiss = { linking = null },
            onConfirm = { url ->
                comment.setLink(url, selection)
                linking = null
            },
        )
    }
}

/** What the sheet shows: the title, what is shared, the search field, the chats, and the bottom once one is picked. */
@Suppress("LongParameterList") // The sheet, its two fields, and what it does with them.
@Composable
internal fun ColumnScope.ShareContent(
    sheet: ShareSheet,
    query: TextFieldState,
    comment: ComposerText,
    onEvent: (ShareEvent) -> Unit,
    onSend: () -> Unit,
    onLink: (TextRange) -> Unit,
    listState: LazyListState = rememberLazyListState(),
) {
    ShareHeader(sheet)
    ChatSearchField(query)
    Box(Modifier.weight(1f).fillMaxWidth()) {
        if (sheet.targets.isEmpty()) {
            NoTargets(
                isLoading = sheet.isLoading,
                isSearching = sheet.isSearching,
                error = sheet.searchError,
                noChats = stringResource(R.string.share_no_chats),
            )
        } else {
            TargetList(
                targets = sheet.targets,
                selectedIds = sheet.selectedIds,
                isSearching = sheet.isSearching,
                listState = listState,
                onToggle = { onEvent(ShareEvent.ToggleTarget(it)) },
                onLoadMore = { onEvent(ShareEvent.LoadMore) },
            )
        }
    }
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = sheet.selectedIds.isNotEmpty(),
        enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
        exit = shrinkVertically(motion.defaultSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
    ) {
        AnimatedContent(
            targetState = sheet.selectedIds.size > 1,
            transitionSpec = {
                (fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()))
                    .using(SizeTransform(clip = false) { _, _ -> motion.defaultSpatialSpec() })
            },
            label = "shareBottom",
        ) { several ->
            if (several) {
                SendToSeveral(sheet.selectedIds.size, comment, onSend, onLink)
            } else {
                OpenOneChat(onSend)
            }
        }
    }
}

/** The title, then what is shared, and how many chats are picked once any are. */
@Composable
private fun ShareHeader(sheet: ShareSheet) {
    val spacing = MaterialTheme.spacing
    val resources = LocalResources.current
    Column(Modifier.fillMaxWidth().padding(horizontal = spacing.large).padding(bottom = spacing.medium)) {
        Text(text = stringResource(R.string.share_title), style = MaterialTheme.typography.titleLargeEmphasized)
        val files =
            when {
                sheet.fileCount == 0 -> {
                    null
                }

                sheet.fileKind == AttachmentKind.Media -> {
                    pluralStringResource(
                        R.plurals.share_media,
                        sheet.fileCount,
                        sheet.fileCount,
                    )
                }

                else -> {
                    pluralStringResource(R.plurals.share_files, sheet.fileCount, sheet.fileCount)
                }
            }
        if (sheet.text.isNotEmpty()) {
            Text(
                text = sheet.text,
                modifier =
                    Modifier.semantics {
                        contentDescription = resources.getString(R.string.share_text_description, sheet.text)
                    },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.userContent,
            )
        }
        val selected = sheet.selectedIds.size
        val details =
            listOfNotNull(
                files,
                if (selected > 0) pluralStringResource(R.plurals.forward_selected, selected, selected) else null,
            )
        if (details.isNotEmpty()) {
            Text(
                text = details.joinToString(" · "),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** One chat picked: why to open it, and the button that does. */
@Composable
private fun OpenOneChat(onOpen: () -> Unit) {
    val spacing = MaterialTheme.spacing
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.large, vertical = spacing.small),
        horizontalArrangement = Arrangement.spacedBy(spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.share_open_chat_hint),
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onOpen, shapes = ButtonDefaults.shapes()) {
            Text(text = stringResource(R.string.share_open_chat), style = MaterialTheme.typography.labelLargeEmphasized)
        }
    }
}

/** Several chats picked: the comment field and the send button; screen readers hear to how many chats. */
@Composable
private fun SendToSeveral(
    count: Int,
    comment: ComposerText,
    onSend: () -> Unit,
    onLink: (TextRange) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val resources = LocalResources.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.medium, vertical = spacing.small),
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
        verticalAlignment = Alignment.Bottom,
    ) {
        ComposerField(
            text = comment,
            onSend = onSend,
            onLink = onLink,
            onFocusChanged = {},
            modifier = Modifier.weight(1f),
            placeholder = stringResource(R.string.share_comment),
        )
        Button(
            onClick = onSend,
            shapes = ButtonDefaults.shapes(),
            modifier =
                Modifier.semantics {
                    contentDescription = resources.getQuantityString(R.plurals.forward_send_description, count, count)
                },
        ) {
            Text(text = stringResource(R.string.share_send), style = MaterialTheme.typography.labelLargeEmphasized)
        }
    }
}

@PingogramPreviews
@Composable
private fun ShareSheetPreview() {
    PingogramPreview {
        Column {
            ShareContent(
                sheet = SharePreviewData.link,
                query = remember { TextFieldState() },
                comment = remember { ComposerText() },
                onEvent = {},
                onSend = {},
                onLink = {},
            )
        }
    }
}

@PingogramPreviews
@Composable
private fun ShareSheetOneChatPreview() {
    PingogramPreview {
        Column {
            ShareContent(
                SharePreviewData.oneChat,
                remember { TextFieldState() },
                remember { ComposerText() },
                {},
                {},
                {},
            )
        }
    }
}

@PingogramPreviews
@Composable
private fun ShareSheetSeveralChatsPreview() {
    PingogramPreview {
        Column {
            ShareContent(
                SharePreviewData.photosToTwoChats,
                remember { TextFieldState() },
                remember { ComposerText() },
                {},
                {},
                {},
            )
        }
    }
}

@PingogramPreviews
@Composable
private fun ShareSheetNothingFoundPreview() {
    PingogramPreview {
        Column {
            ShareContent(
                SharePreviewData.nothingFound,
                remember { TextFieldState("zz") },
                remember { ComposerText() },
                {},
                {},
                {},
            )
        }
    }
}
