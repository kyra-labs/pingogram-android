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
import dev.kyralabs.pingogram.core.domain.model.BookmarkPreview
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.Reminder
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.MessageRepository
import dev.kyralabs.pingogram.core.domain.repository.ReminderRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.fullName
import dev.kyralabs.pingogram.core.ui.text.messagePreviewText
import dev.kyralabs.pingogram.core.ui.time.TimePatterns
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatTimeFormatter
import dev.kyralabs.pingogram.feature.chat.ui.ReminderEvent
import kotlinx.collections.immutable.toImmutableList
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
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Setting a reminder of a message of chat [chatId] (`docs/DESIGN_SYSTEM.md` §6 "Message reminders", the owner's board
 * 29b), kept on this device only ([ReminderRepository]); `core:notifications`' `ReminderAlarms` rings it. The sheet
 * shows what the message says ([MessageRepository.message]), the quick choices with their times ([presetAt]) or a
 * picked date and time, and a note; "Set reminder" keeps what the message says alongside, for the notification and
 * the Reminders page. The message, the choice and the picked time are in the saved state; the note is the sheet's own
 * field. "Undo" in the snackbar puts back what there was before.
 */
@Suppress("LongParameterList") // The repositories a reminder reads from and writes to, and the clock for its times.
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = ReminderSheetViewModel.Factory::class)
class ReminderSheetViewModel
    @AssistedInject
    constructor(
        @Assisted private val chatId: Long,
        private val savedStateHandle: SavedStateHandle,
        private val reminders: ReminderRepository,
        private val messages: MessageRepository,
        private val chats: ChatRepository,
        private val users: UserRepository,
        private val clock: WallClock,
        private val timePatterns: TimePatterns,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(chatId: Long): ReminderSheetViewModel
        }

        private val messageId = savedStateHandle.getStateFlow(KEY_MESSAGE, 0L)
        private val option = savedStateHandle.getStateFlow(KEY_OPTION, ReminderOption.InOneHour.name)
        private val customAt = savedStateHandle.getStateFlow(KEY_CUSTOM, 0L)
        private val preview = MutableStateFlow<Pair<Long, LoadedReminderPreview>?>(null)
        private val saving = MutableStateFlow(false)

        /** What there was before the last reminder set, for "Undo": the message's earlier reminder, or none. */
        private var undo: UndoReminder? = null

        private val effectChannel = Channel<ReminderEffect>(Channel.BUFFERED)
        val effects: Flow<ReminderEffect> = effectChannel.receiveAsFlow()

        /** The sheet while it shows, else null. */
        val sheet: StateFlow<ReminderSheetUiState?> =
            messageId
                .flatMapLatest { id ->
                    if (id == 0L) {
                        flowOf(null)
                    } else {
                        combine(reminders.reminder(chatId, id), option, customAt, preview, saving) {
                            existing,
                            chosen,
                            custom,
                            loaded,
                            isSaving,
                            ->
                            val shown =
                                loaded?.takeIf { it.first == id }?.second
                                    ?: existing?.preview?.let { LoadedReminderPreview.of(it) }
                            ReminderSheetUiState(
                                messageId = id,
                                sender = shown?.sender,
                                text = shown?.text,
                                choices = choices(custom.takeIf { it != 0L }),
                                selected = ReminderOption.valueOf(chosen),
                                note = existing?.note.orEmpty(),
                                hasReminder = existing != null && !existing.done,
                                isSaving = isSaving,
                            )
                        }
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

        init {
            // After process death the sheet reads its message again.
            messageId.value.takeIf { it != 0L }?.let(::loadPreview)
        }

        fun onEvent(event: ReminderEvent) {
            when (event) {
                is ReminderEvent.Open -> open(event.messageId)
                is ReminderEvent.Choose -> savedStateHandle[KEY_OPTION] = event.option.name
                is ReminderEvent.PickCustom -> pickCustom(event.at)
                is ReminderEvent.Save -> save(event.note, BookmarkPreview(event.sender, event.text))
                ReminderEvent.Remove -> remove()
                ReminderEvent.Undo -> undoLast()
                ReminderEvent.Dismiss -> close()
            }
        }

        private fun open(id: Long) {
            savedStateHandle[KEY_MESSAGE] = id
            loadPreview(id)
            viewModelScope.launch {
                val existing = reminders.reminder(chatId, id).first()?.takeIf { !it.done }
                savedStateHandle[KEY_CUSTOM] = existing?.at ?: 0L
                savedStateHandle[KEY_OPTION] = (if (existing != null) ReminderOption.Custom else DEFAULT_OPTION).name
            }
        }

        private fun pickCustom(at: Long) {
            savedStateHandle[KEY_CUSTOM] = at
            savedStateHandle[KEY_OPTION] = ReminderOption.Custom.name
        }

        private fun choices(custom: Long?) =
            ReminderOption.entries
                .mapNotNull { choice ->
                    val at =
                        if (choice ==
                            ReminderOption.Custom
                        ) {
                            custom
                        } else {
                            presetAt(choice, clock.now(), clock.zone())
                        }
                    when {
                        choice == ReminderOption.Custom -> ReminderChoice(choice, at, at?.let(::whenText))
                        at == null -> null
                        else -> ReminderChoice(choice, at, whenText(at))
                    }
                }.toImmutableList()

        private fun whenText(at: Long): UiText {
            val formatter =
                ChatTimeFormatter(clock.zone(), Locale.getDefault(), timePatterns.is24HourClock(), timePatterns)
            val today = clock.now().atZone(clock.zone()).toLocalDate()
            return UiText.resource(
                R.string.reminder_day_time,
                formatter.dateLabel(formatter.day(at), today),
                formatter.time(at),
            )
        }

        private fun loadPreview(id: Long) {
            viewModelScope.launch {
                val message = messages.message(chatId, id).getOrNull() ?: return@launch
                preview.value = id to previewOf(message)
            }
        }

        private suspend fun previewOf(message: Message): LoadedReminderPreview {
            val people = users.users.first()
            val chat = chats.chat(chatId).first()
            val name: (MessageSender) -> UiText? = { sender ->
                when (sender) {
                    is MessageSender.User -> people[sender.userId]?.fullName()
                    is MessageSender.Chat -> chat?.title?.let { UiText.Plain(it) }
                }
            }
            return LoadedReminderPreview(name(message.sender), messagePreviewText(message, users.myUserId.value, name))
        }

        private fun save(
            note: String,
            shownPreview: BookmarkPreview,
        ) {
            val shown = sheet.value ?: return
            val at = shown.at?.takeIf { it > clock.now().epochSecond }
            if (saving.value || at == null) return
            saving.value = true
            viewModelScope.launch {
                val before = reminders.reminder(chatId, shown.messageId).first()
                val reminder = Reminder(chatId, shown.messageId, at, note.trim(), shownPreview)
                val outcome = reminders.save(reminder)
                saving.value = false
                when (outcome) {
                    is Outcome.Success -> {
                        undo = UndoReminder(shown.messageId, before)
                        close()
                        effectChannel.trySend(ReminderEffect.Set(whenText(at)))
                    }

                    is Outcome.Failure -> {
                        effectChannel.trySend(ReminderEffect.ShowError(outcome.error))
                    }
                }
            }
        }

        private fun remove() {
            val id = messageId.value.takeIf { it != 0L } ?: return
            viewModelScope.launch {
                when (val outcome = reminders.remove(chatId, id)) {
                    is Outcome.Success -> {
                        close()
                        effectChannel.trySend(ReminderEffect.Removed)
                    }

                    is Outcome.Failure -> {
                        effectChannel.trySend(ReminderEffect.ShowError(outcome.error))
                    }
                }
            }
        }

        private fun undoLast() {
            val last = undo ?: return
            undo = null
            viewModelScope.launch {
                val outcome = last.before?.let { reminders.save(it) } ?: reminders.remove(chatId, last.messageId)
                if (outcome is Outcome.Failure) effectChannel.trySend(ReminderEffect.ShowError(outcome.error))
            }
        }

        private fun close() {
            savedStateHandle[KEY_MESSAGE] = 0L
            savedStateHandle[KEY_CUSTOM] = 0L
            savedStateHandle[KEY_OPTION] = DEFAULT_OPTION.name
        }

        private companion object {
            const val KEY_MESSAGE = "reminder.message"
            const val KEY_OPTION = "reminder.option"
            const val KEY_CUSTOM = "reminder.custom"
            const val STOP_TIMEOUT_MILLIS = 5_000L
            val DEFAULT_OPTION = ReminderOption.InOneHour
        }
    }

/** What the sheet shows of the message: who sent it and what it says. */
private class LoadedReminderPreview(
    val sender: UiText?,
    val text: UiText,
) {
    companion object {
        fun of(preview: BookmarkPreview) =
            LoadedReminderPreview(
                UiText.Plain(preview.sender).takeIf { preview.sender.isNotEmpty() },
                UiText.Plain(preview.text),
            )
    }
}

/** The message whose reminder was set last, and what it had before ([before], null for none). */
private class UndoReminder(
    val messageId: Long,
    val before: Reminder?,
)
