package dev.kyralabs.pingogram.feature.chat.ui.reminders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.getOrNull
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.MessageReminder
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.MessageRepository
import dev.kyralabs.pingogram.core.domain.repository.ReminderRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.fullName
import dev.kyralabs.pingogram.core.ui.text.messagePreviewText
import dev.kyralabs.pingogram.core.ui.time.ReminderTimeLabels
import dev.kyralabs.pingogram.core.ui.time.TimePatterns
import dev.kyralabs.pingogram.feature.chat.ui.ReminderEvent
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.Locale

/**
 * Setting a reminder on a message of chat [chatId] (`docs/DESIGN_SYSTEM.md` §6 "Message reminders", the owner's board
 * 29b), kept on this device only ([ReminderRepository]). The sheet shows what the message says
 * ([MessageRepository.message]), the quick choices with when each rings ([ReminderTimes]: "This evening" only before
 * 20:00), "Pick a date and time" and a note. "Set reminder" keeps it and tells when it rings, with "Undo", which puts
 * back what the message had before; a message with a reminder can have it deleted. Which message, which choice and the
 * moment picked are in the saved state; the note is the sheet's own field.
 */
@Suppress("LongParameterList") // The repositories a reminder reads from and writes to, and the clock for its times.
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = ReminderSheetViewModel.Factory::class)
class ReminderSheetViewModel internal constructor(
    private val chatId: Long,
    private val savedStateHandle: SavedStateHandle,
    private val reminders: ReminderRepository,
    private val messages: MessageRepository,
    private val chats: ChatRepository,
    private val users: UserRepository,
    private val clock: WallClock,
    private val timePatterns: TimePatterns,
    private val locale: () -> Locale,
) : ViewModel() {
    @AssistedInject
    constructor(
        @Assisted chatId: Long,
        savedStateHandle: SavedStateHandle,
        reminders: ReminderRepository,
        messages: MessageRepository,
        chats: ChatRepository,
        users: UserRepository,
        clock: WallClock,
        timePatterns: TimePatterns,
    ) : this(chatId, savedStateHandle, reminders, messages, chats, users, clock, timePatterns, Locale::getDefault)

    @AssistedFactory
    interface Factory {
        fun create(chatId: Long): ReminderSheetViewModel
    }

    private val messageId = savedStateHandle.getStateFlow(KEY_MESSAGE, 0L)
    private val choice = savedStateHandle.getStateFlow<String?>(KEY_CHOICE, null)
    private val picked = savedStateHandle.getStateFlow(KEY_PICKED, 0L)
    private val preview = MutableStateFlow<Pair<Long, LoadedPreview>?>(null)
    private val saving = MutableStateFlow(false)

    /** The last reminder set and what the message had before, for "Undo". */
    private var lastSet: Pair<MessageReminder, MessageReminder?>? = null

    private var labels: ReminderTimeLabels? = null

    private val effectChannel = Channel<ReminderEffect>(Channel.BUFFERED)
    val effects: Flow<ReminderEffect> = effectChannel.receiveAsFlow()

    /** The sheet while it shows, else null. */
    val sheet: StateFlow<ReminderSheetUiState?> =
        messageId
            .flatMapLatest { id ->
                if (id == 0L) {
                    flowOf(null)
                } else {
                    combine(
                        reminders.reminders(chatId).map { it[id] },
                        choice,
                        picked,
                        preview,
                        saving,
                    ) { existing, chosen, pickedAt, loaded, isSaving ->
                        val shown = loaded?.takeIf { it.first == id }?.second
                        ReminderSheetUiState(
                            messageId = id,
                            sender = shown?.sender,
                            text = shown?.text,
                            options = options(pickedAt),
                            chosen = chosen?.let(ReminderChoice::valueOf),
                            note = existing?.note.orEmpty(),
                            isExisting = existing != null,
                            isSaving = isSaving,
                        )
                    }
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /**
     * When each reminder of the chat that is not done yet rings, by message id, for the bubbles' chips
     * ([ReminderChips]); worded again whenever the chat's reminders change.
     */
    val chips: StateFlow<ImmutableMap<Long, UiText>> =
        reminders
            .reminders(chatId)
            .map { all ->
                all.values
                    .filterNot(MessageReminder::isDone)
                    .associate { it.messageId to labels().label(it.at, clock.now()) }
                    .toImmutableMap()
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), persistentMapOf())

    init {
        // After process death the sheet reads its message again.
        messageId.value.takeIf { it != 0L }?.let(::loadPreview)
    }

    fun onEvent(event: ReminderEvent) {
        when (event) {
            is ReminderEvent.Open -> {
                open(event.messageId)
            }

            is ReminderEvent.Choose -> {
                savedStateHandle[KEY_CHOICE] = event.choice.name
            }

            is ReminderEvent.Pick -> {
                savedStateHandle[KEY_PICKED] = event.epochSeconds
                savedStateHandle[KEY_CHOICE] = ReminderChoice.Custom.name
            }

            is ReminderEvent.Set -> {
                set(event.note)
            }

            ReminderEvent.Delete -> {
                delete()
            }

            ReminderEvent.Undo -> {
                undo()
            }

            ReminderEvent.Dismiss -> {
                close()
            }
        }
    }

    /** The choices offered as of now, each with when it rings; "This evening" left out after 20:00. */
    private fun options(pickedAt: Long): ImmutableList<ReminderOption> {
        val now = clock.now()
        val labels = labels()
        return ReminderChoice.entries
            .mapNotNull { choice ->
                if (choice == ReminderChoice.Custom) {
                    val picked = pickedAt.takeIf { it != 0L }?.let { labels.label(Instant.ofEpochSecond(it), now) }
                    ReminderOption(choice, picked)
                } else {
                    ReminderTimes.at(choice, now, clock.zone(), locale().firstDayOfWeek())?.let { at ->
                        val nextWeek = choice == ReminderChoice.NextWeek
                        ReminderOption(choice, if (nextWeek) labels.weekdayAndTime(at) else labels.label(at, now))
                    }
                }
            }.toImmutableList()
    }

    private fun open(id: Long) {
        savedStateHandle[KEY_MESSAGE] = id
        loadPreview(id)
        viewModelScope.launch {
            val existing = reminders.reminders(chatId).first()[id]
            if (existing != null) {
                savedStateHandle[KEY_PICKED] = existing.at.epochSecond
                savedStateHandle[KEY_CHOICE] = ReminderChoice.Custom.name
            } else {
                savedStateHandle[KEY_PICKED] = 0L
                savedStateHandle[KEY_CHOICE] = ReminderChoice.TomorrowMorning.name
            }
        }
    }

    /** Reads message [id] for the sheet: who sent it and what it says, as the chat list previews it. */
    private fun loadPreview(id: Long) {
        viewModelScope.launch {
            val message = messages.message(chatId, id).getOrNull() ?: return@launch
            val people = users.users.first()
            val chat = chats.chat(chatId).first()
            val name: (MessageSender) -> UiText? = { sender ->
                when (sender) {
                    is MessageSender.User -> people[sender.userId]?.fullName()
                    is MessageSender.Chat -> chat?.title?.let { UiText.Plain(it) }
                }
            }
            preview.value =
                id to LoadedPreview(name(message.sender), messagePreviewText(message, users.myUserId.value, name))
        }
    }

    /** When the chosen choice rings: the moment picked for [ReminderChoice.Custom]; null for none, or one gone by. */
    private fun chosenAt(): Instant? {
        val chosen = choice.value?.let(ReminderChoice::valueOf) ?: return null
        val now = clock.now()
        val at =
            if (chosen == ReminderChoice.Custom) {
                picked.value.takeIf { it != 0L }?.let(Instant::ofEpochSecond)
            } else {
                ReminderTimes.at(chosen, now, clock.zone(), locale().firstDayOfWeek())
            }
        return at?.takeIf { it.isAfter(now) }
    }

    private fun set(note: String) {
        val id = messageId.value
        val at = chosenAt()
        if (id == 0L || at == null || saving.value) return
        saving.value = true
        viewModelScope.launch {
            val before = reminders.reminders(chatId).first()[id]
            val reminder = MessageReminder(chatId, id, at, note.trim())
            val outcome = reminders.set(reminder)
            saving.value = false
            when (outcome) {
                is Outcome.Success -> {
                    lastSet = reminder to before
                    close()
                    effectChannel.trySend(ReminderEffect.Set(labels().label(at, clock.now())))
                }

                is Outcome.Failure -> {
                    effectChannel.trySend(ReminderEffect.ShowError(outcome.error))
                }
            }
        }
    }

    private fun delete() {
        val id = messageId.value.takeIf { it != 0L } ?: return
        viewModelScope.launch {
            when (val outcome = reminders.delete(chatId, id)) {
                is Outcome.Success -> {
                    close()
                    effectChannel.trySend(ReminderEffect.Deleted)
                }

                is Outcome.Failure -> {
                    effectChannel.trySend(ReminderEffect.ShowError(outcome.error))
                }
            }
        }
    }

    /** Puts back what the message had before the last reminder set: its earlier reminder, or none. */
    private fun undo() {
        val (set, before) = lastSet ?: return
        lastSet = null
        viewModelScope.launch {
            val outcome = if (before != null) reminders.set(before) else reminders.delete(set.chatId, set.messageId)
            if (outcome is Outcome.Failure) effectChannel.trySend(ReminderEffect.ShowError(outcome.error))
        }
    }

    private fun close() {
        savedStateHandle[KEY_MESSAGE] = 0L
        savedStateHandle[KEY_CHOICE] = null
        savedStateHandle[KEY_PICKED] = 0L
    }

    /** The labels for the current time zone, language and clock setting, made again only when one of them changed. */
    private fun labels(): ReminderTimeLabels {
        val zone = clock.zone()
        val locale = locale()
        val is24HourClock = timePatterns.is24HourClock()
        return labels?.takeIf { it.isFor(zone, locale, is24HourClock) }
            ?: ReminderTimeLabels(zone, locale, is24HourClock, timePatterns).also { labels = it }
    }

    private companion object {
        const val KEY_MESSAGE = "reminder.message"
        const val KEY_CHOICE = "reminder.choice"
        const val KEY_PICKED = "reminder.picked"
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** Who sent the message and what it says, as the sheet shows them. */
internal data class LoadedPreview(
    val sender: UiText?,
    val text: UiText,
)
