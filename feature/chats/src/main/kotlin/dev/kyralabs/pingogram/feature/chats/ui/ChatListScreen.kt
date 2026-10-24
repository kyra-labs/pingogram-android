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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
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
import dev.kyralabs.pingogram.core.ui.component.ContentSheet
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.component.EmptyState
import dev.kyralabs.pingogram.core.ui.component.HideNavigationBar
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
import dev.kyralabs.pingogram.core.ui.theme.FADE_THROUGH_INITIAL_SCALE
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chats.R
import dev.kyralabs.pingogram.feature.chats.ui.preview.ChatListPreviewStates
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * A chat list (`REQUIREMENTS.md` FR-L1–L5, FR-L8, FR-L9; `docs/DESIGN_SYSTEM.md` §6): the Chats tab ([ChatListTopBar])
 * or the archive ([ArchiveTopBar]), each with a small, pinned top bar; then the chats, each with its swipe actions
 * ([ChatRow]), below the archive row on the main list. The bar sits on `surfaceContainer` and the list on a rounded
 * `surface` sheet below it ([ContentSheet]), which the rows scroll in.
 *
 * - **Insets**: on the Chats tab the list draws behind the floating navigation bar and ends clear of it
 *   ([screenContentWindowInsets]); the archive, pushed on top of the tab where the bar hides, keeps clear of the system
 *   bars only. On medium and wider windows the rows keep a comfortable width, centered.
 * - **Paging**: it reports whether its end is within [LOAD_MORE_THRESHOLD] rows, so more chats load.
 * - **Folders**: selecting another folder replaces the list with that folder's, through Material's fade-through, where
 *   the user left it, or at its top the first time ([rememberShownList]).
 * - **Selection mode**: the floating navigation bar steps aside ([HideNavigationBar]) for the [SelectionToolbar] in
 *   its place, and the list ends clear of the toolbar on every window size. Back (predictive, with the toolbar
 *   following the gesture) and the top bar's close button leave it.
 * - **Back to the top** (Chats tab only): tapping the Chats tab while it is selected scrolls the list back to its top
 *   ([ScrollToTopOnReselect]).
 * - **New chat** (Chats tab only): the [NewChatButton] at the bottom end, above the floating navigation bar or in the
 *   list pane, collapsing while the list scrolls towards its end; it hides in selection mode. The list's end keeps
 *   clear of it. "No chats yet" has a button for the same screen.
 * - **Snackbars** show above the floating bar or toolbar, and the "New chat" button; the delete confirmation is a
 *   dialog ([DeleteChatDialog]).
 * - **Open chat**: beside the conversation of [openChatId] (the list pane on expanded windows), that chat's row looks
 *   and reads as selected ([ChatListItem]); not in selection mode, whose own selection the toolbar acts on.
 * - **Header**: [header] shows at the top of the sheet, above the list, which does not scroll it away: the request to
 *   allow notifications ([NotificationPromptHost]) on the Chats tab.
 * - **Stories**: [stories], where there are any, is the list's first row, above the archive row, and scrolls away with
 *   the chats: the story rings on the Chats tab (`StoryRingsRow`). One item of the list, so scrolling the chats costs
 *   what it did. A row that appears above the first one while the list shows its top is scrolled into view.
 */
@Composable
internal fun ChatListScreen(
    state: ChatListUiState,
    onEvent: (ChatListEvent) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    listState: LazyListState = rememberScrollingListState(),
    onBack: () -> Unit = {},
    openChatId: Long? = null,
    header: @Composable () -> Unit = {},
    stories: (@Composable (Modifier) -> Unit)? = null,
) {
    val selection = state.selection
    HideNavigationBar(hidden = selection != null)
    val backProgress = rememberSelectionBack(enabled = selection != null) { onEvent(ChatListEvent.ClearSelection) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val folderSwitch = remember { FolderSwitch() }
    val newChatButton = rememberNewChatButtonState()
    val insets = contentWindowInsets(state.kind, selectionMode = selection != null)
    // The room the "New chat" button takes above the bottom: the button and the 16 dp below it; none in the archive.
    val newChatButtonSpace =
        if (state.kind == ChatListKind.Chats) MaterialTheme.sizes.fabSmall + MaterialTheme.spacing.large else 0.dp
    ScrollToTopOnReselect(ChatsRoute, listState, enabled = state.kind == ChatListKind.Chats)
    Box(modifier) {
        Scaffold(
            modifier =
                Modifier
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .nestedScroll(newChatButton.nestedScrollConnection),
            // The top bar's container; the list sits on a sheet below it (ContentSheet).
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            topBar = { ChatListScreenTopBar(state, scrollBehavior, onEvent, onBack) },
            snackbarHost = {
                // Above the "New chat" button, which floats over the Scaffold rather than in its button slot.
                SnackbarHost(snackbarHostState, Modifier.padding(bottom = newChatButtonSpace))
            },
            contentWindowInsets = insets,
        ) { innerPadding ->
            ChatListBody(state, onEvent, listState, innerPadding, openChatId, folderSwitch, header, stories)
        }
        if (state.kind == ChatListKind.Chats) {
            // Over the Scaffold: in its button slot, every frame of the button's width animation (collapsing while
            // the list scrolls) measured the whole Scaffold again.
            NewChatButton(
                state = newChatButton,
                visible = selection == null,
                onClick = { onEvent(ChatListEvent.NewChat) },
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .windowInsetsPadding(insets.only(WindowInsetsSides.Bottom + WindowInsetsSides.End))
                        .padding(MaterialTheme.spacing.large),
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
 * The list of the folder shown ([rememberShownList]) on its sheet, fading through on a folder switch ([folderSwitch]).
 * Its own composable, which skips while its arguments stay the same: the `Scaffold` composes its content again whenever
 * it measures it anew, e.g. when the connection pill or the folder tabs change the top bar's height, and those
 * frames stop here.
 */
@Suppress("LongParameterList") // The state, events, list, padding, open chat, folder switch, header, stories.
@Composable
private fun ChatListBody(
    uiState: ChatListUiState,
    onEvent: (ChatListEvent) -> Unit,
    listState: LazyListState,
    innerPadding: PaddingValues,
    openChatId: Long?,
    folderSwitch: FolderSwitch,
    header: @Composable () -> Unit,
    stories: (@Composable (Modifier) -> Unit)?,
) {
    val state = rememberShownList(uiState, listState, folderSwitch, hasStories = stories != null)
    val animateRows = folderSwitch.animateRows
    val swipesChangeFolder = LocalChatSwipes.current.changesFolder && uiState.kind == ChatListKind.Chats
    val neighbours =
        remember(uiState.folderTabs, uiState.selectedFolderId) {
            FolderNeighbours.of(uiState.folderTabs, uiState.selectedFolderId)
        }
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    ContentSheet(innerPadding, Modifier.consumeWindowInsets(innerPadding)) { sheetPadding ->
        val contentPadding = rememberClearOfNewChatButton(sheetPadding, state.kind)
        Column(Modifier.fillMaxSize()) {
            // Takes no room while it shows nothing.
            Box(Modifier.fillMaxWidth().padding(contentPadding.sidesOnly()), Alignment.TopCenter) { header() }
            Box(
                Modifier.weight(1f).folderSwipe(
                    enabled = swipesChangeFolder && uiState.selection == null && uiState.folderTabs.isNotEmpty(),
                    folderSwitch = folderSwitch,
                    neighbours = neighbours,
                    onSelect = { onEvent(ChatListEvent.SelectFolder(it)) },
                ),
            ) {
                val contentModifier = Modifier.fadeThrough(folderSwitch, isRtl).fillMaxSize()
                when (val content = state.content) {
                    ChatListContent.Loading -> {
                        val loading = stringResource(R.string.chat_list_loading)
                        Box(contentModifier.padding(contentPadding), contentAlignment = Alignment.Center) {
                            DelayedLoadingIndicator(Modifier.semantics { contentDescription = loading })
                        }
                    }

                    is ChatListContent.Empty -> {
                        Column(contentModifier.padding(contentPadding)) {
                            // The archive stays in reach when every chat is archived.
                            state.archive?.let {
                                ArchiveRow(it, enabled = true, onClick = { onEvent(ChatListEvent.OpenArchive) })
                            }
                            ChatListEmpty(
                                kind = state.kind,
                                isFolder = content.isFolder,
                                onNewChat = { onEvent(ChatListEvent.NewChat) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    is ChatListContent.Error -> {
                        ChatListError(
                            content = content,
                            onRetry = { onEvent(ChatListEvent.Retry) },
                            modifier = contentModifier.padding(contentPadding),
                        )
                    }

                    is ChatListContent.Content -> {
                        ChatList(
                            items = content.items,
                            state = state,
                            stories = stories,
                            listState = listState,
                            contentPadding = contentPadding,
                            openChatId = openChatId,
                            animateRows = animateRows,
                            onEvent = onEvent,
                            modifier = contentModifier,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The fade-through between the lists of two folders (`docs/DESIGN_SYSTEM.md` §5): the list shown fades out, and the
 * selected folder's list fades in while it grows from [FADE_THROUGH_INITIAL_SCALE] ([rememberShownList]). Read in the
 * draw phase ([fadeThrough]), so the animation never recomposes.
 *
 * The rows do not animate ([animateRows]) while the selected folder's rows replace the others: the two lists have
 * nothing to animate between, and the list would otherwise slide the chats both lists have from where the old one
 * showed them, and fade the others in and out, all started in the frame that composes the new rows.
 */
@Stable
internal class FolderSwitch {
    val alpha = Animatable(1f)
    val scale = Animatable(1f)
    var animateRows by mutableStateOf(true)

    /** How far the finger moved the list toward the end, in pixels, while it swipes it ([folderSwipe]). */
    var drag by mutableFloatStateOf(0f)

    /** The list's movement toward the end after the finger let go, in pixels: sliding away, in or back. */
    val offset = Animatable(0f)

    /** Where the list is, toward the end of [drag] and [offset]; read in the draw phase. */
    val position: Float
        get() = drag + offset.value

    /**
     * Where the selected folder's list slides in from (an [offset]) after a swipe selected it; null for a tab's tap,
     * which fades through. Read once by [rememberShownList].
     */
    var slideInFrom: Float? = null
}

/**
 * Fades each drawing of the list on its own ([CompositingStrategy.ModulateAlpha]) rather than the list as a whole in
 * an offscreen buffer the size of the screen: the rows draw opaque containers in the screen's own color, so the two
 * look the same.
 */
private fun Modifier.fadeThrough(
    folderSwitch: FolderSwitch,
    isRtl: Boolean,
): Modifier =
    graphicsLayer {
        alpha = folderSwitch.alpha.value
        scaleX = folderSwitch.scale.value
        scaleY = folderSwitch.scale.value
        translationX = if (isRtl) -folderSwitch.position else folderSwitch.position
        compositingStrategy = CompositingStrategy.ModulateAlpha
    }

/**
 * The state whose list shows: [state], except while another folder's list fades out after a folder was selected.
 * Material's fade-through runs in sequence, so the new list never shows over the old one: the old list fades out on
 * the motion scheme's fast effects spring, then the selected folder's list replaces it where the user left it
 * ([FolderPositions]), and fades in on the default effects spring while it grows on the default spatial spring. The
 * tabs follow the selection at once. Selecting yet another folder meanwhile carries on from where the animation is;
 * selecting the folder shown again fades it back in. With animations off (reduced motion) the lists swap without
 * fading: ending the springs at once, as Compose does then, would still blank the list for the frames between them.
 *
 * The folder shown and where each folder was left are saved, so a rotation or process death keeps them.
 */
@Composable
private fun rememberShownList(
    state: ChatListUiState,
    listState: LazyListState,
    folderSwitch: FolderSwitch,
    hasStories: Boolean,
): ChatListUiState {
    var shownFolderId by rememberSaveable { mutableStateOf(state.selectedFolderId) }
    val positions = rememberSaveable(saver = FolderPositions.Saver) { FolderPositions() }
    // The last state of the folder shown, for its list to fade out with; read only then, so updating it recomposes
    // nothing.
    val lastShown = remember { mutableStateOf(state) }
    val isShown = state.selectedFolderId == shownFolderId
    SideEffect { if (isShown) lastShown.value = state }
    val currentState by rememberUpdatedState(state)
    val currentHasStories by rememberUpdatedState(hasStories)
    val motionScheme = MaterialTheme.motionScheme
    LaunchedEffect(state.selectedFolderId) {
        // The system's animator duration scale, which Compose applies to every animation; 0 is reduced motion.
        val animated = coroutineContext[MotionDurationScale]?.scaleFactor != 0f
        // A swipe that selected the folder: the old list slid away already, and the new one slides in instead of fading.
        val slideInFrom = folderSwitch.slideInFrom
        folderSwitch.slideInFrom = null
        if (state.selectedFolderId != shownFolderId) {
            if (slideInFrom == null) {
                if (animated) folderSwitch.alpha.animateTo(0f, motionScheme.fastEffectsSpec())
                // A tab tapped while the list was swiped part of the way.
                folderSwitch.offset.snapTo(0f)
            }
            positions.save(shownFolderId, listState)
            folderSwitch.animateRows = false
            shownFolderId = state.selectedFolderId
            // Applied in the layout pass that brings the selected folder's rows, so the list does not keep the row
            // that was first in the old list.
            positions.restore(currentState, currentHasStories, listState)
            when {
                slideInFrom != null -> folderSwitch.offset.snapTo(if (animated) slideInFrom else 0f)
                animated -> folderSwitch.scale.snapTo(FADE_THROUGH_INITIAL_SCALE)
            }
        }
        coroutineScope {
            // Rows animate again once the selected folder's rows are laid out, in the frame that composes them.
            if (!folderSwitch.animateRows) {
                launch {
                    repeat(ROWS_LAYOUT_FRAMES) { withFrameNanos {} }
                    folderSwitch.animateRows = true
                }
            }
            launch { folderSwitch.offset.animateTo(0f, motionScheme.defaultSpatialSpec()) }
            launch { folderSwitch.scale.animateTo(1f, motionScheme.defaultSpatialSpec()) }
            folderSwitch.alpha.animateTo(1f, motionScheme.defaultEffectsSpec())
        }
    }
    return if (isShown) state else lastShown.value
}

/**
 * Where the user left each folder's list (`null` for "All chats"): its first visible row and how far it is scrolled
 * past it. A row is found again by its key, wherever it moved since; one that left the list gives way to the row now at
 * its index. A folder not shown before starts at its top. Not observed: only the folder switch reads and writes it.
 */
private class FolderPositions(
    private val positions: MutableMap<Int?, ListPosition> = HashMap(),
) {
    /** Keeps where [folderId]'s list, shown by [listState], is now. */
    fun save(
        folderId: Int?,
        listState: LazyListState,
    ) {
        val index = listState.firstVisibleItemIndex
        // Not the first of visibleItemsInfo, which may be a row drawn in the content padding behind the top bar.
        val first = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
        positions[folderId] = ListPosition(first.key, index, listState.firstVisibleItemScrollOffset)
    }

    /**
     * Puts the list of [state]'s folder where it was left, or at its top, in the next layout pass; the story rings
     * ([hasStories]) and the archive row come first.
     */
    fun restore(
        state: ChatListUiState,
        hasStories: Boolean,
        listState: LazyListState,
    ) {
        val position = positions[state.selectedFolderId]
        if (position == null) {
            listState.requestScrollToItem(0)
            return
        }
        val items = (state.content as? ChatListContent.Content)?.items.orEmpty()
        val storyRows = if (hasStories) 1 else 0
        val rowsAbove = storyRows + if (state.archive != null) 1 else 0
        val index =
            when (val key = position.key) {
                STORIES_ROW -> 0.takeIf { hasStories }
                ARCHIVE_ROW -> storyRows.takeIf { state.archive != null }
                else -> items.indexOfFirst { it.id == key }.takeIf { it >= 0 }?.plus(rowsAbove)
            }
        if (index != null) {
            listState.requestScrollToItem(index, position.offset)
        } else {
            listState.requestScrollToItem(position.index)
        }
    }

    companion object {
        /** Each position as four values: the folder ([NO_FOLDER] for "All chats"), the row's key, index and offset. */
        val Saver =
            listSaver<FolderPositions, Any>(
                save = { saved ->
                    saved.positions.flatMap { (folderId, position) ->
                        listOf(folderId ?: NO_FOLDER, position.key, position.index, position.offset)
                    }
                },
                restore = { values ->
                    val positions =
                        values.chunked(VALUES_PER_POSITION).associateTo(HashMap()) { saved ->
                            val folderId = (saved[0] as Int).takeIf { it != NO_FOLDER }
                            folderId to ListPosition(key = saved[1], index = saved[2] as Int, offset = saved[3] as Int)
                        }
                    FolderPositions(positions)
                },
            )

        /** Stands for "All chats" when saved; TDLib's folder ids are positive. */
        private const val NO_FOLDER = -1
        private const val VALUES_PER_POSITION = 4
    }
}

/**
 * A list's first visible row: its [key] (a chat id, [STORIES_ROW] or [ARCHIVE_ROW]), its [index], and the [offset]
 * scrolled past it.
 */
private class ListPosition(
    val key: Any,
    val index: Int,
    val offset: Int,
)

/**
 * The story rings ([stories]) and the archive row of [state], if any, then its chats ([items]), keyed by id, each row
 * animating to its new place when the order changes and fading out when it leaves (archived, deleted). Outside
 * selection mode, [openChatId]'s row is open. Reports to [ChatListEvent.NearEndChanged]
 * whether the end is within [LOAD_MORE_THRESHOLD] rows: at once, on every change, afresh for another folder, and false
 * when the list leaves the screen. A row that appears above the first one (the rings, the archive row) while the list
 * shows its top is scrolled into view.
 */
@Suppress("LongParameterList") // The rows, the selection and open chat they show, and the list's state and padding.
@Composable
private fun ChatList(
    items: ImmutableList<ChatListItemModel>,
    state: ChatListUiState,
    stories: (@Composable (Modifier) -> Unit)?,
    listState: LazyListState,
    contentPadding: PaddingValues,
    openChatId: Long?,
    animateRows: Boolean,
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
        modifier = modifier.testTag(CHAT_LIST_TEST_TAG),
        state = listState,
        contentPadding = contentPadding,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (stories != null) {
            item(key = STORIES_ROW, contentType = STORIES_ROW) {
                stories(Modifier.listRow(this, maxWidth, motionScheme.takeIf { animateRows }))
            }
        }
        if (archive != null) {
            item(key = ARCHIVE_ROW, contentType = ARCHIVE_ROW) {
                ArchiveRow(
                    entry = archive,
                    enabled = selection == null,
                    onClick = { onEvent(ChatListEvent.OpenArchive) },
                    modifier = Modifier.listRow(this, maxWidth, motionScheme.takeIf { animateRows }),
                )
            }
        }
        items(items, key = { it.id }, contentType = { CHAT_ROW }) { item ->
            ChatRow(
                item = item,
                selected = selectedIds?.contains(item.id) == true,
                selectionMode = selection != null,
                open = selection == null && item.id == openChatId,
                onEvent = onEvent,
                modifier =
                    Modifier
                        .listRow(this, maxWidth, motionScheme.takeIf { animateRows })
                        .then(if (item.isSavedMessages) Modifier.testTag(SAVED_MESSAGES_TEST_TAG) else Modifier),
            )
        }
    }
    ShowRowsAddedAtTop(listState, rowsAbove = (if (stories != null) 1 else 0) + (if (archive != null) 1 else 0))
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

/**
 * A row of [scope] at most [maxWidth] wide, animating as the list changes on the [motionScheme]'s springs; without one,
 * changes show at once.
 */
private fun Modifier.listRow(
    scope: LazyItemScope,
    maxWidth: Dp,
    motionScheme: MotionScheme?,
): Modifier =
    with(scope) {
        this@listRow
            .widthIn(max = maxWidth)
            .fillMaxWidth()
            .animateItem(
                fadeInSpec = motionScheme?.defaultEffectsSpec(),
                placementSpec = motionScheme?.defaultSpatialSpec(),
                fadeOutSpec = motionScheme?.fastEffectsSpec(),
            )
    }

/**
 * The empty archive, folder or Chats tab; the Chats tab's has a "New chat" button ([onNewChat]), the screen's next
 * step (`docs/DESIGN_SYSTEM.md` §6, Empty states).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChatListEmpty(
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

/**
 * How many frames rows do not animate for after a folder switch: enough for the frame that composes and lays out the
 * selected folder's rows, whether the switch is made during a frame (after the fade-out) or between two (without it).
 */
private const val ROWS_LAYOUT_FRAMES = 2

/** How close to its end the list asks for more chats: about a page of rows ahead of the user. */
private const val LOAD_MORE_THRESHOLD = 20

private const val CHAT_ROW = "chat"
private const val ARCHIVE_ROW = "archive"
private const val STORIES_ROW = "stories"

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
    PingogramPreview { ChatListScreen(state = state, onEvent = {}) }
}
