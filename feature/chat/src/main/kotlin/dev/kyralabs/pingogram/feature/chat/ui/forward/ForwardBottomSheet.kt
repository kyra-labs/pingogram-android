package dev.kyralabs.pingogram.feature.chat.ui.forward

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberSearchBarState
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextAlign
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatEvent
import dev.kyralabs.pingogram.feature.chat.ui.ForwardEvent
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerText
import dev.kyralabs.pingogram.feature.chat.ui.composer.LinkDialog
import dev.kyralabs.pingogram.feature.chat.ui.preview.ForwardPreviewData
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The forward sheet (`REQUIREMENTS.md` FR-C6; `docs/DESIGN_SYSTEM.md` §6 "Forward sheet"): a [ModalBottomSheet]
 * (`extraLarge` top corners) with its title, a search field, and the chats the messages can go to, Saved Messages
 * first; once a chat is picked, the forward options, the comment field and the send button come in below the list.
 *
 * - **Search**: the search bar's input field on its container; every edit goes to [ForwardEvent.QueryChanged], and
 *   the view model searches once the text stays (debounced there).
 * - **Chats** ([ForwardTargetRow]): a keyed lazy list of list items, inset in the sheet like the chat list's rows; a
 *   tap picks or unpicks the chat, which gives its row the list item's selected container (`secondaryContainer`) and
 *   shape (`large`) and morphs its avatar into a check, both on the motion scheme's springs. Screen readers hear
 *   each row as a check box. Near the end of the list the next page of chats loads
 *   ([ForwardEvent.LoadMore]). While the chats load a delayed loading indicator shows; without any, why.
 * - **Options**: "Hide sender's name" where every message can be copied, and "Hide captions" where one has a caption,
 *   as switches; the comment field is the composer's, formatting included; the send button is a default-size
 *   button with a press morph. They grow in and out on the motion scheme's default spatial spring, fading on its
 *   effects springs.
 * - Sending confirms with a haptic, hides the sheet on its spring, then sends ([ForwardEvent.Send]). Dragging the
 *   sheet down, the scrim and the back gesture (Material's predictive back) dismiss it ([ForwardEvent.Dismiss]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ForwardBottomSheet(
    sheet: ForwardSheet,
    comment: ComposerText,
    onEvent: (ChatEvent) -> Unit,
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
                if (currentSheet.selectedIds.isNotEmpty()) {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    scope.launch { sheetState.hide() }.invokeOnCompletion { currentOnEvent(ForwardEvent.Send) }
                }
            }
        }
    val query = rememberTextFieldState()
    LaunchedEffect(query) {
        snapshotFlow { query.text.toString() }.collect { currentOnEvent(ForwardEvent.QueryChanged(it)) }
    }
    var linking by remember { mutableStateOf<TextRange?>(null) }
    ModalBottomSheet(onDismissRequest = { currentOnEvent(ForwardEvent.Dismiss) }, sheetState = sheetState) {
        ForwardContent(
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

/** What the sheet shows: the title, the search field, the chats, and the options once a chat is picked. */
@Suppress("LongParameterList") // The sheet, its two fields, and what it does with them.
@Composable
internal fun ColumnScope.ForwardContent(
    sheet: ForwardSheet,
    query: TextFieldState,
    comment: ComposerText,
    onEvent: (ChatEvent) -> Unit,
    onSend: () -> Unit,
    onLink: (TextRange) -> Unit,
    listState: LazyListState = rememberLazyListState(),
) {
    ForwardHeader(sheet)
    SearchField(query)
    Box(Modifier.weight(1f).fillMaxWidth()) {
        if (sheet.targets.isEmpty()) {
            NoTargets(sheet)
        } else {
            TargetList(sheet, listState, onEvent)
        }
    }
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = sheet.selectedIds.isNotEmpty(),
        enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
        exit = shrinkVertically(motion.defaultSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
    ) {
        ForwardOptions(sheet, comment, onEvent, onSend, onLink)
    }
}

/** The title, and how many chats are picked below it once any are. */
@Composable
private fun ForwardHeader(sheet: ForwardSheet) {
    val spacing = MaterialTheme.spacing
    Column(Modifier.fillMaxWidth().padding(horizontal = spacing.large).padding(bottom = spacing.medium)) {
        Text(
            text =
                if (sheet.messageCount > 1) {
                    pluralStringResource(R.plurals.forward_title_many, sheet.messageCount, sheet.messageCount)
                } else {
                    stringResource(R.string.forward_title)
                },
            style = MaterialTheme.typography.titleLargeEmphasized,
        )
        val selected = sheet.selectedIds.size
        if (selected > 0) {
            Text(
                text = pluralStringResource(R.plurals.forward_selected, selected, selected),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** The search bar's input field on its container, with a clear button while there is text. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchField(query: TextFieldState) {
    val spacing = MaterialTheme.spacing
    val hasText = query.text.isNotEmpty()
    val keyboard = LocalSoftwareKeyboardController.current
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.large).padding(bottom = spacing.small),
        shape = SearchBarDefaults.inputFieldShape,
        color = SearchBarDefaults.colors().containerColor,
    ) {
        SearchBarDefaults.InputField(
            textFieldState = query,
            searchBarState = rememberSearchBarState(),
            onSearch = { keyboard?.hide() },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.forward_search)) },
            leadingIcon = { Icon(painterResource(UiR.drawable.ic_search), contentDescription = null) },
            trailingIcon =
                if (hasText) {
                    {
                        IconButton(onClick = { query.clearText() }) {
                            Icon(
                                painter = painterResource(UiR.drawable.ic_close),
                                contentDescription = stringResource(R.string.forward_search_clear),
                            )
                        }
                    }
                } else {
                    null
                },
        )
    }
}

/**
 * The chats, keyed by id; near the end of the chat list (not of search results, which come whole) the next page
 * loads, again whenever a page leaves the end near.
 */
@Composable
private fun TargetList(
    sheet: ForwardSheet,
    listState: LazyListState,
    onEvent: (ChatEvent) -> Unit,
) {
    val currentOnEvent by rememberUpdatedState(onEvent)
    LaunchedEffect(listState, sheet.isSearching) {
        if (sheet.isSearching) return@LaunchedEffect
        snapshotFlow {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            // The entry count, so that a page that leaves the end near asks again.
            layout.totalItemsCount.takeIf { it > 0 && lastVisible >= it - 1 - LOAD_MORE_THRESHOLD }
        }.filterNotNull()
            .distinctUntilChanged()
            .collect { currentOnEvent(ForwardEvent.LoadMore) }
    }
    val motion = MaterialTheme.motionScheme
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(sheet.targets, key = { it.chatId }, contentType = { TARGET }) { target ->
            ForwardTargetRow(
                target = target,
                selected = target.chatId in sheet.selectedIds,
                onToggle = { onEvent(ForwardEvent.ToggleTarget(target.chatId)) },
                modifier =
                    Modifier.animateItem(
                        fadeInSpec = motion.defaultEffectsSpec(),
                        placementSpec = motion.defaultSpatialSpec(),
                        fadeOutSpec = motion.fastEffectsSpec(),
                    ),
            )
        }
    }
}

/** Why there are no chats: loading (after a short wait), the search failed or found none, or there are none. */
@Composable
private fun NoTargets(sheet: ForwardSheet) {
    val spacing = MaterialTheme.spacing
    Box(Modifier.fillMaxSize().padding(spacing.extraLarge), contentAlignment = Alignment.Center) {
        val error = sheet.searchError
        when {
            sheet.isLoading -> {
                DelayedLoadingIndicator()
            }

            else -> {
                Text(
                    text =
                        when {
                            error != null -> errorText(error)
                            sheet.isSearching -> stringResource(R.string.forward_nothing_found)
                            else -> stringResource(R.string.forward_no_chats)
                        },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private const val TARGET = "target"

/** How many rows before the end the next page of chats loads. */
private const val LOAD_MORE_THRESHOLD = 10

@PingogramPreviews
@Composable
private fun ForwardSheetPreview() {
    PingogramPreview {
        Column {
            ForwardContent(
                sheet = ForwardPreviewData.withSelection,
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
private fun ForwardSheetNothingSelectedPreview() {
    PingogramPreview {
        Column {
            ForwardContent(
                ForwardPreviewData.sheet,
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
private fun ForwardSheetNothingFoundPreview() {
    PingogramPreview {
        Column {
            ForwardContent(
                ForwardPreviewData.nothingFound,
                remember {
                    TextFieldState("zz")
                },
                remember { ComposerText() },
                {},
                {},
                {},
            )
        }
    }
}
