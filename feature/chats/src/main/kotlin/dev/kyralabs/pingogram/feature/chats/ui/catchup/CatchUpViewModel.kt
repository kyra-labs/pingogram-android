package dev.kyralabs.pingogram.feature.chats.ui.catchup

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.Default
import dev.kyralabs.pingogram.core.common.result.onFailure
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.ui.time.TimePatterns
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
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import kotlin.math.ceil

/**
 * Catch-up (`docs/DESIGN_SYSTEM.md` §6 "Catch-up", the owner's board 32): the unread chats [CatchUpChats] takes, one
 * card at a time.
 *
 * - **The round** is the chats as they were when Catch-up opened, in their order (mentions first, then the most
 *   recent; owner decision 2026-11-15); a chat that leaves the main list meanwhile leaves the round. Including muted
 *   chats and channels, or leaving them out again, takes the chats not handled yet afresh.
 * - **Marking as read** is what the chat list's "Mark as read" does ([ChatRepository.markAsRead]: TDLib's
 *   `viewMessages`), and only for the chat the user marked: nothing reads a chat the user did not mark or open (owner
 *   decision 2026-11-15). A chat the user opened, or read anywhere else, counts as read once TDLib says it has
 *   nothing unread.
 * - **Replying** in place ([CatchUpReply]) answers the card's message and then marks the chat as read.
 * - **Skipping** leaves a chat for "Review skipped", a new round of the skipped chats once the round is over.
 * - **A card** shows the newest unread messages of its chat (its history from TDLib's local database, as the
 *   conversation loads it; loading it reads nothing), the older ones on "N earlier messages".
 *
 * Where it stands is kept in the saved state, so a process death resumes the round where it was.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CatchUpViewModel internal constructor(
    private val savedStateHandle: SavedStateHandle,
    private val catchUpChats: CatchUpChats,
    private val sources: CatchUpSources,
    private val clock: WallClock,
    private val cardMapper: CatchUpCardMapper,
    private val dispatcher: CoroutineDispatcher,
) : ViewModel() {
    @Inject
    internal constructor(
        savedStateHandle: SavedStateHandle,
        catchUpChats: CatchUpChats,
        sources: CatchUpSources,
        clock: WallClock,
        timePatterns: TimePatterns,
        @Dispatcher(Default) dispatcher: CoroutineDispatcher,
    ) : this(
        savedStateHandle,
        catchUpChats,
        sources,
        clock,
        CatchUpCardMapper(clock, timePatterns, Locale::getDefault),
        dispatcher,
    )

    private val round = MutableStateFlow(CatchUpRound.restore(savedStateHandle))
    private val expanded = savedStateHandle.getStateFlow(KEY_EXPANDED, 0L)
    private val settingsShown = savedStateHandle.getStateFlow(KEY_SETTINGS, false)

    /** The histories asked for, so each card loads once. */
    private val loadedHistories = mutableSetOf<Long>()

    private val effectChannel = Channel<CatchUpEffect>(Channel.BUFFERED)
    val effects: Flow<CatchUpEffect> = effectChannel.receiveAsFlow()

    private val reply =
        CatchUpReply(
            scope = viewModelScope,
            savedStateHandle = savedStateHandle,
            sources = sources,
            onSent = ::replied,
            onError = { effectChannel.trySend(CatchUpEffect.ShowError(it)) },
        )

    private val cards: Flow<List<CatchUpCard>> =
        combine(
            round.map { it?.queue?.take(2).orEmpty() }.distinctUntilChanged(),
            expanded,
        ) { ids, open -> ids to open }
            .flatMapLatest { (ids, open) ->
                if (ids.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    ids.forEach(::loadHistory)
                    combine(ids.map { id -> sources.card(id, id == open, clock.minutes, cardMapper) }) {
                        it.filterNotNull()
                    }
                }
            }

    val uiState: StateFlow<CatchUpUiState> =
        combine(
            round,
            cards,
            catchUpChats.includesMuted,
            settingsShown,
            reply.state(round.map { it?.queue?.firstOrNull() }),
        ) { round, cards, includesMuted, settings, replyState ->
            val stage = catchUpStage(round, cards, clock.now().toEpochMilli())
            CatchUpUiState(
                stage = if (stage is CatchUpStage.Cards) stage.copy(reply = replyState) else stage,
                includesMuted = includesMuted,
                showsSettings = settings,
            )
        }.flowOn(dispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), CatchUpUiState())

    init {
        viewModelScope.launch(dispatcher) {
            if (round.value == null) {
                val ids = catchUpChats.chats.first().map { it.id }
                save(CatchUpRound(queue = ids, total = ids.size, startedAt = clock.now().toEpochMilli()))
            }
        }
        // The shown chat read elsewhere (its conversation, another device) counts as read; one that left counts not.
        viewModelScope.launch(dispatcher) {
            round
                .map { it?.queue?.firstOrNull() }
                .distinctUntilChanged()
                .flatMapLatest { id -> id?.let(sources.chatRepository::chat)?.map { id to it } ?: flowOf(null) }
                .collect { shown ->
                    val (id, chat) = shown ?: return@collect
                    when {
                        chat == null -> advance(id) { it }
                        chat.unreadCount == 0 && !chat.isMarkedAsUnread -> advance(id) { it.copy(read = it.read + id) }
                    }
                }
        }
    }

    fun onEvent(event: CatchUpEvent) {
        when (event) {
            CatchUpEvent.MarkAsRead -> markAsRead()
            CatchUpEvent.Skip -> shown()?.let { id -> advance(id) { it.copy(skipped = it.skipped + id) } }
            CatchUpEvent.OpenChat -> shown()?.let { effectChannel.trySend(CatchUpEffect.OpenChat(it)) }
            CatchUpEvent.ShowEarlier -> shown()?.let { savedStateHandle[KEY_EXPANDED] = it }
            CatchUpEvent.Reply -> shown()?.let(reply::start)
            CatchUpEvent.CancelReply -> reply.cancel()
            is CatchUpEvent.SendReply -> sendReply(event.text)
            CatchUpEvent.ReviewSkipped -> reviewSkipped()
            is CatchUpEvent.ShowSettings -> savedStateHandle[KEY_SETTINGS] = event.show
            is CatchUpEvent.SetIncludesMuted -> setIncludesMuted(event.include)
        }
    }

    private fun shown(): Long? = round.value?.queue?.firstOrNull()

    private fun markAsRead() {
        val id = shown() ?: return
        advance(id) { it.copy(read = it.read + id) }
        viewModelScope.launch {
            sources.chatRepository.markAsRead(id).onFailure { effectChannel.trySend(CatchUpEffect.ShowError(it)) }
        }
    }

    private fun sendReply(text: String) {
        val cards = uiState.value.stage as? CatchUpStage.Cards ?: return
        reply.send(cards.current.chatId, cards.current.replyTo?.id, text)
    }

    /** A reply went to chat [chatId]: it is read, as after "Mark as read", and counts as replied. */
    private fun replied(chatId: Long) {
        advance(chatId) { it.copy(replied = it.replied + chatId) }
        viewModelScope.launch {
            sources.chatRepository.markAsRead(chatId).onFailure { effectChannel.trySend(CatchUpEffect.ShowError(it)) }
        }
    }

    private fun reviewSkipped() {
        val current = round.value ?: return
        if (current.queue.isNotEmpty() || current.skipped.isEmpty()) return
        save(current.copy(queue = current.skipped, total = current.skipped.size, skipped = emptyList()))
    }

    private fun setIncludesMuted(include: Boolean) {
        viewModelScope.launch(dispatcher) {
            catchUpChats.setIncludesMuted(include).onFailure {
                effectChannel.trySend(CatchUpEffect.ShowError(it))
                return@launch
            }
            val chats = catchUpChats.chats.first { catchUpChats.includesMuted.first() == include }
            val current = round.value ?: return@launch
            val handled = current.read.toSet() + current.replied + current.skipped
            val queue = chats.map { it.id }.filterNot { it in handled }
            val doneInRound = current.total - current.queue.size
            save(current.copy(queue = queue, total = doneInRound + queue.size))
        }
    }

    /** Takes chat [id] off the round's queue, if it is the shown one, and counts it as [change] says. */
    private fun advance(
        id: Long,
        change: (CatchUpRound) -> CatchUpRound,
    ) {
        val current = round.value ?: return
        if (current.queue.firstOrNull() != id) return
        save(change(current.copy(queue = current.queue.drop(1))))
    }

    private fun save(new: CatchUpRound) {
        round.update { new }
        new.saveTo(savedStateHandle)
    }

    private fun loadHistory(chatId: Long) {
        if (!loadedHistories.add(chatId)) return
        viewModelScope.launch { sources.messageRepository.loadNewest(chatId) }
    }

    private companion object {
        const val KEY_EXPANDED = "catchUp.expanded"
        const val KEY_SETTINGS = "catchUp.settings"
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/**
 * Where Catch-up stands: the chats still to show ([queue], the shown one first) of the round's [total], the chats
 * marked as [read], [replied] to and [skipped] (all rounds), and when it opened ([startedAt], Unix milliseconds).
 */
internal data class CatchUpRound(
    val queue: List<Long>,
    val total: Int,
    val read: List<Long> = emptyList(),
    val replied: List<Long> = emptyList(),
    val skipped: List<Long> = emptyList(),
    val startedAt: Long,
) {
    fun saveTo(handle: SavedStateHandle) {
        handle[KEY_QUEUE] = queue.toLongArray()
        handle[KEY_TOTAL] = total
        handle[KEY_READ] = read.toLongArray()
        handle[KEY_REPLIED] = replied.toLongArray()
        handle[KEY_SKIPPED] = skipped.toLongArray()
        handle[KEY_STARTED] = startedAt
    }

    companion object {
        private const val KEY_QUEUE = "catchUp.queue"
        private const val KEY_TOTAL = "catchUp.total"
        private const val KEY_READ = "catchUp.read"
        private const val KEY_REPLIED = "catchUp.replied"
        private const val KEY_SKIPPED = "catchUp.skipped"
        private const val KEY_STARTED = "catchUp.started"

        /** The round kept in [handle]; null before Catch-up read its chats. */
        fun restore(handle: SavedStateHandle): CatchUpRound? {
            val queue = handle.get<LongArray>(KEY_QUEUE) ?: return null
            return CatchUpRound(
                queue = queue.toList(),
                total = handle[KEY_TOTAL] ?: queue.size,
                read = handle.get<LongArray>(KEY_READ)?.toList().orEmpty(),
                replied = handle.get<LongArray>(KEY_REPLIED)?.toList().orEmpty(),
                skipped = handle.get<LongArray>(KEY_SKIPPED)?.toList().orEmpty(),
                startedAt = handle[KEY_STARTED] ?: 0L,
            )
        }
    }
}

/** What Catch-up shows of [round] with its [cards] loaded, [now] in Unix milliseconds. */
internal fun catchUpStage(
    round: CatchUpRound?,
    cards: List<CatchUpCard>,
    now: Long,
): CatchUpStage {
    val first = round?.queue?.firstOrNull()
    val current = cards.firstOrNull { it.chatId == first }
    return when {
        round == null -> {
            CatchUpStage.Loading
        }

        round.total == 0 && round.read.isEmpty() && round.replied.isEmpty() -> {
            CatchUpStage.Empty
        }

        first == null -> {
            CatchUpStage.Done(
                chats = round.read.size + round.replied.size + round.skipped.size,
                minutes = minutesBetween(round.startedAt, now),
                read = round.read.size,
                replied = round.replied.size,
                skipped = round.skipped.size,
            )
        }

        current == null -> {
            CatchUpStage.Loading
        }

        else -> {
            CatchUpStage.Cards(
                position = round.total - round.queue.size + 1,
                total = round.total,
                minutesLeft = ceil(round.queue.size * SECONDS_PER_CHAT / SECONDS_PER_MINUTE).toInt(),
                current = current,
                next = round.queue.getOrNull(1)?.let { next -> cards.firstOrNull { it.chatId == next } },
            )
        }
    }
}

private fun minutesBetween(
    startedAt: Long,
    now: Long,
): Int {
    val seconds = (now - startedAt).coerceAtLeast(0) / MILLIS_PER_SECOND
    return ceil(seconds / SECONDS_PER_MINUTE).toInt().coerceAtLeast(1)
}

private const val SECONDS_PER_CHAT = 20.0
private const val SECONDS_PER_MINUTE = 60.0
private const val MILLIS_PER_SECOND = 1_000.0
