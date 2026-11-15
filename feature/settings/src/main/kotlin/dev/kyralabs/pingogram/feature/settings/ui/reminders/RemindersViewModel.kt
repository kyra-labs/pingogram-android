package dev.kyralabs.pingogram.feature.settings.ui.reminders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.getOrNull
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.MessageReminder
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.MessageRepository
import dev.kyralabs.pingogram.core.domain.repository.ReminderRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.ui.component.AvatarShape
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.messagePreviewText
import dev.kyralabs.pingogram.core.ui.text.shortName
import dev.kyralabs.pingogram.core.ui.time.ReminderTimeLabels
import dev.kyralabs.pingogram.core.ui.time.TimePatterns
import dev.kyralabs.pingogram.feature.settings.R
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.Locale
import javax.inject.Inject

/**
 * The Reminders page (`docs/DESIGN_SYSTEM.md` §6 "Message reminders", the owner's boards 29e and 29f): every reminder
 * of the account kept on this phone ([ReminderRepository]), each with its chat ([ChatRepository]) and the message as
 * the chat list previews it, read once from TDLib ([MessageRepository.message]) and kept for the page's life. Upcoming
 * ones (not done, past ones first) over done ones, by time or by chat as the saved state says. Marking done and back,
 * and deleting with "Undo", are written at once.
 */
@Suppress("LongParameterList") // The reminders, what they show of their chats and messages, and the clock.
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RemindersViewModel internal constructor(
    private val savedStateHandle: SavedStateHandle,
    private val reminders: ReminderRepository,
    private val chats: ChatRepository,
    private val messages: MessageRepository,
    private val users: UserRepository,
    private val clock: WallClock,
    private val timePatterns: TimePatterns,
    private val locale: () -> Locale,
) : ViewModel() {
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        reminders: ReminderRepository,
        chats: ChatRepository,
        messages: MessageRepository,
        users: UserRepository,
        clock: WallClock,
        timePatterns: TimePatterns,
    ) : this(savedStateHandle, reminders, chats, messages, users, clock, timePatterns, Locale::getDefault)

    private val sort = savedStateHandle.getStateFlow(KEY_SORT, ReminderSort.ByTime.name)

    /** The messages' previews read so far, by chat and message; null where TDLib no longer has the message. */
    private val previews = MutableStateFlow<Map<Pair<Long, Long>, UiText?>>(emptyMap())

    private var deleted: MessageReminder? = null
    private var labels: ReminderTimeLabels? = null

    private val effectChannel = Channel<RemindersEffect>(Channel.BUFFERED)
    val effects: Flow<RemindersEffect> = effectChannel.receiveAsFlow()

    val uiState: StateFlow<RemindersUiState> =
        reminders.reminders
            .onEach { list -> list.forEach(::loadPreview) }
            .flatMapLatest { list ->
                val chatIds = list.map { it.chatId }.distinct()
                val chatsShown: Flow<Map<Long, Chat>> =
                    if (chatIds.isEmpty()) {
                        flowOf(emptyMap())
                    } else {
                        combine(chatIds.map(chats::chat)) { all -> all.filterNotNull().associateBy { it.id } }
                    }
                combine(chatsShown, previews, sort, clock.minutes) { byId, shown, sortName, now ->
                    stateOf(list, byId, shown, ReminderSort.valueOf(sortName), now)
                }
            }.distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), RemindersUiState.Loading)

    fun onEvent(event: RemindersEvent) {
        when (event) {
            is RemindersEvent.Open -> effectChannel.trySend(RemindersEffect.OpenMessage(event.chatId, event.messageId))
            is RemindersEvent.ToggleDone -> change(event.chatId, event.messageId) { it.copy(isDone = !it.isDone) }
            is RemindersEvent.Delete -> delete(event.chatId, event.messageId)
            RemindersEvent.UndoDelete -> undo()
            is RemindersEvent.Sort -> savedStateHandle[KEY_SORT] = event.sort.name
        }
    }

    private fun stateOf(
        list: List<MessageReminder>,
        byId: Map<Long, Chat>,
        shown: Map<Pair<Long, Long>, UiText?>,
        sort: ReminderSort,
        now: Instant,
    ): RemindersUiState {
        if (list.isEmpty()) return RemindersUiState.Empty
        val rows =
            list.map { reminder ->
                val chat = byId[reminder.chatId]
                ReminderRow(
                    chatId = reminder.chatId,
                    messageId = reminder.messageId,
                    chatTitle = chat?.title.orEmpty(),
                    photo = chat?.photo,
                    avatarShape = chat?.type.avatarShape(),
                    time = if (reminder.isDone) labels().day(reminder.at, now) else labels().label(reminder.at, now),
                    preview = shown[reminder.chatId to reminder.messageId],
                    note = reminder.note,
                    isDone = reminder.isDone,
                    isOverdue = !reminder.isDone && !reminder.at.isAfter(now),
                )
            }
        val ordered = if (sort == ReminderSort.ByChat) rows.sortedBy { it.chatTitle.lowercase(locale()) } else rows
        return RemindersUiState.Content(
            upcoming = ordered.filterNot { it.isDone }.toImmutableList(),
            done = ordered.filter { it.isDone }.toImmutableList(),
            sort = sort,
        )
    }

    /** Reads [reminder]'s message once for the page: as the chat list previews it, after its sender in groups. */
    private fun loadPreview(reminder: MessageReminder) {
        val key = reminder.chatId to reminder.messageId
        if (key in previews.value) return
        previews.update { it + (key to null) }
        viewModelScope.launch {
            val message = messages.message(reminder.chatId, reminder.messageId).getOrNull() ?: return@launch
            val people = users.users.first()
            val chat = chats.chat(reminder.chatId).first()
            val name: (MessageSender) -> UiText? = { sender ->
                when (sender) {
                    is MessageSender.User -> people[sender.userId]?.shortName()
                    is MessageSender.Chat -> chat?.title?.let { UiText.Plain(it) }
                }
            }
            val text = messagePreviewText(message, users.myUserId.value, name)
            val sender = name(message.sender).takeIf { chat?.type.isGroup() }
            val preview = sender?.let { UiText.resource(R.string.reminders_preview_sender, it, text) } ?: text
            previews.update { it + (key to preview) }
        }
    }

    private fun change(
        chatId: Long,
        messageId: Long,
        edit: (MessageReminder) -> MessageReminder,
    ) {
        viewModelScope.launch {
            val reminder = reminders.reminders.first().firstOrNull { it.chatId == chatId && it.messageId == messageId }
            val outcome = reminder?.let { reminders.set(edit(it)) }
            if (outcome is Outcome.Failure) effectChannel.trySend(RemindersEffect.ShowError(outcome.error))
        }
    }

    private fun delete(
        chatId: Long,
        messageId: Long,
    ) {
        viewModelScope.launch {
            val reminder = reminders.reminders.first().firstOrNull { it.chatId == chatId && it.messageId == messageId }
            when (val outcome = reminders.delete(chatId, messageId)) {
                is Outcome.Success -> {
                    deleted = reminder
                    effectChannel.trySend(RemindersEffect.Deleted)
                }

                is Outcome.Failure -> {
                    effectChannel.trySend(RemindersEffect.ShowError(outcome.error))
                }
            }
        }
    }

    private fun undo() {
        val reminder = deleted ?: return
        deleted = null
        viewModelScope.launch {
            val outcome = reminders.set(reminder)
            if (outcome is Outcome.Failure) effectChannel.trySend(RemindersEffect.ShowError(outcome.error))
        }
    }

    /** The labels for the current time zone, language and clock setting, which can change while the app runs. */
    private fun labels(): ReminderTimeLabels {
        val zone = clock.zone()
        val locale = locale()
        val is24HourClock = timePatterns.is24HourClock()
        return labels?.takeIf { it.isFor(zone, locale, is24HourClock) }
            ?: ReminderTimeLabels(zone, locale, is24HourClock, timePatterns).also { labels = it }
    }

    private companion object {
        const val KEY_SORT = "reminders.sort"
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** The avatar's shape for a chat of this kind (`docs/DESIGN_SYSTEM.md` §13.2); a circle while unknown. */
private fun ChatType?.avatarShape(): AvatarShape =
    AvatarShape.ofChat(
        isForum = false,
        isGroup = this is ChatType.BasicGroup || (this is ChatType.Supergroup && !isChannel),
        isChannel = this is ChatType.Supergroup && isChannel,
    )

private fun ChatType?.isGroup(): Boolean = this is ChatType.BasicGroup || (this is ChatType.Supergroup && !isChannel)
