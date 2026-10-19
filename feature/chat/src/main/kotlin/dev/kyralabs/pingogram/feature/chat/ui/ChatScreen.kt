package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Dp
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.domain.model.SendOptions
import dev.kyralabs.pingogram.core.ui.component.ContentSheet
import dev.kyralabs.pingogram.core.ui.component.EmptyState
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.attach.AttachMenuSheet
import dev.kyralabs.pingogram.feature.chat.ui.attach.AttachOption
import dev.kyralabs.pingogram.feature.chat.ui.attach.AttachPreviewSheet
import dev.kyralabs.pingogram.feature.chat.ui.attach.MessageUpload
import dev.kyralabs.pingogram.feature.chat.ui.attach.PollEditor
import dev.kyralabs.pingogram.feature.chat.ui.attach.PollFormSheet
import dev.kyralabs.pingogram.feature.chat.ui.composer.ChatBottomBar
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerBar
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerFocusState
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerText
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerUiState
import dev.kyralabs.pingogram.feature.chat.ui.composer.rememberComposerFocusState
import dev.kyralabs.pingogram.feature.chat.ui.content.Playback
import dev.kyralabs.pingogram.feature.chat.ui.content.PreviewImages
import dev.kyralabs.pingogram.feature.chat.ui.content.downloadableFileId
import dev.kyralabs.pingogram.feature.chat.ui.content.isBare
import dev.kyralabs.pingogram.feature.chat.ui.content.isEdgeToEdge
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewStates
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The conversation (`REQUIREMENTS.md` FR-C1–C3, FR-C5, FR-C8; `docs/DESIGN_SYSTEM.md` §5–6): the [ChatTopBar] on
 * `surfaceContainer` over the messages, newest at the bottom, on a rounded `surface` sheet ([ContentSheet]) that they
 * scroll in, and the composer below them ([ChatBottomBar]), above the keyboard and the system bars.
 *
 * - **List**: a reverse-layout `LazyColumn` with a key and a content type per entry, each animating to its place on
 *   the motion scheme's springs, so a new message comes in from the bottom. It reports whether the oldest message is
 *   within [LOAD_MORE_THRESHOLD] entries ([ChatEvent.NearOldestChanged]) and which messages it shows
 *   ([ChatEvent.MessagesVisible]), and opens at the unread divider, near the top, if there is one, else at the newest
 *   message; a rotation keeps the position.
 * - **Date pill**: while the list scrolls, the date of the topmost message floats at the top, fading in and out on the
 *   motion scheme's effects springs, read in the draw phase.
 * - **Jump to bottom**: a small FAB, with the unread count as badge, springs in once the newest message is out of
 *   view, and scrolls back to it.
 * - **Jump to a message** ([jumpState]): the list scrolls to a tapped quote's message, or to the message the
 *   conversation was opened at, centred, and highlights it ([JumpToMessages]). Opened at a message
 *   ([MessageJumpState.opensAtMessage]), the list does not scroll to the unread divider first, so the two never pull
 *   the list both ways.
 * - **States**: a loading indicator, an empty state, an error with retry, and a chat that does not exist.
 * - **Composer** ([composerText], the view model's): where the user can write, whatever the messages' state; sending a
 *   message now scrolls to the newest one, where it shows. Where they cannot, a label says why; in a channel they only
 *   read, there is nothing. Its reply or edit bar shows its message in the list when tapped, as a reply quote does;
 *   [composerFocus] gives its text field the focus when the user starts a reply or an edit.
 * - **Message actions**: a long press on a bubble opens its actions sheet ([MessageActionsBottomSheet]), deleting asks
 *   first ([DeleteMessagesAlert]); tapping a bubble's reaction chip toggles the user's reaction.
 * - **Attach menu**: the composer's attach button opens it ([AttachMenuSheet]), without the camera where [hasCamera]
 *   is false; the option chosen goes to [onAttach], which opens its picker. Picked photos, videos and files show in
 *   their preview ([AttachPreviewSheet], with [attachCaption]), a new poll in its form ([PollFormSheet], editing
 *   [pollEditor]); sending them now scrolls to the newest message, as sending a text does. Bubbles being sent show
 *   their uploads ([ChatUiState.uploads]).
 *
 * [onBack] is the top bar's back button; null hides it, beside the chat list on expanded windows.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ChatScreen(
    state: ChatUiState,
    onEvent: (ChatEvent) -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    jumpState: MessageJumpState = rememberMessageJumpState(),
    composerText: ComposerText = remember { ComposerText() },
    composerFocus: ComposerFocusState = rememberComposerFocusState(),
    onAttach: (AttachOption) -> Unit = {},
    hasCamera: Boolean = true,
    attachCaption: ComposerText = remember { ComposerText() },
    pollEditor: PollEditor = remember { PollEditor() },
) {
    val content = state.content
    val showJump by remember(listState) { derivedStateOf { listState.canScrollBackward } }
    val scope = rememberCoroutineScope()
    val motionScheme = MaterialTheme.motionScheme
    var attachMenuOpen by rememberSaveable { mutableStateOf(false) }
    val scrollToSent = { options: SendOptions ->
        // A message sent now goes to the bottom of the list: bring it into view. Scheduled ones do not.
        if (options.scheduledAt == null && content is ChatContent.Content) {
            scope.launch { listState.scrollToNewest(motionScheme.defaultSpatialSpec()) }
        }
    }
    Scaffold(
        modifier = modifier,
        topBar = { ChatTopBar(state.header, onBack) },
        bottomBar = {
            val editing = (state.composer as? ComposerUiState.Available)?.bar is ComposerBar.Edit
            ChatBottomBar(
                composer = state.composer,
                text = composerText,
                onSend = { options ->
                    onEvent(ChatEvent.Send(options))
                    // A message sent now goes to the bottom of the list: bring it into view. Scheduled ones and edits
                    // do not.
                    if (options.scheduledAt == null && !editing && content is ChatContent.Content) {
                        scope.launch { listState.scrollToNewest(motionScheme.defaultSpatialSpec()) }
                    }
                },
                onFocusChanged = { onEvent(ChatEvent.ComposerFocusChanged(it)) },
                focus = composerFocus,
                onShowBarMessage = { onEvent(ChatEvent.OpenReply(it)) },
                onCloseBar = { onEvent(if (editing) ChatEvent.CancelEdit else ChatEvent.CancelReply) },
                onAttach = { attachMenuOpen = true },
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            JumpToBottomButton(
                visible = content is ChatContent.Content && showJump,
                unreadCount = state.unreadCount,
                motionScheme = motionScheme,
                onClick = { scope.launch { listState.scrollToNewest(motionScheme.defaultSpatialSpec()) } },
            )
        },
    ) { innerPadding ->
        ContentSheet(innerPadding, Modifier.consumeWindowInsets(innerPadding)) { contentPadding ->
            ChatBody(state, onEvent, listState, jumpState, contentPadding)
        }
    }
    state.messageActions?.let { MessageActionsBottomSheet(it, onEvent) }
    val attachOptions = (state.composer as? ComposerUiState.Available)?.attachOptions.orEmpty()
    if (attachMenuOpen && attachOptions.isNotEmpty()) {
        AttachMenuSheet(
            options = attachOptions.filter { hasCamera || it != AttachOption.Camera }.toImmutableList(),
            onPick = { option ->
                attachMenuOpen = false
                onAttach(option)
            },
            onDismiss = { attachMenuOpen = false },
        )
    }
    state.attachPreview?.let { preview ->
        AttachPreviewSheet(
            preview = preview,
            caption = attachCaption,
            onEvent = onEvent,
            onSend = { options ->
                onEvent(ChatEvent.SendAttachments(options))
                scrollToSent(options)
            },
        )
    }
    state.poll?.let { sheet ->
        PollFormSheet(
            sheet = sheet,
            editor = pollEditor,
            onEvent = { event ->
                onEvent(event)
                if (event is ChatEvent.SendPoll) scrollToSent(event.options)
            },
        )
    }
    state.deleteDialog?.let { dialog ->
        DeleteMessagesAlert(
            dialog = dialog,
            onConfirm = { onEvent(ChatEvent.ConfirmDelete(it)) },
            onDismiss = { onEvent(ChatEvent.DismissDelete) },
        )
    }
}

/** What the sheet shows for [state]'s content: progress, an empty or error state, or the messages. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BoxScope.ChatBody(
    state: ChatUiState,
    onEvent: (ChatEvent) -> Unit,
    listState: LazyListState,
    jumpState: MessageJumpState,
    contentPadding: PaddingValues,
) {
    val bodyModifier = Modifier.fillMaxSize().padding(contentPadding)
    when (val content = state.content) {
        ChatContent.Loading -> {
            Box(bodyModifier, contentAlignment = Alignment.Center) { LoadingIndicator() }
        }

        ChatContent.Empty -> {
            ChatEmpty(bodyModifier)
        }

        is ChatContent.Error -> {
            ChatError(content, onRetry = { onEvent(ChatEvent.Retry) }, modifier = bodyModifier)
        }

        ChatContent.NotFound -> {
            ChatNotFound(bodyModifier)
        }

        is ChatContent.Content -> {
            MessageList(state, content, listState, jumpState, contentPadding, onEvent)
            FloatingDatePill(
                items = content.items,
                listState = listState,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = MaterialTheme.spacing.small),
            )
        }
    }
}

/**
 * The entries of [content], newest first in a reverse layout, then the state of older pages above the oldest one.
 * Each entry animates to its new place and fades in and out on the motion scheme's springs.
 */
@Suppress("LongParameterList") // What the list shows, its state and the sheet's padding, all from ChatScreen.
@Composable
private fun MessageList(
    state: ChatUiState,
    content: ChatContent.Content,
    listState: LazyListState,
    jumpState: MessageJumpState,
    contentPadding: PaddingValues,
    onEvent: (ChatEvent) -> Unit,
) {
    val fileStates = state.fileStates
    val uploads = state.uploads
    val playback = state.playback
    val theme = rememberBubbleTheme()
    val texts = remember { AnnotatedTextCache() }
    val onLinkClick = rememberLinkOpener()
    val reactions = rememberReactionToggleState()
    val shortcuts = state.shortcuts
    val bubbleResources =
        remember(theme, texts, onLinkClick, jumpState, reactions, shortcuts) {
            BubbleResources(theme, texts, onLinkClick, jumpState, reactions, shortcuts)
        }
    val motionScheme = MaterialTheme.motionScheme
    val spacing = MaterialTheme.spacing
    val layoutDirection = LocalLayoutDirection.current
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(MESSAGE_LIST_TEST_TAG),
        state = listState,
        contentPadding =
            PaddingValues(
                start = contentPadding.calculateStartPadding(layoutDirection) + spacing.medium,
                top = contentPadding.calculateTopPadding(),
                end = contentPadding.calculateEndPadding(layoutDirection) + spacing.medium,
                bottom = contentPadding.calculateBottomPadding() + spacing.small,
            ),
        reverseLayout = true,
    ) {
        items(content.items, key = { it.key }, contentType = { it.contentType }) { item ->
            val itemModifier = Modifier.entry(this, motionScheme, item.topSpacing(spacing.small, spacing.extraSmall))
            ChatItemRow(item, fileStates, uploads, playback, bubbleResources, onEvent, itemModifier)
        }
        if (content.isLoadingOlder || content.olderError != null) {
            item(key = OLDER_KEY, contentType = OLDER_KEY) {
                OlderMessagesState(
                    content = content,
                    onRetry = { onEvent(ChatEvent.Retry) },
                    modifier = Modifier.entry(this, motionScheme, spacing.small),
                )
            }
        }
    }
    InitialScroll(content.items, listState, toUnread = !jumpState.opensAtMessage)
    FollowNewestMessage(content.items.firstOrNull()?.key, listState)
    ReportVisibleMessages(content.items, listState, onEvent)
    JumpToMessages(content.items, listState, jumpState, motionScheme)
    val currentOnEvent by rememberUpdatedState(onEvent)
    LaunchedEffect(listState) {
        try {
            snapshotFlow {
                val layout = listState.layoutInfo
                val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
                layout.totalItemsCount > 0 && lastVisible >= layout.totalItemsCount - 1 - LOAD_MORE_THRESHOLD
            }.distinctUntilChanged()
                .collect { currentOnEvent(ChatEvent.NearOldestChanged(it)) }
        } finally {
            // Leaving the screen, or the list.
            currentOnEvent(ChatEvent.NearOldestChanged(false))
        }
    }
}

/**
 * One entry of the list: a message with the state of its file, the uploads of its messages being sent and its playback
 * (only the message in the player has one), a service message, a date or the unread divider.
 */
@Suppress("LongParameterList") // The entry, the states that change apart from it, and what the bubbles share.
@Composable
private fun ChatItemRow(
    item: ChatItem,
    fileStates: ImmutableMap<Int, FileState>,
    uploads: ImmutableMap<Long, MessageUpload>,
    playback: MessagePlayback?,
    bubbleResources: BubbleResources,
    onEvent: (ChatEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (item) {
        is ChatItem.Bubble -> {
            val fileState = item.content.downloadableFileId?.let(fileStates::get)
            val bubblePlayback = playback?.takeIf { it.messageId == item.id }?.playback ?: Playback.Stopped
            MessageBubbleRow(
                bubble = item,
                fileState = fileState,
                playback = bubblePlayback,
                resources = bubbleResources,
                onEvent = onEvent,
                modifier = modifier,
                uploads = item.uploadsIn(uploads),
            )
        }

        is ChatItem.Service -> {
            ServiceMessageRow(item, modifier)
        }

        is ChatItem.DateSeparator -> {
            DateSeparatorRow(item, modifier)
        }

        is ChatItem.UnreadDivider -> {
            UnreadDividerRow(modifier)
        }
    }
}

/**
 * Reports the messages of the entries [listState] shows, wholly or in part ([ChatEvent.MessagesVisible]): on every
 * change of them, as the list scrolls or messages come and go, and none once the list leaves the screen. Read from the
 * layout, outside the items (`docs/PERFORMANCE.md` §4). A frame of a scroll only compares the keys of the entries at
 * both ends of the screen; the ids are gathered, by key, when those or the items change.
 */
@Composable
private fun ReportVisibleMessages(
    items: ImmutableList<ChatItem>,
    listState: LazyListState,
    onEvent: (ChatEvent) -> Unit,
) {
    val currentItems by rememberUpdatedState(items)
    val currentOnEvent by rememberUpdatedState(onEvent)
    LaunchedEffect(listState) {
        try {
            val ends =
                snapshotFlow {
                    val visible = listState.layoutInfo.visibleItemsInfo
                    visible.firstOrNull()?.key to visible.lastOrNull()?.key
                }
            // By key, as the layout can be a frame behind the items: a new message shifts every index.
            val itemsByKey = snapshotFlow { currentItems }.map { items -> items.associateBy { it.key } }
            combine(ends, itemsByKey) { _, byKey ->
                listState.layoutInfo.visibleItemsInfo.flatMapTo(HashSet()) { byKey[it.key]?.messageIds.orEmpty() }
            }.distinctUntilChanged()
                .collect { currentOnEvent(ChatEvent.MessagesVisible(it)) }
        } finally {
            currentOnEvent(ChatEvent.MessagesVisible(emptySet()))
        }
    }
}

/**
 * The uploads of this bubble's messages among [uploads]; the same empty map when none of them is being sent, so the
 * bubbles that do not upload skip while one does.
 */
private fun ChatItem.Bubble.uploadsIn(uploads: ImmutableMap<Long, MessageUpload>): ImmutableMap<Long, MessageUpload> {
    if (uploads.isEmpty()) return NoUploads
    val ids = album?.messageIds ?: listOf(id)
    if (ids.none(uploads::containsKey)) return NoUploads
    return ids.mapNotNull { messageId -> uploads[messageId]?.let { messageId to it } }.toMap().toImmutableMap()
}

private val NoUploads: ImmutableMap<Long, MessageUpload> = persistentMapOf()

/** The messages an entry shows: a message, every item of an album; none for dates and the divider. */
private val ChatItem.messageIds: List<Long>
    get() =
        when (this) {
            is ChatItem.Bubble -> album?.messageIds ?: listOf(id)
            is ChatItem.Service -> listOf(id)
            is ChatItem.DateSeparator, is ChatItem.UnreadDivider -> emptyList()
        }

/**
 * Scrolls once, when the list first shows: the unread divider to near the top, so the unread messages follow it
 * downwards; without one, or not [toUnread] (the conversation opened at a message, which the jump scrolls to), the list
 * stays at the newest message, where the reverse layout starts. Saved, so a rotation or process death keeps the
 * restored position.
 */
@Composable
private fun InitialScroll(
    items: ImmutableList<ChatItem>,
    listState: LazyListState,
    toUnread: Boolean,
) {
    var done by rememberSaveable { mutableStateOf(false) }
    val currentItems by rememberUpdatedState(items)
    val margin = with(LocalDensity.current) { MaterialTheme.spacing.extraExtraLarge.toPx() }
    LaunchedEffect(listState) {
        if (done) return@LaunchedEffect
        done = true
        if (!toUnread) return@LaunchedEffect
        val dividerIndex = currentItems.indexOfFirst { it is ChatItem.UnreadDivider }
        if (dividerIndex < 0) return@LaunchedEffect
        // Measured at once: the divider at the bottom, then moved up by the viewport's height less its own.
        listState.scrollToItem(dividerIndex)
        val layout = listState.layoutInfo
        val divider = layout.visibleItemsInfo.firstOrNull { it.index == dividerIndex } ?: return@LaunchedEffect
        val viewport = layout.viewportSize.height - layout.beforeContentPadding - layout.afterContentPadding
        // A margin below the top, where the date pill floats.
        listState.scrollBy(-(viewport - divider.size - margin).coerceAtLeast(0f))
    }
}

/**
 * Brings a new newest message into view when the list showed the newest one: a lazy list keeps its first visible
 * entry in place by key, which would leave a message added below it out of view. Elsewhere in the history the list
 * stays where the user scrolled to, and the jump button leads down.
 */
@Composable
private fun FollowNewestMessage(
    newestKey: Any?,
    listState: LazyListState,
) {
    var shownNewestKey by remember { mutableStateOf(newestKey) }
    LaunchedEffect(newestKey) {
        val previousKey = shownNewestKey
        if (newestKey == previousKey) return@LaunchedEffect
        shownNewestKey = newestKey
        // Entries may have come with it (a date separator), so the previous newest one is looked up by key.
        if (listState.layoutInfo.visibleItemsInfo.any { it.key == previousKey }) listState.requestScrollToItem(0)
    }
}

/** An entry of the list: [top] above it, animating as the list changes on the [motionScheme]'s springs. */
private fun Modifier.entry(
    scope: LazyItemScope,
    motionScheme: MotionScheme,
    top: Dp,
): Modifier =
    with(scope) {
        this@entry
            .padding(top = top)
            .animateItem(
                fadeInSpec = motionScheme.defaultEffectsSpec(),
                placementSpec = motionScheme.defaultSpatialSpec(),
                fadeOutSpec = motionScheme.fastEffectsSpec(),
            )
    }

/** The space above an entry: less between the bubbles of a group, which then read as one stack. */
private fun ChatItem.topSpacing(
    between: Dp,
    withinGroup: Dp,
): Dp = if (this is ChatItem.Bubble && position.joinsPrevious) withinGroup else between

/** The content type of an entry, so the list reuses its compositions among entries of one kind. */
private val ChatItem.contentType: Any
    get() =
        when (this) {
            is ChatItem.Bubble -> BubbleContentType.of(this)
            is ChatItem.Service -> SERVICE
            is ChatItem.DateSeparator -> DATE
            is ChatItem.UnreadDivider -> UNREAD
        }

/** The content types of bubbles: their side and their frame, whose layouts differ the most. */
private enum class BubbleContentType {
    IncomingPadded,
    OutgoingPadded,
    IncomingMedia,
    OutgoingMedia,
    IncomingBare,
    OutgoingBare,
    ;

    companion object {
        fun of(bubble: ChatItem.Bubble): BubbleContentType {
            val content = bubble.content
            val outgoing = bubble.isOutgoing
            return when {
                bubble.album == null && content.isBare -> if (outgoing) OutgoingBare else IncomingBare
                bubble.album != null || content.isEdgeToEdge -> if (outgoing) OutgoingMedia else IncomingMedia
                else -> if (outgoing) OutgoingPadded else IncomingPadded
            }
        }
    }
}

/** How close to the oldest message the list asks for older ones: about half a page of entries ahead. */
private const val LOAD_MORE_THRESHOLD = 20

private const val SERVICE = "service"
private const val DATE = "date"
private const val UNREAD = "unread"
private const val OLDER_KEY = "older"

// The conversation scroll benchmark and the Baseline Profile generator (`:benchmark`, `Journeys.kt`) find the list by
// this tag, which `MainActivity` shows as a resource id.
private const val MESSAGE_LIST_TEST_TAG = "message_list"

@PingogramPreviews
@Composable
private fun ChatScreenPreview(
    @PreviewParameter(ChatPreviewStates::class) state: ChatUiState,
) {
    PingogramPreview { PreviewImages { ChatScreen(state = state, onEvent = {}, onBack = {}) } }
}
