package dev.kyralabs.pingogram.feature.chat.ui.bookmarks

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
import dev.kyralabs.pingogram.core.domain.model.Bookmark
import dev.kyralabs.pingogram.core.domain.model.BookmarkPreview
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.repository.BookmarkRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.MessageRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.fullName
import dev.kyralabs.pingogram.core.ui.text.messagePreviewText
import dev.kyralabs.pingogram.feature.chat.ui.BookmarkEvent
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

/**
 * Bookmarking a message of chat [chatId] (`docs/DESIGN_SYSTEM.md` §6 "Bookmarks", the owner's board 34a), kept on this
 * device only ([BookmarkRepository]). The sheet shows what the message says ([MessageRepository.message]), the tags to
 * choose from (none is allowed, owner decision 2026-11-15) and a note; "Save" keeps what the message says alongside, so
 * the Bookmarks page can show it when the message is gone. Which message and which tags are chosen are in the saved
 * state; the note is the sheet's own field.
 */
@Suppress("LongParameterList") // The repositories a bookmark reads from and writes to, and the clock for its date.
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = BookmarkSheetViewModel.Factory::class)
class BookmarkSheetViewModel
    @AssistedInject
    constructor(
        @Assisted private val chatId: Long,
        private val savedStateHandle: SavedStateHandle,
        private val bookmarks: BookmarkRepository,
        private val messages: MessageRepository,
        private val chats: ChatRepository,
        private val users: UserRepository,
        private val clock: WallClock,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(chatId: Long): BookmarkSheetViewModel
        }

        private val messageId = savedStateHandle.getStateFlow(KEY_MESSAGE, 0L)
        private val chosen = savedStateHandle.getStateFlow(KEY_CHOSEN, LongArray(0))
        private val preview = MutableStateFlow<Pair<Long, LoadedPreview>?>(null)
        private val saving = MutableStateFlow(false)

        private val effectChannel = Channel<BookmarkEffect>(Channel.BUFFERED)
        val effects: Flow<BookmarkEffect> = effectChannel.receiveAsFlow()

        /** The sheet while it shows, else null. */
        val sheet: StateFlow<BookmarkSheetUiState?> =
            messageId
                .flatMapLatest { id ->
                    if (id == 0L) {
                        flowOf(null)
                    } else {
                        combine(
                            bookmarks.bookmark(chatId, id),
                            bookmarks.tags,
                            chosen,
                            preview,
                            saving,
                        ) { existing, tags, picked, loaded, isSaving ->
                            val shown =
                                loaded?.takeIf { it.first == id }?.second
                                    ?: existing?.preview?.let {
                                        LoadedPreview(
                                            UiText.Plain(it.sender).takeIf { _ ->
                                                it.sender.isNotEmpty()
                                            },
                                            UiText.Plain(it.text),
                                        )
                                    }
                            BookmarkSheetUiState(
                                messageId = id,
                                sender = shown?.sender,
                                text = shown?.text,
                                tags = tags.map { TagChoice(it, it.id in picked) }.toImmutableList(),
                                note = existing?.note.orEmpty(),
                                isBookmarked = existing != null,
                                isSaving = isSaving,
                            )
                        }
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

        init {
            // After process death the sheet reads its message again.
            messageId.value.takeIf { it != 0L }?.let(::loadPreview)
        }

        fun onEvent(event: BookmarkEvent) {
            when (event) {
                is BookmarkEvent.Open -> open(event.messageId)
                is BookmarkEvent.ToggleTag -> toggle(event.id)
                is BookmarkEvent.NewTag -> newTag(event.name)
                is BookmarkEvent.Save -> save(event.note, BookmarkPreview(event.sender, event.text))
                BookmarkEvent.Remove -> remove()
                BookmarkEvent.Dismiss -> close()
            }
        }

        private fun open(id: Long) {
            savedStateHandle[KEY_MESSAGE] = id
            loadPreview(id)
            viewModelScope.launch {
                val existing = bookmarks.bookmark(chatId, id).first()
                savedStateHandle[KEY_CHOSEN] = existing?.tagIds?.toLongArray() ?: LongArray(0)
            }
        }

        private fun loadPreview(id: Long) {
            viewModelScope.launch {
                val message = messages.message(chatId, id).getOrNull() ?: return@launch
                preview.value = id to previewOf(message)
            }
        }

        private suspend fun previewOf(message: Message): LoadedPreview {
            val people = users.users.first()
            val chat = chats.chat(chatId).first()
            val name: (MessageSender) -> UiText? = { sender ->
                when (sender) {
                    is MessageSender.User -> people[sender.userId]?.fullName()
                    is MessageSender.Chat -> chat?.title?.let { UiText.Plain(it) }
                }
            }
            return LoadedPreview(name(message.sender), messagePreviewText(message, users.myUserId.value, name))
        }

        private fun toggle(id: Long) {
            val now = chosen.value.toList()
            savedStateHandle[KEY_CHOSEN] = (if (id in now) now - id else now + id).toLongArray()
        }

        private fun newTag(name: String) {
            if (name.isBlank()) return
            viewModelScope.launch {
                val count = bookmarks.tags.first().size
                when (val added = bookmarks.addTag(name, color = count % TAG_COLORS)) {
                    is Outcome.Success -> {
                        savedStateHandle[KEY_CHOSEN] =
                            (chosen.value.toList() + added.value.id).toLongArray()
                    }

                    is Outcome.Failure -> {
                        effectChannel.trySend(BookmarkEffect.ShowError(added.error))
                    }
                }
            }
        }

        private fun save(
            note: String,
            shownPreview: BookmarkPreview,
        ) {
            val shown = sheet.value ?: return
            if (saving.value) return
            saving.value = true
            viewModelScope.launch {
                val existing = bookmarks.bookmark(chatId, shown.messageId).first()
                val bookmark =
                    Bookmark(
                        chatId = chatId,
                        messageId = shown.messageId,
                        savedAt = existing?.savedAt ?: clock.now().epochSecond,
                        tagIds = chosen.value.toList().toImmutableList(),
                        note = note.trim(),
                        preview = shownPreview,
                    )
                val outcome = bookmarks.save(bookmark)
                saving.value = false
                when (outcome) {
                    is Outcome.Success -> {
                        close()
                        effectChannel.trySend(BookmarkEffect.Saved)
                    }

                    is Outcome.Failure -> {
                        effectChannel.trySend(BookmarkEffect.ShowError(outcome.error))
                    }
                }
            }
        }

        private fun remove() {
            val id = messageId.value.takeIf { it != 0L } ?: return
            viewModelScope.launch {
                when (val outcome = bookmarks.remove(chatId, id)) {
                    is Outcome.Success -> {
                        close()
                        effectChannel.trySend(BookmarkEffect.Removed)
                    }

                    is Outcome.Failure -> {
                        effectChannel.trySend(BookmarkEffect.ShowError(outcome.error))
                    }
                }
            }
        }

        private fun close() {
            savedStateHandle[KEY_MESSAGE] = 0L
            savedStateHandle[KEY_CHOSEN] = LongArray(0)
        }

        private companion object {
            const val KEY_MESSAGE = "bookmark.message"
            const val KEY_CHOSEN = "bookmark.chosen"
            const val STOP_TIMEOUT_MILLIS = 5_000L

            /** The sender hues a tag's color is one of (`PingogramColors.senderHues`). */
            const val TAG_COLORS = 8
        }
    }

/** Who sent the message and what it says, as the sheet shows them. */
internal data class LoadedPreview(
    val sender: UiText?,
    val text: UiText,
)
