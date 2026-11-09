package dev.kyralabs.pingogram.feature.chats.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.kyralabs.pingogram.core.navigation.ChatsRoute
import dev.kyralabs.pingogram.core.ui.component.BottomFade
import dev.kyralabs.pingogram.core.ui.component.ContentSheet
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.component.EmptyState
import dev.kyralabs.pingogram.core.ui.component.HideNavigationBar
import dev.kyralabs.pingogram.core.ui.component.LocalNavigationBarInsets
import dev.kyralabs.pingogram.core.ui.component.ScrollToTopOnReselect
import dev.kyralabs.pingogram.core.ui.component.floatingToolbarInsets
import dev.kyralabs.pingogram.core.ui.component.rememberScrollingListState
import dev.kyralabs.pingogram.core.ui.component.rememberSelectionBack
import dev.kyralabs.pingogram.core.ui.component.screenContentWindowInsets
import dev.kyralabs.pingogram.core.ui.component.sidesOnly
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.preview.PingogramVariantPreviews
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chats.R
import dev.kyralabs.pingogram.feature.chats.navigation.ChatListHeaderActions
import dev.kyralabs.pingogram.feature.chats.ui.preview.ChatListPreviewData
import dev.kyralabs.pingogram.feature.chats.ui.preview.ChatListPreviewStates
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * A chat list (`REQUIREMENTS.md` FR-L1–L5, FR-L8, FR-L9; `docs/DESIGN_SYSTEM.md` §6): the Chats tab, under its large
 * header that collapses as the list scrolls ([ChatListTopBar]: its status line from [summary], its search and account
 * buttons from [headerActions]), or the archive under a small, pinned top bar ([ArchiveTopBar]); then the chats, each
 * with its swipe actions
 * ([ChatRow]), below the archive row on the main list. The bar sits on `surfaceContainer` and the list on a rounded
 * `surface` sheet below it ([ContentSheet]), which the rows scroll in.
 *
 * - **Insets**: on the Chats tab the list draws behind the floating navigation bar and ends clear of it
 *   ([screenContentWindowInsets]); the archive, pushed on top of the tab where the bar hides, keeps clear of the system
 *   bars only. On medium and wider windows the rows keep a comfortable width, centered.
 * - **Paging**: it reports whether its end is within [LOAD_MORE_THRESHOLD] rows, so more chats load.
 * - **Folders**: selecting another folder slides that folder's list in from the side of its tab, where the user left
 *   it, or at its top the first time ([rememberShownList]); swiping the list, where the user chose it, pulls the
 *   folder beside it in with the finger ([folderSwipe], its first page loaded ahead: [neighbours]).
 * - **Selection mode**: the floating navigation bar steps aside ([HideNavigationBar]) for the [SelectionToolbar] in
 *   its place, and the list ends clear of the toolbar on every window size. Back (predictive, with the toolbar
 *   following the gesture) and the top bar's close button leave it.
 * - **Back to the top** (Chats tab only): tapping the Chats tab while it is selected scrolls the list back to its top
 *   ([ScrollToTopOnReselect]).
 * - **New chat**: the app shell's button beside the floating navigation bar or in the rail's header
 *   (`newChatButtonTransform`); "No chats yet" has a button for the same screen. Behind the floating bar the list's end
 *   fades into the sheet ([BottomFade]).
 * - **Snackbars** show above the floating bar or toolbar; the delete confirmation is a dialog ([DeleteChatDialog]).
 * - **Open chat**: beside the conversation of [openChatId] (the list pane on expanded windows), that chat's row looks
 *   and reads as selected ([ChatListItem]); not in selection mode, whose own selection the toolbar acts on.
 * - **Header**: [header] shows at the top of the sheet, above the list, which does not scroll it away: the request to
 *   allow notifications ([NotificationPromptHost]) on the Chats tab.
 * - **Stories**: [stories], where there are any, is the list's first row, above the archive row, and scrolls away with
 *   the chats: the story rings on the Chats tab (`StoryRingsRow`). One item of the list, so scrolling the chats costs
 *   what it did. A row that appears above the first one while the list shows its top is scrolled into view.
 * - **Archive row**: full or compact, its menu and swipe set its style ([ChatListEvent.SetArchiveRowStyle]); hidden,
 *   the top bar opens the archive. The archive's own top bar opens its settings.
 * - **Folder tabs**: a long press opens a tab's menu, "Mark all as read" and, with [onEditFolder], "Edit folder".
 */
@Composable
internal fun ChatListScreen(
    state: ChatListUiState,
    onEvent: (ChatListEvent) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    listState: LazyListState = rememberScrollingListState(),
    listStates: FolderListStates = rememberFolderListStates(listState, state.selectedFolderId),
    onBack: () -> Unit = {},
    openChatId: Long? = null,
    header: @Composable () -> Unit = {},
    stories: (@Composable (Modifier) -> Unit)? = null,
    onEditFolder: ((folderId: Int?) -> Unit)? = null,
    summary: () -> ChatListSummary = { ChatListSummary() },
    headerActions: ChatListHeaderActions? = null,
    neighbours: () -> FolderNeighbourLists = { FolderNeighbourLists() },
) {
    val selection = state.selection
    HideNavigationBar(hidden = selection != null)
    val backProgress = rememberSelectionBack(enabled = selection != null) { onEvent(ChatListEvent.ClearSelection) }
    // The Chats tab's header collapses as the list scrolls and comes back at its top; the archive's bar stays.
    val scrollBehavior =
        when (state.kind) {
            ChatListKind.Chats -> TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
            ChatListKind.Archive -> TopAppBarDefaults.pinnedScrollBehavior()
        }
    val folderSwitch = remember { FolderSwitch() }
    val insets = contentWindowInsets(state.kind, selectionMode = selection != null)
    Box(modifier) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            // The top bar's container; the list sits on a sheet below it (ContentSheet).
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            topBar = {
                ChatListScreenTopBar(state, scrollBehavior, onEvent, onBack, onEditFolder, summary, headerActions)
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = insets,
        ) { innerPadding ->
            ChatListBody(
                state,
                onEvent,
                listStates,
                innerPadding,
                openChatId,
                folderSwitch,
                neighbours,
                header,
                stories,
            )
        }
        SelectionToolbarHost(
            selection = selection,
            backProgress = { backProgress.value },
            onAction = { onEvent(ChatListEvent.SelectionAction(it)) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
    state.deleteDialog?.let { dialog ->
        DeleteChatDialog(
            dialog = dialog,
            onConfirm = { onEvent(ChatListEvent.ConfirmDelete(it)) },
            onDismiss = { onEvent(ChatListEvent.DismissDelete) },
        )
    }
}

/**
 * The bottom the list keeps clear of: on the Chats tab the floating navigation bar's, in selection mode the selection
 * toolbar's in its place; the archive, pushed on top of the tab where the bar hides, only the system bars'.
 */
@Composable
private fun contentWindowInsets(
    kind: ChatListKind,
    selectionMode: Boolean,
): WindowInsets {
    val insets =
        when (kind) {
            ChatListKind.Chats -> screenContentWindowInsets()
            ChatListKind.Archive -> ScaffoldDefaults.contentWindowInsets
        }
    return if (selectionMode) insets.union(floatingToolbarInsets()) else insets
}

/**
 * The list of the folder shown ([rememberShownList]) on its sheet, sliding to another folder's on a folder switch
 * ([folderSwitch]), with the list it slides against beside it while it moves ([FolderCompanion]: [neighbours] under a
 * swipe). Its own composable, which skips while its arguments stay the same: the `Scaffold` composes its content again
 * whenever it measures it anew, e.g. when the connection pill or the folder tabs change the top bar's height, and
 * those frames stop here.
 *
 * Each folder's list is movable content of its own, with its own list state ([FolderListStates]): when a swipe lands
 * or a tab is tapped, the two lists change places (the shown one and the companion) without either composing its rows
 * again, and a folder's list keeps where it was scrolled. The shown folder's list, and only it, asks for more chats
 * ([FolderListArgs.active]) and scrolls back to its top when the Chats tab is tapped again ([ScrollToTopOnReselect]).
 */
@Suppress("LongParameterList") // State, events, list, padding, open chat, folder switch, neighbours, slots.
@Composable
private fun ChatListBody(
    uiState: ChatListUiState,
    onEvent: (ChatListEvent) -> Unit,
    listStates: FolderListStates,
    innerPadding: PaddingValues,
    openChatId: Long?,
    folderSwitch: FolderSwitch,
    neighbours: () -> FolderNeighbourLists,
    header: @Composable () -> Unit,
    stories: (@Composable (Modifier) -> Unit)?,
) {
    val state = rememberShownList(uiState, listStates, folderSwitch)
    val hasStories = stories != null
    val shownListState = listStates.of(state, folderSwitch.positions, hasStories)
    ScrollToTopOnReselect(ChatsRoute, shownListState, enabled = state.kind == ChatListKind.Chats)
    val folderLists = remember { FolderLists() }
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    ContentSheet(innerPadding, Modifier.consumeWindowInsets(innerPadding)) { sheetPadding ->
        Column(Modifier.fillMaxSize()) {
            // Takes no room while it shows nothing.
            Box(Modifier.fillMaxWidth().padding(sheetPadding.sidesOnly()), Alignment.TopCenter) { header() }
            Box(Modifier.weight(1f).folderSwipe(uiState, folderSwitch, neighbours, onEvent)) {
                folderLists.of(state.selectedFolderId)(
                    FolderListArgs(
                        state = state,
                        listState = shownListState,
                        contentPadding = sheetPadding,
                        stories = stories,
                        openChatId = openChatId,
                        onEvent = onEvent,
                        active = true,
                        modifier = Modifier.folderSlide(folderSwitch, isRtl).fillMaxSize(),
                    ),
                )
                FolderCompanion(folderSwitch, neighbours, isRtl, shownFolderId = state.selectedFolderId) {
                    companion,
                    modifier,
                    ->
                    folderLists.of(companion.selectedFolderId)(
                        FolderListArgs(
                            state = companion,
                            listState = listStates.of(companion, folderSwitch.positions, hasStories),
                            contentPadding = sheetPadding,
                            stories = stories,
                            openChatId = openChatId,
                            onEvent = onEvent,
                            active = false,
                            modifier = modifier,
                        ),
                    )
                }
            }
        }
        // Behind the floating navigation bar on compact windows: no room there on rail windows and in the archive.
        if (state.kind == ChatListKind.Chats) BottomFade(Modifier.align(Alignment.BottomCenter))
    }
}

/**
 * What a folder's list is drawn with: its [state], [listState] and [contentPadding], the story rings, the open chat,
 * the screen's events, whether it is the list shown ([active]: the companion asks for nothing), and its [modifier].
 */
private data class FolderListArgs(
    val state: ChatListUiState,
    val listState: LazyListState,
    val contentPadding: PaddingValues,
    val stories: (@Composable (Modifier) -> Unit)?,
    val openChatId: Long?,
    val onEvent: (ChatListEvent) -> Unit,
    val active: Boolean,
    val modifier: Modifier,
)

/** Each folder's list as movable content (`null` for "All chats"), made the first time the folder shows. */
private class FolderLists {
    private val lists = HashMap<Int?, @Composable (FolderListArgs) -> Unit>()

    fun of(folderId: Int?): @Composable (FolderListArgs) -> Unit =
        lists.getOrPut(folderId) { movableContentOf { args: FolderListArgs -> FolderListContent(args) } }
}

/** What a folder's list shows: its indicator while loading, its empty state or error, or its chats. */
@Composable
private fun FolderListContent(args: FolderListArgs) {
    val state = args.state
    val onEvent = args.onEvent
    val modifier = args.modifier
    val contentPadding = args.contentPadding
    when (val content = state.content) {
        ChatListContent.Loading -> {
            val loading = stringResource(R.string.chat_list_loading)
            Box(modifier.padding(contentPadding), contentAlignment = Alignment.Center) {
                DelayedLoadingIndicator(Modifier.semantics { contentDescription = loading })
            }
        }

        is ChatListContent.Empty -> {
            ChatListEmptyWithArchive(
                archive = state.archive,
                kind = state.kind,
                isFolder = content.isFolder,
                onEvent = onEvent,
                modifier = modifier.padding(contentPadding),
            ) { if (state.hasLockedChats) LockedChatsRow(enabled = true) }
        }

        is ChatListContent.Error -> {
            ChatListError(
                content = content,
                onRetry = { onEvent(ChatListEvent.Retry) },
                modifier = modifier.padding(contentPadding),
            )
        }

        is ChatListContent.Content -> {
            ChatList(
                items = content.items,
                pinnedCount = content.pinnedCount,
                state = state,
                stories = args.stories,
                listState = args.listState,
                contentPadding = contentPadding,
                openChatId = args.openChatId,
                active = args.active,
                onEvent = onEvent,
                modifier = modifier,
            )
        }
    }
}

/**
 * The story rings ([stories]) and the archive row of [state], if any, then its chats ([items]), keyed by id, each row
 * animating to its new place when the order changes and fading out when it leaves (archived, deleted). Outside
 * selection mode, [openChatId]'s row is open. While it is the list shown ([active]), it reports to
 * [ChatListEvent.NearEndChanged] whether the end is within [LOAD_MORE_THRESHOLD] rows: at once, on every change, afresh
 * for another folder, and false when the list leaves the screen or stops being shown. A row that comes first while the
 * list shows its top (the rings, the archive row, a chat that moved up or loaded above) is scrolled into view
 * ([KeepTopShown]).
 */
@Suppress("LongParameterList") // The rows, the selection and open chat they show, and the list's state and padding.
@Composable
private fun ChatList(
    items: ImmutableList<ChatListItemModel>,
    pinnedCount: Int,
    state: ChatListUiState,
    stories: (@Composable (Modifier) -> Unit)?,
    listState: LazyListState,
    contentPadding: PaddingValues,
    openChatId: Long?,
    active: Boolean,
    onEvent: (ChatListEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val archive = state.archive
    val selection = state.selection
    val folderId = state.selectedFolderId
    val selectedIds = selection?.chatIds
    val maxWidth = MaterialTheme.sizes.contentMaxWidth
    val rowMotion = MaterialTheme.motionScheme
    LazyColumn(
        modifier = modifier.testTag(CHAT_LIST_TEST_TAG),
        state = listState,
        contentPadding = contentPadding,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        leadingRows(stories, state, hasPinned = pinnedCount > 0, onEvent) {
            Modifier.listRow(this, maxWidth, rowMotion)
        }
        itemsIndexed(items, key = { _, item -> item.id }, contentType = { _, _ -> CHAT_ROW }) { index, item ->
            ChatRow(
                item = item,
                selected = selectedIds?.contains(item.id) == true,
                selectionMode = selection != null,
                open = selection == null && item.id == openChatId,
                onEvent = onEvent,
                segment = if (index < pinnedCount) RowSegment(index, pinnedCount) else null,
                modifier =
                    Modifier
                        .listRow(this, maxWidth, rowMotion)
                        .then(if (item.isSavedMessages) Modifier.testTag(SAVED_MESSAGES_TEST_TAG) else Modifier),
            )
        }
        if (state.hasLockedChats) lockedChatsRow(selection == null) { Modifier.listRow(this, maxWidth, rowMotion) }
    }
    KeepTopShown(
        listState,
        firstKey =
            when {
                stories != null -> STORIES_ROW
                archive != null -> ARCHIVE_ROW
                pinnedCount > 0 -> PINNED_HEADER
                else -> items.firstOrNull()?.id
            },
    )
    val currentOnEvent by rememberUpdatedState(onEvent)
    LaunchedEffect(listState, folderId, active) {
        if (!active) return@LaunchedEffect
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

/**
 * The empty archive, folder or Chats tab; the Chats tab's has a "New chat" button ([onNewChat]), the screen's next
 * step (`docs/DESIGN_SYSTEM.md` §6, Empty states).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ChatListEmpty(
    kind: ChatListKind,
    isFolder: Boolean,
    onNewChat: () -> Unit,
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
                action = {
                    Button(onClick = onNewChat, shapes = ButtonDefaults.shapes()) {
                        Text(
                            text = stringResource(R.string.new_chat),
                            style = MaterialTheme.typography.labelLargeEmphasized,
                        )
                    }
                },
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
        // A soft shape: errors are no alarm (`docs/DESIGN_SYSTEM.md` §4).
        illustration = MaterialShapes.Cookie4Sided,
        icon = UiR.drawable.ic_error,
        title = stringResource(R.string.chat_list_error_title),
        body = errorText(content.error),
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.errorContainer,
        iconColor = MaterialTheme.colorScheme.onErrorContainer,
        action = {
            Button(onClick = onRetry, shapes = ButtonDefaults.shapes()) {
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

// The Macrobenchmarks and the Baseline Profile generator (`:benchmark`, `Journeys.kt`) find the list and the row they
// scroll and open by these tags, which `MainActivity` shows as resource ids.
private const val CHAT_LIST_TEST_TAG = "chat_list"
private const val SAVED_MESSAGES_TEST_TAG = "saved_messages"

@PingogramPreviews
@PingogramVariantPreviews
@Composable
private fun ChatListScreenPreview(
    @PreviewParameter(ChatListPreviewStates::class) state: ChatListUiState,
) {
    PingogramPreview {
        ChatListScreen(state = state, onEvent = {}, summary = { ChatListPreviewData.summary }, onEditFolder = {})
    }
}
