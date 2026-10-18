package dev.kyralabs.pingogram.feature.chats.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Dp
import dev.kyralabs.pingogram.core.ui.component.EmptyState
import dev.kyralabs.pingogram.core.ui.component.HideNavigationBar
import dev.kyralabs.pingogram.core.ui.component.floatingToolbarInsets
import dev.kyralabs.pingogram.core.ui.component.screenContentWindowInsets
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.feature.chats.R
import dev.kyralabs.pingogram.feature.chats.ui.preview.ChatListPreviewStates
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.distinctUntilChanged
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * A chat list (`REQUIREMENTS.md` FR-L1–L5, FR-L8, FR-L9; `docs/DESIGN_SYSTEM.md` §6): the Chats tab with a
 * [MediumFlexibleTopAppBar] that collapses as the list scrolls ([ChatListTopBar]), or the archive with a small one
 * ([ArchiveTopBar]); then the chats, each with its swipe actions ([ChatRow]), below the archive row on the main list.
 *
 * - **Insets**: the list draws behind the floating navigation bar and ends clear of it ([screenContentWindowInsets]);
 *   on medium and wider windows its rows keep a comfortable width, centered.
 * - **Paging**: it reports whether its end is within [LOAD_MORE_THRESHOLD] rows, so more chats load, and scrolls to
 *   the top when another folder is selected.
 * - **Selection mode**: the floating navigation bar steps aside ([HideNavigationBar]) for the [SelectionToolbar] in
 *   its place, and the list ends clear of the toolbar on every window size. Back (predictive, with the toolbar
 *   following the gesture) and the top bar's close button leave it.
 * - **Snackbars** show above the floating bar or toolbar; the delete confirmation is a dialog ([DeleteChatDialog]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatListScreen(
    state: ChatListUiState,
    onEvent: (ChatListEvent) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    listState: LazyListState = rememberLazyListState(),
    onBack: () -> Unit = {},
) {
    val selection = state.selection
    HideNavigationBar(hidden = selection != null)
    val backProgress = rememberSelectionBack(enabled = selection != null) { onEvent(ChatListEvent.ClearSelection) }
    val clearSelection = { onEvent(ChatListEvent.ClearSelection) }
    val scrollBehavior =
        when (state.kind) {
            ChatListKind.Chats -> TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
            ChatListKind.Archive -> TopAppBarDefaults.pinnedScrollBehavior()
        }
    Box(modifier) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                when (state.kind) {
                    ChatListKind.Chats -> {
                        ChatListTopBar(
                            state = state,
                            scrollBehavior = scrollBehavior,
                            onSelectFolder = { onEvent(ChatListEvent.SelectFolder(it)) },
                            onClearSelection = clearSelection,
                        )
                    }

                    ChatListKind.Archive -> {
                        ArchiveTopBar(selection?.chatIds?.size, scrollBehavior, onBack, clearSelection)
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            // The floating navigation bar, or the selection toolbar in its place, covers the bottom.
            contentWindowInsets =
                if (selection != null) {
                    screenContentWindowInsets().union(floatingToolbarInsets())
                } else {
                    screenContentWindowInsets()
                },
        ) { innerPadding -> ChatListBody(state, onEvent, listState, innerPadding) }
        SelectionToolbarHost(
            selection = selection,
            backProgress = { backProgress.value },
            onAction = { onEvent(ChatListEvent.SelectionAction(it)) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
    ScrollToTopOnFolderChange(state.selectedFolderId, listState)
    state.deleteDialog?.let { dialog ->
        DeleteChatDialog(
            dialog = dialog,
            onConfirm = { onEvent(ChatListEvent.ConfirmDelete(it)) },
            onDismiss = { onEvent(ChatListEvent.DismissDelete) },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChatListBody(
    state: ChatListUiState,
    onEvent: (ChatListEvent) -> Unit,
    listState: LazyListState,
    innerPadding: PaddingValues,
) {
    val contentModifier =
        Modifier
            .fillMaxSize()
            .consumeWindowInsets(innerPadding)
    when (val content = state.content) {
        ChatListContent.Loading -> {
            Box(contentModifier.padding(innerPadding), contentAlignment = Alignment.Center) { LoadingIndicator() }
        }

        is ChatListContent.Empty -> {
            Column(contentModifier.padding(innerPadding)) {
                // The archive stays in reach when every chat is archived.
                state.archive?.let { ArchiveRow(it, enabled = true, onClick = { onEvent(ChatListEvent.OpenArchive) }) }
                ChatListEmpty(kind = state.kind, isFolder = content.isFolder, modifier = Modifier.weight(1f))
            }
        }

        is ChatListContent.Error -> {
            ChatListError(
                content = content,
                onRetry = { onEvent(ChatListEvent.Retry) },
                modifier = contentModifier.padding(innerPadding),
            )
        }

        is ChatListContent.Content -> {
            ChatList(
                items = content.items,
                state = state,
                listState = listState,
                contentPadding = innerPadding,
                onEvent = onEvent,
                modifier = contentModifier,
            )
        }
    }
}

/**
 * Scrolls [listState] to the top when another folder is selected. The folder last scrolled for is saved, so a
 * rotation or process death keeps the restored position.
 */
@Composable
private fun ScrollToTopOnFolderChange(
    selectedFolderId: Int?,
    listState: LazyListState,
) {
    var shownFolderId by rememberSaveable { mutableStateOf(selectedFolderId) }
    LaunchedEffect(selectedFolderId) {
        if (selectedFolderId != shownFolderId) {
            listState.scrollToItem(0)
            shownFolderId = selectedFolderId
        }
    }
}

/**
 * The archive row of [state], if any, then its chats ([items]), keyed by id, each row animating to its new place when
 * the order changes and fading out when it leaves (archived, deleted). Reports to [ChatListEvent.NearEndChanged]
 * whether the end is within [LOAD_MORE_THRESHOLD] rows: at once, on every change, afresh for another folder, and false
 * when the list leaves the screen. An archive row that appears above the first chat while the list shows its top is
 * scrolled into view.
 */
@Composable
private fun ChatList(
    items: ImmutableList<ChatListItemModel>,
    state: ChatListUiState,
    listState: LazyListState,
    contentPadding: PaddingValues,
    onEvent: (ChatListEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val archive = state.archive
    val selection = state.selection
    val folderId = state.selectedFolderId
    val selectedIds = selection?.chatIds
    val maxWidth = MaterialTheme.sizes.contentMaxWidth
    val motionScheme = MaterialTheme.motionScheme
    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = contentPadding,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (archive != null) {
            item(key = ARCHIVE_ROW, contentType = ARCHIVE_ROW) {
                ArchiveRow(
                    entry = archive,
                    enabled = selection == null,
                    onClick = { onEvent(ChatListEvent.OpenArchive) },
                    modifier = Modifier.listRow(this, maxWidth, motionScheme),
                )
            }
        }
        items(items, key = { it.id }, contentType = { CHAT_ROW }) { item ->
            ChatRow(
                item = item,
                selected = selectedIds?.contains(item.id) == true,
                selectionMode = selection != null,
                onEvent = onEvent,
                modifier = Modifier.listRow(this, maxWidth, motionScheme),
            )
        }
    }
    val hasArchive = archive != null
    LaunchedEffect(hasArchive) {
        // Inserted above the first chat, which the list keeps in place: show the row if the list was at its top.
        if (hasArchive && listState.firstVisibleItemIndex == 1 && listState.firstVisibleItemScrollOffset == 0) {
            listState.scrollToItem(0)
        }
    }
    val currentOnEvent by rememberUpdatedState(onEvent)
    LaunchedEffect(listState, folderId) {
        try {
            snapshotFlow {
                val layout = listState.layoutInfo
                val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
                layout.totalItemsCount > 0 && lastVisible >= layout.totalItemsCount - 1 - LOAD_MORE_THRESHOLD
            }.distinctUntilChanged()
                .collect { currentOnEvent(ChatListEvent.NearEndChanged(it)) }
        } finally {
            // Leaving the screen, or another folder that reports afresh.
            currentOnEvent(ChatListEvent.NearEndChanged(false))
        }
    }
}

/** A row of [scope] at most [maxWidth] wide, animating as the list changes on the [motionScheme]'s springs. */
private fun Modifier.listRow(
    scope: LazyItemScope,
    maxWidth: Dp,
    motionScheme: MotionScheme,
): Modifier =
    with(scope) {
        this@listRow
            .widthIn(max = maxWidth)
            .fillMaxWidth()
            .animateItem(
                fadeInSpec = motionScheme.defaultEffectsSpec(),
                placementSpec = motionScheme.defaultSpatialSpec(),
                fadeOutSpec = motionScheme.fastEffectsSpec(),
            )
    }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChatListEmpty(
    kind: ChatListKind,
    isFolder: Boolean,
    modifier: Modifier = Modifier,
) {
    when {
        kind == ChatListKind.Archive -> {
            EmptyState(
                illustration = MaterialShapes.Pill,
                icon = UiR.drawable.ic_archive,
                title = stringResource(R.string.chat_list_archive_empty_title),
                body = stringResource(R.string.chat_list_archive_empty_body),
                modifier = modifier,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                iconColor = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }

        isFolder -> {
            EmptyState(
                illustration = MaterialShapes.Clover4Leaf,
                icon = UiR.drawable.ic_chat_filled,
                title = stringResource(R.string.chat_list_folder_empty_title),
                body = stringResource(R.string.chat_list_folder_empty_body),
                modifier = modifier,
            )
        }

        else -> {
            EmptyState(
                illustration = MaterialShapes.Cookie9Sided,
                icon = UiR.drawable.ic_chat_filled,
                title = stringResource(R.string.chat_list_empty_title),
                body = stringResource(R.string.chat_list_empty_body),
                modifier = modifier,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChatListError(
    content: ChatListContent.Error,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EmptyState(
        illustration = MaterialShapes.Burst,
        icon = UiR.drawable.ic_error,
        title = stringResource(R.string.chat_list_error_title),
        body = errorText(content.error),
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.errorContainer,
        iconColor = MaterialTheme.colorScheme.onErrorContainer,
        action = {
            Button(onClick = onRetry) {
                Text(
                    text = stringResource(R.string.chat_list_retry),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }
        },
    )
}

/** How close to its end the list asks for more chats: about a page of rows ahead of the user. */
private const val LOAD_MORE_THRESHOLD = 20

private const val CHAT_ROW = "chat"
private const val ARCHIVE_ROW = "archive"

@PingogramPreviews
@Composable
private fun ChatListScreenPreview(
    @PreviewParameter(ChatListPreviewStates::class) state: ChatListUiState,
) {
    PingogramPreview { ChatListScreen(state = state, onEvent = {}) }
}
