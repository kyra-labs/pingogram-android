package dev.kyralabs.pingogram.feature.chat.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Dp
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.domain.model.SendOptions
import dev.kyralabs.pingogram.core.ui.component.ContentSheet
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.component.rememberSelectionBack
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.attach.AttachInputs
import dev.kyralabs.pingogram.feature.chat.ui.attach.AttachSheets
import dev.kyralabs.pingogram.feature.chat.ui.attach.MessageUpload
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
import dev.kyralabs.pingogram.feature.chat.ui.panel.EmojiPanelEvent
import dev.kyralabs.pingogram.feature.chat.ui.panel.EmojiPanelUiState
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewStates
import dev.kyralabs.pingogram.feature.chat.ui.voice.VoiceRecordingUiState
import dev.kyralabs.pingogram.feature.chat.ui.voice.voiceEvents
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet

/**
 * The conversation (`REQUIREMENTS.md` FR-C1–C3, FR-C5, FR-C8; `docs/DESIGN_SYSTEM.md` §5–6): the [ChatTopBar] on
 * `surfaceContainer` over the messages, newest at the bottom, on a rounded `surface` sheet ([ContentSheet]) that they
 * scroll in, and the composer below them ([ChatBottomBar]), above the keyboard and the system bars.
 *
 * - **List**: a reverse-layout `LazyColumn` with a key and a content type per entry, each animating to its place on
 *   the motion scheme's springs, so a new message comes in from the bottom. It reports whether the oldest or the
 *   newest loaded message is within [LOAD_MORE_THRESHOLD] entries ([ChatEvent.NearOldestChanged],
 *   [ChatEvent.NearNewestChanged]) and which messages it shows ([ChatEvent.MessagesVisible]), with the state of older
 *   pages above the oldest message and of newer ones below the newest. It goes where the content's [ListAnchor] says,
 *   once per anchor ([FollowListAnchor]): the unread divider near the top, a message in the middle, or the newest
 *   message; a rotation or process death keeps the position.
 * - **Date pill**: while the list scrolls, the date of the topmost message floats at the top, fading in and out on the
 *   motion scheme's effects springs, read in the draw phase.
 * - **Jump to bottom**: a small FAB, with the unread count as badge, springs in once the newest message is out of
 *   view, or not loaded, and scrolls back to it; when the loaded messages do not reach it, it asks for the newest ones
 *   ([ChatEvent.ShowNewest]), which come with their anchor.
 * - **Jump to a message** ([jumpState]): a tapped quote's message, or the message the conversation was opened at,
 *   comes as an anchor: the list centres it and highlights it.
 * - **States**: a loading indicator once loading takes a moment ([DelayedLoadingIndicator]; messages from TDLib's
 *   local database come sooner), an empty state, an error with retry, and a chat that does not exist.
 * - **Composer** ([composerText], the view model's): where the user can write, whatever the messages' state; sending a
 *   message now scrolls to the newest one, where it shows (asking for it when the loaded messages do not reach it).
 *   Where they cannot, a label says why; in a channel they only
 *   read, there is nothing. Its reply or edit bar shows its message in the list when tapped, as a reply quote does;
 *   [composerFocus] gives its text field the focus when the user starts a reply or an edit. Its microphone records
 *   voice messages ([recording], the view model's, kept apart from [state]); one sent scrolls to the newest message
 *   too. Its emoji button opens the emoji and sticker panel ([emojiPanel], from its own view model, which gets
 *   [onEmojiPanelEvent]) in the keyboard's place; a sticker tapped there is sent and scrolls to the newest message.
 * - **Message actions**: a long press on a bubble opens its actions sheet ([MessageActionsBottomSheet]), deleting asks
 *   first ([DeleteMessagesAlert]); tapping a bubble's reaction chip toggles the user's reaction.
 * - **Attach menu**: the composer's attach button opens it, with what [attach] gives; picked photos, videos and
 *   files show in their preview, a new poll in its form ([AttachSheets]). Sending them now scrolls to the newest
 *   message, as sending a text does. Bubbles being sent show their uploads ([ChatUiState.uploads]).
 * - **Selection mode** ([ChatUiState.selection]): the top bar shows how many messages are selected with a close
 *   button, the bubbles a check, and the selection toolbar ([SelectionBottomBar]) takes the composer's place, which
 *   leaves with the keyboard. Back (predictive, the toolbar following the gesture) and the close button leave it.
 * - **Report** of messages: its sheet ([ReportMessagesSheet]) with the reasons Telegram asks for, and the comment
 *   ([reportComment]).
 * - **Sponsored messages** of channels and bots (`REQUIREMENTS.md` §2) are cards among the messages
 *   ([SponsoredMessageCard]); one goes to TDLib as viewed once it is wholly on screen ([ReportSponsoredViews]), and
 *   its info button opens "About this ad" ([SponsoredMessageSheet]).
 *
 * [onBack] is the top bar's back button; null hides it, beside the chat list on expanded windows.
 */
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
    attach: AttachInputs = remember { AttachInputs() },
    recording: VoiceRecordingUiState? = null,
    reportComment: TextFieldState = remember { TextFieldState() },
    emojiPanel: EmojiPanelUiState = EmojiPanelUiState(),
    onEmojiPanelEvent: (EmojiPanelEvent) -> Unit = {},
) {
    val content = state.content
    val selection = state.selection
    val clearSelection = { onEvent(SelectionEvent.Clear) }
    val selectionBack = rememberSelectionBack(enabled = selection != null, onBack = clearSelection)
    LeaveComposerWhileSelecting(selecting = selection != null)
    val showJump by remember(listState) { derivedStateOf { listState.canScrollBackward } }
    val scrollToNewest = rememberJumpToNewest(listState, content, onEvent)
    var attachMenuOpen by rememberSaveable { mutableStateOf(false) }
    val scrollToSent = scrollingToSent(content, scrollToNewest)
    Scaffold(
        modifier = modifier,
        topBar = {
            ChatTopBar(
                header = state.header,
                onBack = onBack,
                selectedCount = selection?.messageIds?.size,
                onClearSelection = clearSelection,
            )
        },
        bottomBar = {
            ChatScreenBottomBar(
                state = state,
                recording = recording,
                composerText = composerText,
                composerFocus = composerFocus,
                selectionBack = { selectionBack.value },
                onEvent = onEvent,
                onSent = scrollToSent,
                emojiPanel = emojiPanel,
                onEmojiPanelEvent = onEmojiPanelEvent,
            ) { attachMenuOpen = true }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            JumpToBottomButton(
                visible = content is ChatContent.Content && (showJump || !content.reachedEnd),
                unreadCount = state.unreadCount,
                motionScheme = MaterialTheme.motionScheme,
                onClick = scrollToNewest,
            )
        },
    ) { innerPadding ->
        ContentSheet(innerPadding, Modifier.consumeWindowInsets(innerPadding)) { contentPadding ->
            ChatBody(state, onEvent, listState, jumpState, contentPadding)
        }
    }
    ChatSheets(state, onEvent, reportComment)
    AttachSheets(
        state = state,
        inputs = attach,
        menuOpen = attachMenuOpen,
        onCloseMenu = { attachMenuOpen = false },
        onEvent = onEvent,
        onSent = scrollToSent,
    )
}

/**
 * The bottom of the screen: the composer ([ChatBottomBar]), or in selection mode the selection toolbar in its place
 * ([SelectionBottomBar]), which shrinks with the predictive back gesture's [selectionBack]. Sending reports through
 * [onSent], except an edit, whose message stays where it is; so does sending the voice message being [recording].
 */
@Suppress("LongParameterList") // The state, the recording, the composer's text and focus, the panel, its buttons.
@Composable
private fun ChatScreenBottomBar(
    state: ChatUiState,
    recording: VoiceRecordingUiState?,
    composerText: ComposerText,
    composerFocus: ComposerFocusState,
    selectionBack: () -> Float,
    onEvent: (ChatEvent) -> Unit,
    onSent: (SendOptions) -> Unit,
    emojiPanel: EmojiPanelUiState,
    onEmojiPanelEvent: (EmojiPanelEvent) -> Unit,
    onAttach: () -> Unit,
) {
    SelectionBottomBar(
        actions = state.selection?.actions,
        backProgress = selectionBack,
        onAction = { onEvent(SelectionEvent.Act(it)) },
    ) {
        val editing = (state.composer as? ComposerUiState.Available)?.bar is ComposerBar.Edit
        ChatBottomBar(
            composer = state.composer,
            text = composerText,
            onSend = { options ->
                onEvent(ChatEvent.Send(options))
                if (!editing) onSent(options)
            },
            onFocusChanged = { onEvent(ChatEvent.ComposerFocusChanged(it)) },
            focus = composerFocus,
            onShowBarMessage = { onEvent(ChatEvent.OpenReply(it)) },
            onCloseBar = { onEvent(if (editing) ChatEvent.CancelEdit else ChatEvent.CancelReply) },
            onAttach = onAttach,
            recording = recording,
            onVoiceEvent = voiceEvents(onEvent, onSent = { onSent(SendOptions()) }),
            panel = emojiPanel,
            onPanelEvent = onEmojiPanelEvent,
            onSendSticker = { sticker ->
                onEvent(ChatEvent.SendSticker(sticker))
                onSent(SendOptions())
            },
        )
    }
}

/**
 * What shows over the conversation: a message's actions, the report of messages with its [reportComment], "About this
 * ad" of a sponsored message, and the confirmation before messages are deleted.
 */
@Composable
private fun ChatSheets(
    state: ChatUiState,
    onEvent: (ChatEvent) -> Unit,
    reportComment: TextFieldState,
) {
    state.messageActions?.let { MessageActionsBottomSheet(it, onEvent) }
    state.report?.let { ReportMessagesSheet(it, reportComment, onEvent) }
    state.sponsoredSheet?.let { SponsoredMessageSheet(it, onEvent) }
    state.deleteDialog?.let { dialog ->
        DeleteMessagesAlert(
            dialog = dialog,
            onConfirm = { onEvent(ChatEvent.ConfirmDelete(it)) },
            onDismiss = { onEvent(ChatEvent.DismissDelete) },
        )
    }
}

/** What the sheet shows for [state]'s content: progress, an empty or error state, or the messages. */
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
            Box(bodyModifier, contentAlignment = Alignment.Center) { DelayedLoadingIndicator() }
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
    val playback = state.playback
    val selectedIds = state.selection?.messageIds
    val bubbleResources = rememberBubbleResources(jumpState, state.shortcuts, state.mediaDownloads)
    val motionScheme = MaterialTheme.motionScheme
    val spacing = MaterialTheme.spacing
    val layoutDirection = LocalLayoutDirection.current
    val showsNewerState = content.isLoadingNewer || content.newerError != null
    val listPadding =
        PaddingValues(
            start = contentPadding.calculateStartPadding(layoutDirection) + spacing.medium,
            top = contentPadding.calculateTopPadding(),
            end = contentPadding.calculateEndPadding(layoutDirection) + spacing.medium,
            bottom = contentPadding.calculateBottomPadding() + spacing.small,
        )
    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .anchorPlacement(jumpState, listState, listPadding, margin = spacing.extraExtraLarge)
                .hideUntilAnchored(content.anchor, content.items, jumpState)
                .testTag(MESSAGE_LIST_TEST_TAG),
        state = listState,
        contentPadding = listPadding,
        reverseLayout = true,
    ) {
        if (showsNewerState) {
            pagesState(NEWER_KEY, content.isLoadingNewer, content.newerError, R.string.chat_newer_error, onEvent)
        }
        items(content.items, key = { it.key }, contentType = { it.contentType }) { item ->
            val itemModifier = Modifier.entry(this, motionScheme, item.topSpacing(spacing.small, spacing.extraSmall))
            ChatItemRow(item, fileStates, state.uploads, playback, bubbleResources, onEvent, itemModifier, selectedIds)
        }
        if (content.isLoadingOlder || content.olderError != null) {
            pagesState(OLDER_KEY, content.isLoadingOlder, content.olderError, R.string.chat_older_error, onEvent)
        }
    }
    FollowListAnchor(content, listState, jumpState, leadingEntries = if (showsNewerState) 1 else 0)
    FollowNewestMessage(
        newestKey = content.items.firstOrNull()?.key,
        newestMessageKey = content.items.newestMessageKey(),
        reachedEnd = content.reachedEnd,
        listState = listState,
    )
    ReportVisibleMessages(content.items, listState, onEvent)
    ReportSponsoredViews(content.items, listState, onEvent)
    ReportNearEnds(listState, onEvent)
}

/**
 * The entry [key] with the state of older or newer pages ([PagesState]): loading, or [errorText] with a retry after
 * [error].
 */
private fun LazyListScope.pagesState(
    key: String,
    isLoading: Boolean,
    error: DomainError?,
    @StringRes errorText: Int,
    onEvent: (ChatEvent) -> Unit,
) = item(key = key, contentType = PAGES_STATE) {
    PagesState(
        isLoading = isLoading,
        error = error,
        errorText = errorText,
        onRetry = { onEvent(ChatEvent.Retry) },
        modifier = Modifier.entry(this, MaterialTheme.motionScheme, MaterialTheme.spacing.small),
    )
}

/**
 * One entry of the list: a message with the state of its file, the uploads of its messages being sent and its playback
 * (only the message in the player has one), a service message, a date, the unread divider or a sponsored message. In
 * selection mode ([selectedIds] not null) a bubble shows whether it is selected: an album's when any of its items is.
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
    selectedIds: ImmutableSet<Long>? = null,
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
                selection = selectedIds?.let { ids -> item.selectionIn(ids) },
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

        is ChatItem.Sponsored -> {
            SponsoredMessageCard(item.card, onEvent, modifier)
        }
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
            is ChatItem.Sponsored -> SPONSORED
        }

/** The content types of bubbles: their side and their frame, whose layouts differ the most. */
private enum class BubbleContentType {
    IncomingPadded,
    OutgoingPadded,
    IncomingMedia,
    OutgoingMedia,
    IncomingBare,
    OutgoingBare,
    IncomingEmoji,
    OutgoingEmoji,
    ;

    companion object {
        fun of(bubble: ChatItem.Bubble): BubbleContentType {
            val content = bubble.content
            val outgoing = bubble.isOutgoing
            return when {
                bubble.isEmojiOnly -> if (outgoing) OutgoingEmoji else IncomingEmoji
                bubble.album == null && content.isBare -> if (outgoing) OutgoingBare else IncomingBare
                bubble.album != null || content.isEdgeToEdge -> if (outgoing) OutgoingMedia else IncomingMedia
                else -> if (outgoing) OutgoingPadded else IncomingPadded
            }
        }
    }
}

private const val SERVICE = "service"
private const val DATE = "date"
private const val UNREAD = "unread"
private const val SPONSORED = "sponsored"
private const val OLDER_KEY = "older"
private const val NEWER_KEY = "newer"
private const val PAGES_STATE = "pages"

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
