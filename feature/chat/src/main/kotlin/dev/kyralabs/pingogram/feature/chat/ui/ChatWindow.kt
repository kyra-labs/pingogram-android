package dev.kyralabs.pingogram.feature.chat.ui

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatHistory
import dev.kyralabs.pingogram.core.domain.repository.MessageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

/**
 * Which messages of chat [chatId] the conversation shows, where its list shows them ([ListAnchor]) and how far pages
 * have loaded ([state]), for [ChatViewModel]. Everything runs in [scope] on the main thread; [onEffect] gets the errors
 * and the messages that are gone.
 *
 * - **Opening** ([open]): once the chat is known ([chat]), the messages it opens at load: around the message search
 *   found ([initialMessageId]), else around the first unread message (`lastReadInboxMessageId`, when anything is
 *   unread), else the newest page ([MessageRepository.loadAround], [MessageRepository.loadNewest]); nothing loads when
 *   the history in memory holds them. Until then [WindowState.shown] is null, so the conversation never shows the
 *   newest messages first and then jumps. After process death ([KEY_ANCHOR] saved) it opens around the newest message
 *   the list last showed ([KEY_SHOWN_MESSAGE]), without highlight. The unread divider is fixed first
 *   ([ChatViewModel.KEY_UNREAD_AFTER]), where it stays while the screen lives.
 * - **Paging**: older pages load for as long as the screen reports that the oldest message is near
 *   ([onNearOldestChanged]), one at a time, until the start; newer ones while the newest loaded message is near
 *   ([onNearNewestChanged]) and the history does not reach the chat's newest message. A failure shows; nothing is
 *   retried by itself: [retry], or the end coming near again, tries again.
 * - **Jumps**: [showMessage] anchors a loaded message at once, and loads the messages around one far back first, as
 *   the older pages' indicator shows; [ChatEffect.MessageUnavailable] when it no longer exists. [showNewest] loads the
 *   newest page in place of the loaded one. A new jump, or leaving the screen, cancels the one still loading. While a
 *   jump loads, the old messages stay; the new ones and the anchor show in one state ([ShownWindow]), so the list never
 *   draws them where the old ones were.
 *
 * [history] is the view model's history, [content] what the screen shows now.
 */
@Suppress("LongParameterList") // What it reads and changes, from the view model.
internal class ChatWindow(
    private val chatId: Long,
    private val repository: MessageRepository,
    private val savedState: SavedStateHandle,
    private val scope: CoroutineScope,
    private val chat: Flow<Chat>,
    private val history: Flow<ChatHistory>,
    private val content: () -> ChatContent,
    private val initialMessageId: Long?,
    private val onEffect: (ChatEffect) -> Unit,
) {
    private val mutableState = MutableStateFlow(WindowState())

    /** The messages shown and how far pages have loaded. */
    val state: StateFlow<WindowState> = mutableState.asStateFlow()

    /** The screen shows the oldest message, or close to it. */
    private var isNearOldest = false

    /** The screen shows the newest loaded message, or close to it. */
    private var isNearNewest = false

    /** Loading the messages the conversation opens at. */
    private var opening: Job? = null

    /** The jump loading its messages. */
    private var jump: Job? = null

    /** Loads the messages the conversation opens at, and shows them with their anchor; again after a failure. */
    fun open() {
        opening?.cancel()
        opening =
            scope.launch {
                val chat = chat.first()
                val unreadAfter =
                    savedState.get<Long>(ChatViewModel.KEY_UNREAD_AFTER)
                        ?: chat.unreadAfterId().also { savedState[ChatViewModel.KEY_UNREAD_AFTER] = it }
                val restored = savedState.get<Long>(KEY_SHOWN_MESSAGE)?.takeIf { KEY_ANCHOR in savedState }
                val target = openTarget(restored, initialMessageId, unreadAfter)
                val result = repository.loadFor(chatId, target, unreadAfter)
                // A found message that is gone says so; one the list showed before process death does not.
                val foundMessage = target is AnchorTarget.Message && target.highlight
                when (result) {
                    is Outcome.Failure -> mutableState.update { it.copy(openError = result.error) }
                    is Outcome.Success -> showLoaded(target, unavailable = foundMessage)
                }
            }
    }

    /** The oldest message coming near loads the next page, also after a failure; once at the start nothing. */
    fun onNearOldestChanged(nearOldest: Boolean) {
        isNearOldest = nearOldest
        val reachedStart = (content() as? ChatContent.Content)?.reachedStart == true
        if (nearOldest && !reachedStart) loadOlderPage()
    }

    /** The newest loaded message coming near loads the next newer page, also after a failure; nothing at the end. */
    fun onNearNewestChanged(nearNewest: Boolean) {
        isNearNewest = nearNewest
        val content = content() as? ChatContent.Content
        if (nearNewest && content != null && !content.reachedEnd) loadNewerPage()
    }

    /**
     * Shows message [messageId] with a new anchor: at once when it is loaded; [ChatEffect.MessageUnavailable] when it
     * is not but lies among the loaded messages, which are consecutive, so it was deleted; otherwise after loading the
     * messages around it ([MessageRepository.loadAround]) with the older pages' indicator, and the error if that fails.
     */
    fun showMessage(messageId: Long) {
        startJump { job ->
            val messages = history.first().messages
            when {
                messages.findById(messageId) != null -> {
                    showLoaded(AnchorTarget.Message(messageId))
                }

                messages.isNotEmpty() && messageId > messages.last().id && messageId < messages.first().id -> {
                    onEffect(ChatEffect.MessageUnavailable)
                }

                else -> {
                    mutableState.update { it.copy(isFindingMessage = true) }
                    val result =
                        try {
                            repository.loadAround(chatId, messageId)
                        } finally {
                            if (jump === job) mutableState.update { it.copy(isFindingMessage = false) }
                        }
                    when (result) {
                        is Outcome.Failure -> onEffect(ChatEffect.ShowError(result.error))
                        is Outcome.Success -> showLoaded(AnchorTarget.Message(messageId), unavailable = true)
                    }
                }
            }
        }
    }

    /**
     * Shows the chat's newest message: loads the newest page in place of the loaded messages, if they do not reach it,
     * with the newer pages' indicator, and anchors the list there.
     */
    fun showNewest() {
        startJump { job ->
            mutableState.update { it.copy(isFindingNewest = true) }
            val result =
                try {
                    repository.loadNewest(chatId)
                } finally {
                    if (jump === job) mutableState.update { it.copy(isFindingNewest = false) }
                }
            when (result) {
                is Outcome.Failure -> onEffect(ChatEffect.ShowError(result.error))
                is Outcome.Success -> showLoaded(AnchorTarget.Newest)
            }
        }
    }

    /** Opens again if that failed, and loads the pages that failed; false if nothing failed here. */
    fun retry(): Boolean {
        val state = mutableState.value
        if (state.openError != null) open()
        if (state.error != null) loadOlderPage()
        if (state.newerError != null) loadNewerPage()
        return state.openError != null || state.error != null || state.newerError != null
    }

    /**
     * Shows the history as the repository holds it now, with a new anchor to [target]: in one state, whichever path
     * the history then takes to the view model ([ShownWindow]). [ChatEffect.MessageUnavailable] if [target] is a
     * message the history does not hold and [unavailable].
     */
    private suspend fun showLoaded(
        target: AnchorTarget,
        unavailable: Boolean = false,
    ) {
        val loaded = repository.history(chatId).first()
        val anchor = ListAnchor(id = (savedState.get<Int>(KEY_ANCHOR) ?: 0) + 1, target = target)
        savedState[KEY_ANCHOR] = anchor.id
        mutableState.update { it.copy(shown = ShownWindow(anchor, loaded), openError = null) }
        if (unavailable && target is AnchorTarget.Message && loaded.messages.findById(target.messageId) == null) {
            onEffect(ChatEffect.MessageUnavailable)
        }
    }

    /**
     * Runs [block] as the jump, replacing one still running, whose indicator goes. Started lazily, so [jump] names it
     * before it runs: a replaced jump, ending as it is cancelled, then leaves the indicators to this one.
     */
    private fun startJump(block: suspend (Job) -> Unit) {
        jump?.cancel()
        mutableState.update { it.copy(isFindingMessage = false, isFindingNewest = false) }
        val started = scope.launch(start = CoroutineStart.LAZY) { block(coroutineContext.job) }
        jump = started
        started.start()
    }

    /**
     * Loads the page before the oldest loaded message; nothing while one loads. A page that succeeds while the screen
     * still shows the oldest message loads the next one, until the start; a failed page waits for [retry] or the oldest
     * message coming near again.
     */
    private fun loadOlderPage() {
        val state = mutableState.value
        if (state.isLoading) return
        mutableState.value = state.copy(isLoading = true, error = null)
        scope.launch {
            val result = repository.loadOlder(chatId)
            // The repository has applied the page by now; a fresh look tells whether it reached the start.
            val reachedStart = result is Outcome.Success && repository.history(chatId).first().reachedStart
            mutableState.update { it.copy(isLoading = false, error = (result as? Outcome.Failure)?.error) }
            if (result is Outcome.Success && !reachedStart && isNearOldest) loadOlderPage()
        }
    }

    /** Loads the page after the newest loaded message, as [loadOlderPage] does before the oldest, until the end. */
    private fun loadNewerPage() {
        val state = mutableState.value
        if (state.isLoadingNewer) return
        mutableState.value = state.copy(isLoadingNewer = true, newerError = null)
        scope.launch {
            val result = repository.loadNewer(chatId)
            val reachedEnd = result is Outcome.Success && repository.history(chatId).first().reachedEnd
            mutableState.update { it.copy(isLoadingNewer = false, newerError = (result as? Outcome.Failure)?.error) }
            if (result is Outcome.Success && !reachedEnd && isNearNewest) loadNewerPage()
        }
    }

    companion object {
        /** The id of the latest [ListAnchor], which the list keeps too, so neither moves it again after restoring. */
        const val KEY_ANCHOR = "chat.anchor"

        /** The newest message the list showed, which the conversation opens around after process death. */
        const val KEY_SHOWN_MESSAGE = "chat.shownMessage"
    }
}

/**
 * The messages shown ([shown], null until the conversation opened at them, or [openError] if that failed), how far
 * older ([isLoading], [error]) and newer ([isLoadingNewer], [newerError]) pages have loaded, and whether the messages
 * around a tapped quote's message ([isFindingMessage]) or the newest ones ([isFindingNewest]) load.
 */
internal data class WindowState(
    val isLoading: Boolean = false,
    val error: DomainError? = null,
    val isFindingMessage: Boolean = false,
    val isLoadingNewer: Boolean = false,
    val newerError: DomainError? = null,
    val isFindingNewest: Boolean = false,
    val shown: ShownWindow? = null,
    val openError: DomainError? = null,
) {
    /** [live], or what [shown] took from the repository while [live] comes from before that ([ShownWindow]). */
    fun historyFor(live: ChatHistory): ChatHistory = shown?.snapshot?.takeIf { live.windowId < it.windowId } ?: live
}

/**
 * The messages the conversation shows and where its list shows them, as a load (opening, a jump) left them:
 * [snapshot] is the repository's history right after it. The history reaches the view model on another path, a little
 * later; until it does ([ChatHistory.windowId] older than the snapshot's), the items come from [snapshot], so the new
 * messages and the [anchor] show in the same state.
 */
internal class ShownWindow(
    val anchor: ListAnchor,
    val snapshot: ChatHistory,
)

/** The last message read when the chat is first shown, if anything is unread; else [NO_UNREAD_DIVIDER]. */
private fun Chat.unreadAfterId(): Long = if (unreadCount > 0) lastReadInboxMessageId else NO_UNREAD_DIVIDER

/**
 * Where the conversation opens: around the newest message the list showed before process death ([restored]), the
 * message search found ([initialMessageId]), the unread divider, or the newest message.
 */
private fun openTarget(
    restored: Long?,
    initialMessageId: Long?,
    unreadAfter: Long,
): AnchorTarget =
    when {
        restored != null -> AnchorTarget.Message(restored, highlight = false)
        initialMessageId != null -> AnchorTarget.Message(initialMessageId)
        unreadAfter != NO_UNREAD_DIVIDER && unreadAfter > 0 -> AnchorTarget.Unread
        else -> AnchorTarget.Newest
    }

/** Loads the messages [target] shows: around its message, around the last message read, or the newest page. */
private suspend fun MessageRepository.loadFor(
    chatId: Long,
    target: AnchorTarget,
    unreadAfter: Long,
): Outcome<Unit> =
    when (target) {
        is AnchorTarget.Message -> loadAround(chatId, target.messageId)
        AnchorTarget.Unread -> loadAround(chatId, unreadAfter)
        AnchorTarget.Newest -> loadNewest(chatId)
    }
