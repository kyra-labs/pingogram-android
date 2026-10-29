package dev.kyralabs.pingogram.feature.profile.chatinfo.actions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.ChatEvent
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.repository.ChatEventLogRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.navigation.RecentActionsRoute
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.fullName
import dev.kyralabs.pingogram.core.ui.text.isDeleted
import dev.kyralabs.pingogram.core.ui.time.TimePatterns
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The recent actions of supergroup or channel [chatId] (`REQUIREMENTS.md` FR-P3): `getChatEventLog` in pages of 50,
 * newest first, of the kinds the chips pick (none: every kind), the next page on "Load more". The chips picked are
 * kept in the [SavedStateHandle]. Names and photos come from [UserRepository] and [ChatRepository], asked once per
 * user or chat.
 */
@Suppress("LongParameterList") // The route, saved state, three repositories, clock and language.
@HiltViewModel
class RecentActionsViewModel internal constructor(
    private val chatId: Long,
    private val savedStateHandle: SavedStateHandle,
    private val repository: ChatEventLogRepository,
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
    private val clock: WallClock,
    private val timePatterns: TimePatterns,
    private val locale: () -> Locale,
) : ViewModel() {
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        repository: ChatEventLogRepository,
        chatRepository: ChatRepository,
        userRepository: UserRepository,
        clock: WallClock,
        timePatterns: TimePatterns,
    ) : this(
        savedStateHandle.toRoute<RecentActionsRoute>().chatId,
        savedStateHandle,
        repository,
        chatRepository,
        userRepository,
        clock,
        timePatterns,
        Locale::getDefault,
    )

    private val mutableUiState = MutableStateFlow<RecentActionsUiState>(RecentActionsUiState.Loading)
    val uiState: StateFlow<RecentActionsUiState> = mutableUiState.asStateFlow()

    private val effectChannel = Channel<RecentActionsEffect>(Channel.BUFFERED)
    val effects: Flow<RecentActionsEffect> = effectChannel.receiveAsFlow()

    /** The actions loaded, for the next page's start. */
    private var loaded = emptyList<ChatEvent>()

    /** The names of the people and chats met so far. */
    private val names = mutableMapOf<MessageSender, Named>()

    private var loading: Job? = null

    private val filters: Set<RecentActionsFilter>
        get() =
            savedStateHandle
                .get<ArrayList<String>>(KEY_FILTERS)
                .orEmpty()
                .mapNotNull { name -> RecentActionsFilter.entries.firstOrNull { it.name == name } }
                .toSet()

    init {
        load()
    }

    fun onEvent(event: RecentActionsEvent) {
        when (event) {
            is RecentActionsEvent.ToggleFilter -> {
                toggle(event.filter)
            }

            RecentActionsEvent.LoadMore -> {
                loadMore()
            }

            RecentActionsEvent.Retry -> {
                if (uiState.value is RecentActionsUiState.Error) load()
            }

            is RecentActionsEvent.Open -> {
                if (event.userId != 0L) {
                    effectChannel.trySend(RecentActionsEffect.OpenProfile(event.userId))
                }
            }
        }
    }

    private fun toggle(filter: RecentActionsFilter) {
        val picked = filters.let { if (filter in it) it - filter else it + filter }
        savedStateHandle[KEY_FILTERS] = ArrayList(picked.map { it.name })
        load()
    }

    private fun load() {
        loading?.cancel()
        val picked = filters
        mutableUiState.update { state ->
            if (state is RecentActionsUiState.Content) {
                state.copy(filters = picked.toPersistentSet(), isLoading = true, pageError = null)
            } else {
                RecentActionsUiState.Loading
            }
        }
        loading =
            viewModelScope.launch {
                when (val page = repository.events(chatId, fromEventId = 0, filters = picked.kinds())) {
                    is Outcome.Success -> {
                        loaded = page.value
                        mutableUiState.value =
                            RecentActionsUiState.Content(
                                filters = picked.toPersistentSet(),
                                actions = items(loaded),
                                hasMore = page.value.size >= ChatEventLogRepository.PAGE_SIZE,
                            )
                    }

                    is Outcome.Failure -> {
                        mutableUiState.value = RecentActionsUiState.Error(page.error)
                    }
                }
            }
    }

    private fun loadMore() {
        val content = uiState.value as? RecentActionsUiState.Content ?: return
        if (!content.canLoadMore || loaded.isEmpty()) return
        mutableUiState.value = content.copy(loadingMore = true, pageError = null)
        loading =
            viewModelScope.launch {
                val page = repository.events(chatId, fromEventId = loaded.last().id, filters = filters.kinds())
                when (page) {
                    is Outcome.Success -> {
                        val known = loaded.map { it.id }.toSet()
                        loaded = loaded + page.value.filter { it.id !in known }
                        val items = items(loaded)
                        val hasMore = page.value.size >= ChatEventLogRepository.PAGE_SIZE
                        updateContent { it.copy(actions = items, hasMore = hasMore, loadingMore = false) }
                    }

                    is Outcome.Failure -> {
                        updateContent { it.copy(loadingMore = false, pageError = page.error) }
                    }
                }
            }
    }

    private suspend fun items(events: List<ChatEvent>) =
        events
            .map { event ->
                val actor = named(event.actor)
                val others = collectNames(event)
                val name: (MessageSender) -> UiText = { sender -> (others[sender]?.name ?: UNKNOWN).bidiIsolated() }
                RecentActionItem(
                    id = event.id,
                    userId = (event.actor as? MessageSender.User)?.userId ?: 0,
                    avatarId = actor.avatarId,
                    name = actor.name,
                    photo = actor.photo,
                    text = actionText(event.action, name),
                    message = actionMessage(event.action) { sender -> others[sender]?.name },
                    time = time(event.date),
                )
            }.toImmutableList()

    /** The names of everyone [event] mentions, the senders of its messages included. */
    private suspend fun collectNames(event: ChatEvent): Map<MessageSender, Named> =
        event.action.senders().associateWith { named(it) }

    private suspend fun named(sender: MessageSender): Named =
        names.getOrPut(sender) {
            when (sender) {
                is MessageSender.User -> {
                    val user = userRepository.user(sender.userId).first()
                    Named(
                        name =
                            user?.takeUnless { it.isDeleted }?.fullName()
                                ?: UiText.resource(UiR.string.user_deleted_account),
                        photo = user?.profilePhoto,
                        avatarId = sender.userId,
                    )
                }

                is MessageSender.Chat -> {
                    val chat = chatRepository.chat(sender.chatId).first()
                    Named(UiText.Plain(chat?.title.orEmpty()), chat?.photo, sender.chatId)
                }
            }
        }

    /** "Oct 30, 2:05 PM" in the user's language and clock. */
    private fun time(epochSeconds: Long): String {
        val locale = locale()
        val time = Instant.ofEpochSecond(epochSeconds).atZone(clock.zone())
        val clockSkeleton = if (timePatterns.is24HourClock()) "Hm" else "hm"
        val day = DateTimeFormatter.ofPattern(timePatterns.bestPattern(locale, "MMMd"), locale)
        val hour = DateTimeFormatter.ofPattern(timePatterns.bestPattern(locale, clockSkeleton), locale)
        return "${day.format(time)}, ${hour.format(time)}"
    }

    private fun updateContent(transform: (RecentActionsUiState.Content) -> RecentActionsUiState.Content) =
        mutableUiState.update { if (it is RecentActionsUiState.Content) transform(it) else it }

    private class Named(
        val name: UiText,
        val photo: dev.kyralabs.pingogram.core.domain.model.ProfilePhoto?,
        val avatarId: Long,
    )

    private companion object {
        const val KEY_FILTERS = "recentActions.filters"
        val UNKNOWN = UiText.resource(UiR.string.user_deleted_account)
    }
}

/** TDLib's kinds of action for the chips picked; none picked is every kind. */
private fun Set<RecentActionsFilter>.kinds() = flatMap { it.kinds }.toSet()
