package dev.kyralabs.pingogram.feature.media.story

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.core.domain.model.ReactionType
import dev.kyralabs.pingogram.core.domain.model.StoryViewer
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.repository.StoryRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Who saw the user's own story (board 26), for its list's sheet: [StoryViewersViewModel.show] it for a story, then
 * pages through `getStoryInteractions` (50 a page) as the list nears its end, searches names 300 ms after the last key,
 * and filters: everyone, those who reacted, or contacts. The names and photos come from the users TDLib announced.
 */
@HiltViewModel
class StoryViewersViewModel
    @Inject
    constructor(
        private val storyRepository: StoryRepository,
        userRepository: UserRepository,
        private val clock: WallClock,
    ) : ViewModel() {
        private val list = MutableStateFlow(ViewersList())
        private var loading: Job? = null

        val uiState: StateFlow<StoryViewersUiState> =
            combine(list, userRepository.users) { list, users -> list.toUiState(users, clock.now().epochSecond) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), StoryViewersUiState())

        /** Shows the viewers of story [storyId], from the first page; the same story again changes nothing. */
        fun show(storyId: Int) {
            if (list.value.storyId == storyId) return
            list.value = ViewersList(storyId = storyId)
            load(reset = true)
        }

        fun onEvent(event: StoryViewersEvent) {
            when (event) {
                is StoryViewersEvent.Query -> {
                    if (event.text == list.value.query) return
                    list.update { it.copy(query = event.text) }
                    load(reset = true, wait = true)
                }

                is StoryViewersEvent.Filter -> {
                    if (event.filter == list.value.filter) return
                    list.update { it.copy(filter = event.filter) }
                    load(reset = true)
                }

                StoryViewersEvent.LoadMore -> {
                    if (loading?.isActive != true && list.value.nextOffset != null) load(reset = false)
                }

                StoryViewersEvent.Retry -> {
                    load(reset = list.value.viewers.isEmpty())
                }
            }
        }

        private fun load(
            reset: Boolean,
            wait: Boolean = false,
        ) {
            val storyId = list.value.storyId ?: return
            loading?.cancel()
            loading =
                viewModelScope.launch {
                    if (wait) delay(QUERY_DELAY_MILLIS)
                    val current = list.value
                    list.update { it.copy(isLoading = true, error = null) }
                    val filter = current.filter
                    val offset = if (reset) "" else current.nextOffset.orEmpty()
                    val page =
                        storyRepository.viewers(
                            storyId = storyId,
                            query = current.query.trim(),
                            onlyContacts = filter == StoryViewersFilter.Contacts,
                            reactionsFirst = filter == StoryViewersFilter.Reactions,
                            offset = offset,
                        )
                    list.update { state ->
                        when (page) {
                            is Outcome.Success -> {
                                val loaded = page.value
                                val earlier = if (reset) persistentListOf() else state.viewers
                                state.copy(
                                    viewers = (earlier + loaded.viewers).toPersistentList(),
                                    total = loaded.totalCount,
                                    reactionTotal = loaded.reactionCount,
                                    nextOffset = loaded.nextOffset,
                                    isLoading = false,
                                    hasLoaded = true,
                                )
                            }

                            is Outcome.Failure -> {
                                state.copy(isLoading = false, error = page.error)
                            }
                        }
                    }
                }
        }

        /** The list as loaded: for [storyId], what was asked ([query], [filter]), the [viewers] and the next page. */
        private data class ViewersList(
            val storyId: Int? = null,
            val query: String = "",
            val filter: StoryViewersFilter = StoryViewersFilter.All,
            val viewers: List<StoryViewer> = emptyList(),
            val total: Int = 0,
            val reactionTotal: Int = 0,
            val nextOffset: String? = null,
            val isLoading: Boolean = false,
            val hasLoaded: Boolean = false,
            val error: DomainError? = null,
        ) {
            fun toUiState(
                users: Map<Long, User>,
                now: Long,
            ) = StoryViewersUiState(
                query = query,
                filter = filter,
                total = total,
                reactionTotal = reactionTotal,
                rows =
                    viewers
                        // "Reactions" shows those who reacted: TDLib only puts them first.
                        .filter { filter != StoryViewersFilter.Reactions || it.reaction != null }
                        .map { viewer ->
                            val user = users[viewer.userId]
                            StoryViewerRow(
                                userId = viewer.userId,
                                name = user?.displayName().orEmpty(),
                                photo = user?.profilePhoto,
                                ageSeconds = (now - viewer.date).coerceAtLeast(0),
                                reaction = (viewer.reaction as? ReactionType.Emoji)?.emoji,
                            )
                        }.toImmutableList(),
                isLoading = isLoading,
                hasLoaded = hasLoaded,
                hasMore = nextOffset != null,
                error = error,
            )
        }

        private companion object {
            const val QUERY_DELAY_MILLIS = 300L
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

/** Which of the viewers show: everyone, those who reacted, or contacts. */
enum class StoryViewersFilter {
    All,
    Reactions,
    Contacts,
}

/** The list of who saw the user's own story, as its sheet shows it. */
@Immutable
data class StoryViewersUiState(
    val query: String = "",
    val filter: StoryViewersFilter = StoryViewersFilter.All,
    val total: Int = 0,
    val reactionTotal: Int = 0,
    val rows: ImmutableList<StoryViewerRow> = persistentListOf(),
    val isLoading: Boolean = false,
    val hasLoaded: Boolean = false,
    val hasMore: Boolean = false,
    val error: DomainError? = null,
)

/** A viewer: user [userId] ([name], [photo]), who saw the story [ageSeconds] ago and reacted [reaction]. */
@Immutable
data class StoryViewerRow(
    val userId: Long,
    val name: String,
    val photo: ProfilePhoto?,
    val ageSeconds: Long,
    val reaction: String?,
)

/** "Ada Lovelace": the first and last names that are there. */
private fun User.displayName(): String = listOf(firstName, lastName).filter(String::isNotBlank).joinToString(" ")

/** What the user does in the list of viewers. */
sealed interface StoryViewersEvent {
    data class Query(
        val text: String,
    ) : StoryViewersEvent

    data class Filter(
        val filter: StoryViewersFilter,
    ) : StoryViewersEvent

    /** The list nears its end: the next page loads. */
    data object LoadMore : StoryViewersEvent

    data object Retry : StoryViewersEvent
}
