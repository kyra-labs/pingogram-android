package dev.kyralabs.pingogram.feature.chat.ui.scheduled

import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.Default
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.getOrNull
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatHistory
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageSchedule
import dev.kyralabs.pingogram.core.domain.model.ReplyTo
import dev.kyralabs.pingogram.core.domain.model.ScheduledMessage
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.MessageActionRepository
import dev.kyralabs.pingogram.core.domain.repository.MessageRepository
import dev.kyralabs.pingogram.core.domain.repository.ScheduledMessageRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.navigation.ScheduledMessagesRoute
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.messagePreviewText
import dev.kyralabs.pingogram.core.ui.time.TimePatterns
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatPeople
import dev.kyralabs.pingogram.feature.chat.ui.ChatRef
import dev.kyralabs.pingogram.feature.chat.ui.ChatTimeFormatter
import dev.kyralabs.pingogram.feature.chat.ui.ReplyState
import dev.kyralabs.pingogram.feature.chat.ui.chatRef
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerBar
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerText
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerUiState
import dev.kyralabs.pingogram.feature.chat.ui.composer.EditTarget
import dev.kyralabs.pingogram.feature.chat.ui.composer.editTarget
import dev.kyralabs.pingogram.feature.chat.ui.composer.editableText
import dev.kyralabs.pingogram.feature.chat.ui.composer.restoredText
import dev.kyralabs.pingogram.feature.chat.ui.composer.savedEntities
import dev.kyralabs.pingogram.feature.chat.ui.composer.textOrCaption
import dev.kyralabs.pingogram.feature.chat.ui.fullNameOf
import dev.kyralabs.pingogram.feature.chat.ui.referencedChatIds
import dev.kyralabs.pingogram.feature.chat.ui.shortNameOf
import dev.kyralabs.pingogram.feature.chat.ui.userId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.Locale
import javax.inject.Inject

/**
 * The scheduled messages of chat [chatId] (`REQUIREMENTS.md` FR-C5) as [uiState], for [ScheduledMessagesRoute]; events
 * come in through [onEvent].
 *
 * - **List**: [ScheduledMessageRepository.scheduledMessages], kept current by TDLib's updates (a message Telegram sent
 *   at its time leaves it by itself, and shows in the conversation's history), mapped by a [ScheduledItemMapper] into
 *   the conversation's bubbles under their send days, with the chat, its users and the chats the messages name, the
 *   messages they reply to (fetched once each, [MessageRepository.repliedMessage]) and the minute
 *   ([WallClock.minutes], for "Today" and "Tomorrow"), on [dispatcher]. It runs while the screen collects, and 5 s
 *   after. A failed load shows its error; "Try again" ([ScheduledEvent.Retry]) loads again. Empty, it says so.
 * - **Actions** ([ScheduledEvent.ShowActions]): "Send now" (`editMessageSchedulingState` without a state),
 *   "Reschedule" (the date and time pickers, [ScheduledMessagesUiState.reschedule], then the new time with the
 *   message's repeat period), "Edit" where TDLib allows it ([MessageActionRepository.properties]), "Copy text" where
 *   the chat lets its content be saved, and "Delete" after an `AlertDialog` ([ScheduledMessagesUiState.deleteDialog]).
 *   Each result is a snackbar ([effects]): sent, rescheduled for its time, deleted, or why Telegram refused (a flood
 *   wait's time).
 * - **Edit**: the message's text or caption goes into [composerText], the conversation's composer in edit mode
 *   ([ScheduledMessagesUiState.composer]); its check saves it with the conversation's edit
 *   ([MessageActionRepository.editText] / `editCaption`), an unchanged text sending nothing; a refused edit comes back
 *   into the field with a snackbar. The message edited and the text are kept in the [SavedStateHandle].
 * - **Starting**: what it starts (following the composer's text) runs once the screen's first frame is done.
 *
 * Created by [ScheduledMessagesRoute]'s destination with the route's chat, through Hilt.
 */
@Suppress(
    "LongParameterList", // The chat and its saved state, the repositories it combines and changes, the clock and times.
    "TooManyFunctions", // One function per event, and the state they share.
)
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ScheduledMessagesViewModel internal constructor(
    private val chatId: Long,
    private val savedState: SavedStateHandle,
    private val repository: ScheduledMessageRepository,
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
    private val messageRepository: MessageRepository,
    private val actionRepository: MessageActionRepository,
    private val clock: WallClock,
    private val timePatterns: TimePatterns,
    private val dispatcher: CoroutineDispatcher,
    private val locale: () -> Locale,
) : ViewModel() {
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        repository: ScheduledMessageRepository,
        chatRepository: ChatRepository,
        userRepository: UserRepository,
        messageRepository: MessageRepository,
        actionRepository: MessageActionRepository,
        clock: WallClock,
        timePatterns: TimePatterns,
        @Dispatcher(Default) dispatcher: CoroutineDispatcher,
    ) : this(
        savedStateHandle.toRoute<ScheduledMessagesRoute>().chatId,
        savedStateHandle,
        repository,
        chatRepository,
        userRepository,
        messageRepository,
        actionRepository,
        clock,
        timePatterns,
        dispatcher,
        Locale::getDefault,
    )

    /** Each retry loads the list again. */
    private val attempts = MutableStateFlow(0)

    private val effectChannel = Channel<ScheduledEffect>(Channel.BUFFERED)

    /** One-off effects for the screen: snackbars, the clipboard, the composer's focus. */
    val effects: Flow<ScheduledEffect> = effectChannel.receiveAsFlow()

    /** The text the composer edits while a message is edited; kept across rotations and, saved, process death. */
    val composerText = ComposerText(restoredText(savedState[KEY_TEXT], savedState[KEY_ENTITIES]) ?: FormattedText.Empty)

    /** The message being edited; null while none is. */
    private val editingId = savedState.getStateFlow<Long?>(KEY_EDITING, null)

    private val replies = MutableStateFlow<PersistentMap<Long, ReplyState>>(persistentMapOf())
    private val revealedSpoilers = MutableStateFlow<PersistentSet<Long>>(persistentSetOf())
    private val actionsSheet = MutableStateFlow<ScheduledActionsSheet?>(null)
    private val deleteDialog = MutableStateFlow<ScheduledDeleteDialog?>(null)
    private val rescheduleDialog = MutableStateFlow<RescheduleDialog?>(null)

    private val mapper = ScheduledItemMapper()
    private var timeFormatter: ChatTimeFormatter? = null

    /** The scheduled messages, or why they are not there; null while they load. */
    private val scheduled: SharedFlow<Outcome<ImmutableList<ScheduledMessage>>?> =
        attempts
            .flatMapLatest {
                repository
                    .scheduledMessages(chatId)
                    .map<Outcome<ImmutableList<ScheduledMessage>>, Outcome<ImmutableList<ScheduledMessage>>?> { it }
                    .onStart { emit(null) }
            }.onEach { loaded -> loaded?.getOrNull()?.let(::fetchMissingReplies) }
            .flowOn(dispatcher)
            .shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), replay = 1)

    /** The chats the messages name besides this one, by id: their titles and photos. */
    private val referencedChats: Flow<ImmutableMap<Long, ChatRef>> =
        combine(scheduled, replies) { loaded, replies ->
            val messages = loaded?.getOrNull().orEmpty().map { it.message }
            referencedChatIds(chatId, ChatHistory(messages.toImmutableList(), reachedStart = true), replies)
        }.distinctUntilChanged()
            .flatMapLatest { ids ->
                if (ids.isEmpty()) {
                    flowOf(persistentMapOf())
                } else {
                    combine(ids.map(chatRepository::chatRef)) { refs ->
                        refs.filterNotNull().associateBy { it.id }.toImmutableMap()
                    }
                }
            }

    private val people: SharedFlow<ChatPeople> =
        combine(userRepository.users, userRepository.myUserId, referencedChats, ::ChatPeople)
            .shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), replay = 1)

    private val chat: SharedFlow<Chat?> =
        chatRepository.chat(chatId).shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), 1)

    private val overlays: Flow<Overlays> =
        combine(actionsSheet, deleteDialog, rescheduleDialog, editingId, ::Overlays)

    val uiState: StateFlow<ScheduledMessagesUiState> =
        combine(
            chat,
            scheduled,
            people,
            combine(replies, revealedSpoilers, clock.minutes, ::Taps),
            overlays,
            ::state,
        ).flowOn(dispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ScheduledMessagesUiState())

    init {
        // Dispatched: the screen creates the view model in the frame that first composes it, which does not wait.
        viewModelScope.launch(Dispatchers.Main) {
            snapshotFlow { composerText.content }.collect { content ->
                savedState[KEY_TEXT] = content.text
                savedState[KEY_ENTITIES] = content.savedEntities()
            }
        }
    }

    fun onEvent(event: ScheduledEvent) {
        when (event) {
            is ScheduledEvent.ShowActions -> showActions(event.messageId)
            ScheduledEvent.DismissActions -> actionsSheet.value = null
            is ScheduledEvent.Act -> act(event.messageId, event.action)
            ScheduledEvent.ConfirmDelete -> confirmDelete()
            ScheduledEvent.DismissDelete -> deleteDialog.value = null
            is ScheduledEvent.Reschedule -> reschedule(event.messageId, event.sendDate)
            ScheduledEvent.DismissReschedule -> rescheduleDialog.value = null
            ScheduledEvent.SaveEdit -> saveEdit()
            ScheduledEvent.CancelEdit -> endEdit()
            is ScheduledEvent.RevealSpoilers -> revealedSpoilers.update { it.adding(event.messageId) }
            ScheduledEvent.Retry -> attempts.update { it + 1 }
        }
    }

    /** Opens the actions of [messageId] with what TDLib allows for it; a message gone meanwhile says so. */
    private fun showActions(messageId: Long) {
        val scheduled = scheduledOf(messageId) ?: return gone()
        viewModelScope.launch {
            val properties = actionRepository.properties(chatId, messageId).getOrNull()
            val protected = chat.replayCache.firstOrNull()?.hasProtectedContent == true
            val message = scheduled.message
            val actions =
                buildList {
                    add(ScheduledAction.SendNow)
                    add(ScheduledAction.Reschedule)
                    if (properties?.canBeEdited == true && message.editTarget != null) add(ScheduledAction.Edit)
                    val canCopy = properties?.canBeSaved ?: !protected
                    if (canCopy && message.textOrCaption.text.isNotEmpty()) add(ScheduledAction.Copy)
                    add(ScheduledAction.Delete)
                }
            // Gone while TDLib answered: nothing to act on.
            if (scheduledOf(messageId) == null) return@launch gone()
            actionsSheet.value =
                ScheduledActionsSheet(
                    messageId = messageId,
                    sendsAt = sendsAtText(scheduled.schedule),
                    actions = actions.toImmutableList(),
                    repeatPeriod = (scheduled.schedule as? MessageSchedule.At)?.repeatPeriod ?: 0,
                )
        }
    }

    private fun act(
        messageId: Long,
        action: ScheduledAction,
    ) {
        actionsSheet.value = null
        val scheduled = scheduledOf(messageId) ?: return gone()
        when (action) {
            ScheduledAction.SendNow -> {
                request(ScheduledEffect.Sent) { repository.sendNow(chatId, messageId) }
            }

            ScheduledAction.Reschedule -> {
                rescheduleDialog.value = RescheduleDialog(messageId, scheduled.schedule.sendDate)
            }

            ScheduledAction.Edit -> {
                startEdit(scheduled.message)
            }

            ScheduledAction.Copy -> {
                effectChannel.trySend(ScheduledEffect.CopyText(scheduled.message.textOrCaption.text))
            }

            ScheduledAction.Delete -> {
                deleteDialog.value = ScheduledDeleteDialog(messageId)
            }
        }
    }

    private fun confirmDelete() {
        val dialog = deleteDialog.value ?: return
        deleteDialog.value = null
        if (editingId.value == dialog.messageId) endEdit()
        request(ScheduledEffect.Deleted) { repository.delete(chatId, listOf(dialog.messageId)) }
    }

    /** Moves [messageId] to [sendDate], keeping how often it repeats. */
    private fun reschedule(
        messageId: Long,
        sendDate: Long,
    ) {
        rescheduleDialog.value = null
        val scheduled = scheduledOf(messageId) ?: return gone()
        val repeatPeriod = (scheduled.schedule as? MessageSchedule.At)?.repeatPeriod ?: 0
        request(ScheduledEffect.Rescheduled(sendDate)) {
            repository.reschedule(chatId, messageId, sendDate, repeatPeriod)
        }
    }

    /** Puts [message]'s text or caption into the composer, which edits it, and gives the field the focus. */
    private fun startEdit(message: Message) {
        savedState[KEY_EDITING] = message.id
        composerText.setContent(message.editableText)
        effectChannel.trySend(ScheduledEffect.FocusComposer)
    }

    /** Leaves the edit without saving it: the composer hides and forgets the text. */
    private fun endEdit() {
        savedState[KEY_EDITING] = null
        composerText.setContent(FormattedText.Empty)
    }

    /**
     * Saves the edit in progress: a text message needs more than white space. The edit ends first; an unchanged text
     * sends nothing; a refused one comes back into the composer, unless another edit began, with a snackbar.
     */
    private fun saveEdit() {
        val messageId = editingId.value ?: return
        val message = scheduledOf(messageId)?.message
        val target = message?.editTarget
        val content = composerText.content
        when {
            message == null || target == null -> {
                endEdit()
                gone()
            }

            target == EditTarget.Text && content.text.isBlank() -> {
                // A text message needs a text: the edit stays.
            }

            else -> {
                endEdit()
                if (content != message.editableText) sendEdit(messageId, target, content)
            }
        }
    }

    /** Sends [content] as the edit of [messageId]; a refusal brings the edit back, unless another one began. */
    private fun sendEdit(
        messageId: Long,
        target: EditTarget,
        content: FormattedText,
    ) {
        viewModelScope.launch {
            val result =
                when (target) {
                    EditTarget.Text -> {
                        actionRepository.editText(chatId, messageId, content)
                    }

                    is EditTarget.Caption -> {
                        actionRepository.editCaption(chatId, messageId, content, target.showAboveMedia)
                    }
                }
            if (result is Outcome.Failure) {
                if (editingId.value == null) {
                    savedState[KEY_EDITING] = messageId
                    composerText.setContent(content)
                }
                effectChannel.trySend(ScheduledEffect.ShowError(result.error))
            }
        }
    }

    /** Sends [call]; [done] once it succeeds, its error otherwise. */
    private fun request(
        done: ScheduledEffect,
        call: suspend () -> Outcome<Unit>,
    ) {
        viewModelScope.launch {
            when (val result = call()) {
                is Outcome.Success -> effectChannel.trySend(done)
                is Outcome.Failure -> effectChannel.trySend(ScheduledEffect.ShowError(result.error))
            }
        }
    }

    private fun gone() {
        effectChannel.trySend(ScheduledEffect.MessageGone)
    }

    /** Message [messageId] as last loaded; null if it is not (any more). */
    private fun scheduledOf(messageId: Long): ScheduledMessage? =
        scheduled.replayCache
            .firstOrNull()
            ?.getOrNull()
            ?.firstOrNull { it.message.id == messageId }

    /** Fetches, once each, the messages that loaded ones reply to. */
    private fun fetchMissingReplies(messages: List<ScheduledMessage>) {
        val missing =
            messages.filter { scheduled ->
                val replyTo = scheduled.message.replyTo as? ReplyTo.Message
                replyTo != null && replyTo.messageId != 0L && scheduled.message.id !in replies.value
            }
        for (scheduled in missing) {
            val id = scheduled.message.id
            var added = false
            replies.update { replies ->
                added = id !in replies
                if (added) replies.putting(id, ReplyState.Pending) else replies
            }
            if (added) {
                viewModelScope.launch {
                    val state =
                        when (val replied = messageRepository.repliedMessage(chatId, id)) {
                            is Outcome.Success -> ReplyState.Loaded(replied.value)
                            is Outcome.Failure -> ReplyState.Failed(replied.error)
                        }
                    replies.update { it.putting(id, state) }
                }
            }
        }
    }

    /** Runs on [dispatcher]: maps the list, the composer and what shows over them. */
    private fun state(
        chat: Chat?,
        loaded: Outcome<ImmutableList<ScheduledMessage>>?,
        people: ChatPeople,
        taps: Taps,
        overlays: Overlays,
    ): ScheduledMessagesUiState {
        if (chat == null) return ScheduledMessagesUiState()
        val messages = loaded?.getOrNull()
        val content =
            when {
                loaded == null -> {
                    ScheduledContent.Loading
                }

                loaded is Outcome.Failure -> {
                    ScheduledContent.Error(loaded.error)
                }

                messages.isNullOrEmpty() -> {
                    ScheduledContent.Empty
                }

                else -> {
                    val formatter = timeFormatter()
                    val sources =
                        ScheduledSources(
                            chat = chat,
                            messages = messages,
                            replies = taps.replies,
                            revealedSpoilers = taps.revealedSpoilers,
                            people = people,
                            today = taps.now.atZone(formatter.zone).toLocalDate(),
                            timeFormatter = formatter,
                        )
                    ScheduledContent.Content(mapper.map(sources))
                }
            }
        val ids = messages.orEmpty().map { it.message.id }.toSet()
        val editing = overlays.editingId?.let { id -> messages?.firstOrNull { it.message.id == id }?.message }
        return ScheduledMessagesUiState(
            content = content,
            // A message sent or deleted meanwhile takes its sheet and dialogs with it.
            actions = overlays.sheet?.takeIf { it.messageId in ids },
            deleteDialog = overlays.deleteDialog?.takeIf { it.messageId in ids },
            reschedule = overlays.reschedule?.takeIf { it.messageId in ids },
            composer = editing?.let { composerOf(it, chat, people) } ?: ComposerUiState.Hidden,
            canCopy = !chat.hasProtectedContent,
        )
    }

    /** The composer editing [message]: its edit bar, nothing else (no attachments, no scheduling). */
    private fun composerOf(
        message: Message,
        chat: Chat,
        people: ChatPeople,
    ): ComposerUiState {
        val preview = messagePreviewText(message, people.myUserId) { sender -> shortNameOf(sender, chat, people) }
        val bar = ComposerBar.Edit(message.id, preview, canBeEmpty = message.editTarget is EditTarget.Caption)
        return ComposerUiState.Available(canSchedule = false, bar = bar, attachOptions = persistentListOf())
    }

    /** "Sends Today, 9:41 AM", or "Sends when Ada is online". */
    private fun sendsAtText(schedule: MessageSchedule): UiText {
        val users = people.replayCache.firstOrNull()?.users
        val user =
            chat.replayCache
                .firstOrNull()
                ?.userId
                ?.let { users?.get(it) }
                ?.let(::fullNameOf)
        val formatter = timeFormatter()
        return formatter.sendsText(schedule, clock.now().atZone(formatter.zone).toLocalDate(), user)
    }

    /** The formatter for the current time zone, language and clock setting, which can change while the app runs. */
    private fun timeFormatter(): ChatTimeFormatter {
        val zone = clock.zone()
        val locale = locale()
        val is24HourClock = timePatterns.is24HourClock()
        return timeFormatter?.takeIf { it.isFor(zone, locale, is24HourClock) }
            ?: ChatTimeFormatter(zone, locale, is24HourClock, timePatterns).also { timeFormatter = it }
    }

    /** What was fetched and tapped for the bubbles, and the minute. */
    private class Taps(
        val replies: Map<Long, ReplyState>,
        val revealedSpoilers: Set<Long>,
        val now: Instant,
    )

    /** What shows over the list: the actions sheet, the dialogs, and the message being edited. */
    private class Overlays(
        val sheet: ScheduledActionsSheet?,
        val deleteDialog: ScheduledDeleteDialog?,
        val reschedule: RescheduleDialog?,
        val editingId: Long?,
    )

    internal companion object {
        const val KEY_EDITING = "scheduled.editing"
        const val KEY_TEXT = "scheduled.composerText"
        const val KEY_ENTITIES = "scheduled.composerEntities"

        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
