package dev.kyralabs.pingogram.feature.chats.ui

import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.component.EmptyState
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
 * The Chats tab: hoists [ChatListViewModel], and reports the app fully drawn once the chat list shows its chats or
 * that there are none, for the startup benchmark's `timeToFullDisplay` (`docs/PERFORMANCE.md` §1–2). Rows are not
 * clickable until the conversation screen lands.
 */
@Composable
internal fun ChatListRoute(
    modifier: Modifier = Modifier,
    viewModel: ChatListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReportDrawnWhen { state.content is ChatListContent.Content || state.content is ChatListContent.Empty }
    ChatListScreen(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}

/**
 * The chat list (`REQUIREMENTS.md` FR-L1–L5, `docs/DESIGN_SYSTEM.md` §6): a [MediumFlexibleTopAppBar] that collapses
 * as the list scrolls, with the connection pill below the title while not connected, the folder tabs when the user has
 * folders, and the chats. The bar and the tabs share one container, `surface` turning into `surfaceContainer` as the
 * bar collapses, read in the draw phase.
 *
 * The list draws behind the floating navigation bar and ends clear of it ([screenContentWindowInsets]); on medium and
 * wider windows its rows keep a comfortable width, centered. It reports whether its end is within
 * [LOAD_MORE_THRESHOLD] rows, so more chats load, and scrolls to the top when another folder is selected.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ChatListScreen(
    state: ChatListUiState,
    onEvent: (ChatListEvent) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            ChatListTopBar(
                state = state,
                scrollBehavior = scrollBehavior,
                onSelectFolder = { onEvent(ChatListEvent.SelectFolder(it)) },
            )
        },
        // The floating navigation bar covers the bottom of top-level screens.
        contentWindowInsets = screenContentWindowInsets(),
    ) { innerPadding ->
        val contentModifier =
            Modifier
                .fillMaxSize()
                .consumeWindowInsets(innerPadding)
        when (val content = state.content) {
            ChatListContent.Loading -> {
                Box(contentModifier.padding(innerPadding), contentAlignment = Alignment.Center) { LoadingIndicator() }
            }

            is ChatListContent.Empty -> {
                ChatListEmpty(isFolder = content.isFolder, modifier = contentModifier.padding(innerPadding))
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
                    folderId = state.selectedFolderId,
                    listState = listState,
                    contentPadding = innerPadding,
                    onNearEndChanged = { onEvent(ChatListEvent.NearEndChanged(it)) },
                    modifier = contentModifier,
                )
            }
        }
    }
    ScrollToTopOnFolderChange(state.selectedFolderId, listState)
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
 * The chats, keyed by id, each row animating to its new place when the order changes. Reports to [onNearEndChanged]
 * whether the end is within [LOAD_MORE_THRESHOLD] rows: at once, on every change, afresh for another folder
 * ([folderId]), and false when the list leaves the screen.
 */
@Composable
private fun ChatList(
    items: ImmutableList<ChatListItemModel>,
    folderId: Int?,
    listState: LazyListState,
    contentPadding: PaddingValues,
    onNearEndChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motionScheme = MaterialTheme.motionScheme
    val maxWidth = MaterialTheme.sizes.contentMaxWidth
    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = contentPadding,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        items(items, key = { it.id }, contentType = { CHAT_ROW }) { item ->
            ChatListItem(
                item = item,
                onClick = null,
                modifier =
                    Modifier
                        .widthIn(max = maxWidth)
                        .fillMaxWidth()
                        .animateItem(
                            fadeInSpec = motionScheme.defaultEffectsSpec(),
                            placementSpec = motionScheme.defaultSpatialSpec(),
                            fadeOutSpec = motionScheme.fastEffectsSpec(),
                        ),
            )
        }
    }
    val nearEndChanged by rememberUpdatedState(onNearEndChanged)
    LaunchedEffect(listState, folderId) {
        try {
            snapshotFlow {
                val layout = listState.layoutInfo
                val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
                layout.totalItemsCount > 0 && lastVisible >= layout.totalItemsCount - 1 - LOAD_MORE_THRESHOLD
            }.distinctUntilChanged()
                .collect { nearEndChanged(it) }
        } finally {
            // Leaving the screen, or another folder that reports afresh.
            nearEndChanged(false)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChatListEmpty(
    isFolder: Boolean,
    modifier: Modifier = Modifier,
) {
    EmptyState(
        illustration = if (isFolder) MaterialShapes.Clover4Leaf else MaterialShapes.Cookie9Sided,
        icon = UiR.drawable.ic_chat_filled,
        title = stringResource(if (isFolder) R.string.chat_list_folder_empty_title else R.string.chat_list_empty_title),
        body = stringResource(if (isFolder) R.string.chat_list_folder_empty_body else R.string.chat_list_empty_body),
        modifier = modifier,
    )
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

@PingogramPreviews
@Composable
private fun ChatListScreenPreview(
    @PreviewParameter(ChatListPreviewStates::class) state: ChatListUiState,
) {
    PingogramPreview { ChatListScreen(state = state, onEvent = {}) }
}
