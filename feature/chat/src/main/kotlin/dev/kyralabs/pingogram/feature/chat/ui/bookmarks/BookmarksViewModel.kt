package dev.kyralabs.pingogram.feature.chat.ui.bookmarks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.Default
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.onFailure
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.Bookmark
import dev.kyralabs.pingogram.core.domain.model.BookmarkTag
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.repository.BookmarkRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.ui.component.AvatarShape
import dev.kyralabs.pingogram.core.ui.time.TimePatterns
import dev.kyralabs.pingogram.feature.chat.ui.ChatTimeFormatter
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.Locale
import javax.inject.Inject

/**
 * The Bookmarks page (`docs/DESIGN_SYSTEM.md` §6 "Bookmarks", the owner's board 34b), kept on this phone
 * ([BookmarkRepository]): every bookmark, the newest first (owner decision 2026-11-15), filtered by one tag (in the
 * saved state), each with its chat as TDLib knows it ([ChatRepository], asked for where it does not) and what the
 * message said when it was saved. Removing offers "Undo", which saves the bookmark back as it was; a card's "Edit tags"
 * opens the bookmark's own sheet ([editing], its chat in the saved state).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BookmarksViewModel internal constructor(
    private val savedStateHandle: SavedStateHandle,
    private val bookmarks: BookmarkRepository,
    private val chats: ChatRepository,
    private val times: BookmarkTimes,
    dispatcher: CoroutineDispatcher,
) : ViewModel() {
    @Inject
    internal constructor(
        savedStateHandle: SavedStateHandle,
        bookmarks: BookmarkRepository,
        chats: ChatRepository,
        clock: WallClock,
        timePatterns: TimePatterns,
        @Dispatcher(Default) dispatcher: CoroutineDispatcher,
    ) : this(savedStateHandle, bookmarks, chats, BookmarkTimes(clock, timePatterns, Locale::getDefault), dispatcher)

    private val selectedTag = savedStateHandle.getStateFlow(KEY_TAG, NO_TAG)
    private val query = savedStateHandle.getStateFlow<String?>(KEY_QUERY, null)
    private val editedChat = savedStateHandle.getStateFlow(KEY_EDITING, NO_CHAT)
    private val toOpen = MutableStateFlow<Long?>(null)
    private var removed: Bookmark? = null
    private val askedChats = mutableSetOf<Long>()

    private val effectChannel = Channel<BookmarksEffect>(Channel.BUFFERED)
    val effects: Flow<BookmarksEffect> = effectChannel.receiveAsFlow()

    private val chatsOfBookmarks: Flow<Map<Long, Chat>> =
        bookmarks.bookmarks
            .map { all -> all.map { it.chatId }.distinct() }
            .distinctUntilChanged()
            .flatMapLatest { ids ->
                if (ids.isEmpty()) {
                    flowOf(emptyMap())
                } else {
                    combine(ids.map { id -> chats.chat(id).onEach { if (it == null) askFor(id) } }) { known ->
                        known.filterNotNull().associateBy { it.id }
                    }
                }
            }

    val uiState: StateFlow<BookmarksUiState> =
        combine(
            bookmarks.bookmarks,
            bookmarks.tags,
            chatsOfBookmarks,
            combine(selectedTag, query, ::Pair),
            times.minutes,
        ) { all, tags, known, (selected, words), now ->
            val tag = selected.takeIf { it != NO_TAG && tags.any { tag -> tag.id == it } }
            stateOf(all, tags, known, Shown(tag, words), now)
        }.flowOn(dispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), BookmarksUiState.Loading)

    /** The bookmark being edited in its sheet, else null. */
    val editing: StateFlow<BookmarkEditing?> =
        combine(editedChat, toOpen) { chatId, messageId ->
            chatId.takeIf { it != NO_CHAT }?.let { BookmarkEditing(it, messageId) }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun onEvent(event: BookmarksEvent) {
        when (event) {
            is BookmarksEvent.SelectTag -> savedStateHandle[KEY_TAG] = event.tagId ?: NO_TAG
            is BookmarksEvent.Open -> effectChannel.trySend(BookmarksEffect.OpenMessage(event.chatId, event.messageId))
            is BookmarksEvent.Remove -> remove(event.chatId, event.messageId)
            BookmarksEvent.UndoRemove -> undo()
            BookmarksEvent.OpenSearch -> savedStateHandle[KEY_QUERY] = query.value.orEmpty()
            is BookmarksEvent.Search -> if (query.value != null) savedStateHandle[KEY_QUERY] = event.query
            BookmarksEvent.CloseSearch -> savedStateHandle[KEY_QUERY] = null
            is BookmarksEvent.Edit -> edit(event.chatId, event.messageId)
            BookmarksEvent.EditorOpened -> toOpen.value = null
            BookmarksEvent.EditorClosed -> savedStateHandle[KEY_EDITING] = NO_CHAT
        }
    }

    private fun remove(
        chatId: Long,
        messageId: Long,
    ) {
        viewModelScope.launch {
            removed = bookmarks.bookmark(chatId, messageId).first()
            val effect =
                when (val outcome = bookmarks.remove(chatId, messageId)) {
                    is Outcome.Success -> BookmarksEffect.Removed
                    is Outcome.Failure -> BookmarksEffect.ShowError(outcome.error)
                }
            effectChannel.trySend(effect)
        }
    }

    private fun edit(
        chatId: Long,
        messageId: Long,
    ) {
        toOpen.value = messageId
        savedStateHandle[KEY_EDITING] = chatId
    }

    private fun undo() {
        val bookmark = removed ?: return
        removed = null
        viewModelScope.launch {
            bookmarks.save(bookmark).onFailure { effectChannel.trySend(BookmarksEffect.ShowError(it)) }
        }
    }

    /** Makes TDLib announce a chat it has not, once. */
    private fun askFor(chatId: Long) {
        if (askedChats.add(chatId)) viewModelScope.launch { chats.getChat(chatId) }
    }

    private fun stateOf(
        all: List<Bookmark>,
        tags: List<BookmarkTag>,
        known: Map<Long, Chat>,
        shown: Shown,
        now: Instant,
    ): BookmarksUiState {
        val selected = shown.tagId
        if (all.isEmpty()) return BookmarksUiState.Empty
        val formatter = times.formatter()
        val filters =
            listOf(BookmarkFilter(null, "", color = 0, count = all.size)) +
                tags.map { tag -> BookmarkFilter(tag.id, tag.name, tag.color, all.count { tag.id in it.tagIds }) }
        val byId = tags.associateBy { it.id }
        val tagged = if (selected == null) all else all.filter { selected in it.tagIds }
        val search = BookmarkSearch.of(shown.query.orEmpty(), times.locale())
        return BookmarksUiState.Content(
            filters = filters.toImmutableList(),
            selectedTagId = selected,
            query = shown.query,
            items =
                tagged
                    .filter { bookmark -> search.matches(bookmark, known[bookmark.chatId], byId) }
                    .map { bookmark ->
                        val chat = known[bookmark.chatId]
                        BookmarkItem(
                            chatId = bookmark.chatId,
                            messageId = bookmark.messageId,
                            chatTitle = chat?.title.orEmpty(),
                            photo = chat?.photo,
                            avatarShape = chat.avatarShape(),
                            // A private chat's or a channel's own name says who sent it already.
                            sender =
                                bookmark.preview.sender
                                    .takeUnless { it == chat?.title }
                                    .orEmpty(),
                            text = bookmark.preview.text,
                            date = formatter.listTime(bookmark.savedAt, now),
                            note = bookmark.note,
                            tags =
                                bookmark.tagIds
                                    .mapNotNull(byId::get)
                                    .map { BookmarkFilter(it.id, it.name, it.color, count = 0) }
                                    .toImmutableList(),
                        )
                    }.toImmutableList(),
        )
    }

    private companion object {
        const val KEY_TAG = "bookmarks.tag"
        const val KEY_QUERY = "bookmarks.query"
        const val KEY_EDITING = "bookmarks.editing"
        const val NO_CHAT = 0L
        const val NO_TAG = 0L
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** What the page lets through: one tag ([tagId], null for all) and the search's words ([query], null while closed). */
private data class Shown(
    val tagId: Long?,
    val query: String?,
)

/** The avatar's shape of a bookmark's chat, by its kind (a circle while TDLib does not know it). */
private fun Chat?.avatarShape(): AvatarShape {
    val type = this?.type
    return AvatarShape.ofChat(
        isForum = false,
        isGroup = type is ChatType.BasicGroup || (type as? ChatType.Supergroup)?.isChannel == false,
        isChannel = (type as? ChatType.Supergroup)?.isChannel == true,
    )
}

/** When bookmarks were saved, worded for the reader's zone, language and clock, which can change while the app runs. */
internal class BookmarkTimes(
    private val clock: WallClock,
    private val timePatterns: TimePatterns,
    private val locale: () -> Locale,
) {
    private var formatter: ChatTimeFormatter? = null

    /** The minute, then each new one. */
    val minutes: Flow<Instant> get() = clock.minutes

    /** The reader's language now. */
    fun locale(): Locale = locale.invoke()

    fun formatter(): ChatTimeFormatter {
        val zone = clock.zone()
        val locale = locale()
        val is24HourClock = timePatterns.is24HourClock()
        return formatter?.takeIf { it.isFor(zone, locale, is24HourClock) }
            ?: ChatTimeFormatter(zone, locale, is24HourClock, timePatterns).also { formatter = it }
    }
}
