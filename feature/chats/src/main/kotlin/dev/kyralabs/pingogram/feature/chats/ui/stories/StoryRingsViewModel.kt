package dev.kyralabs.pingogram.feature.chats.ui.stories

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ActiveStories
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.core.domain.model.StoryList
import dev.kyralabs.pingogram.core.domain.repository.ChatListSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.StoryRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The story rings above the chat list: one per chat with active stories, in the stories list's order. */
@Immutable
data class StoryRingsUiState(
    val rings: ImmutableList<StoryRing> = persistentListOf(),
)

/**
 * The ring of chat [chatId]'s stories: its [name] (the user's own stories are [isMine]) and [photo], whether a story is
 * unseen ([hasUnread], a `primary` ring; seen ones `outlineVariant`), and how many there are.
 */
@Immutable
data class StoryRing(
    val chatId: Long,
    val name: String,
    val photo: ProfilePhoto?,
    val hasUnread: Boolean,
    val storyCount: Int,
    val isMine: Boolean = false,
)

/** What happens to the rings, handled by [StoryRingsViewModel.onEvent]. */
sealed interface StoryRingsEvent {
    /** The row's end came within a few rings of the screen: more chats with stories load. */
    data object NearEnd : StoryRingsEvent
}

/**
 * The story rings above the chat list (`docs/ROADMAP.md` M6, stories viewer): the chats of the main stories list
 * ([StoryList.Main], `StoryRepository.activeStories`) with their names and photos from [ChatRepository], in TDLib's
 * order, then after every change: a new story, one seen in the viewer, one expired. A chat TDLib has not announced yet
 * waits for it.
 *
 * - **Loading**: the first chats load once per session when the rings are first collected, in a dispatched coroutine
 *   (after the chat list's first frame, `docs/PERFORMANCE.md` §4); more as the row nears its end
 *   ([StoryRingsEvent.NearEnd]), one request at a time, until TDLib has no more. Failures leave the rings as they are:
 *   they are no more than a shortcut, and the row comes back with the next start.
 * - Opening a ring is the screen's: it goes straight to the viewer, which marks what it shows viewed.
 *
 * - **Hidden**: while Appearance → "Show stories" is off ([ChatListSettingsRepository]), there are no rings and nothing
 *   loads; turned on again, the rings load as at the first collection.
 *
 * Nothing to restore after process death: everything is read again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StoryRingsViewModel
    @Inject
    constructor(
        private val storyRepository: StoryRepository,
        chatRepository: ChatRepository,
        userRepository: UserRepository,
        chatListSettings: ChatListSettingsRepository,
    ) : ViewModel() {
        private var loadingMore: Job? = null
        private var allLoaded = false

        private val rings =
            storyRepository
                .activeStories(StoryList.Main)
                .flatMapLatest { active ->
                    if (active.isEmpty()) {
                        flowOf(persistentListOf())
                    } else {
                        combine(active.map { stories -> chatRepository.chat(stories.chatId).map { stories to it } }) {
                            it.toList()
                        }
                    }
                }.combine(userRepository.myUserId) { chats, me ->
                    StoryRingsUiState(
                        chats
                            .mapNotNull { (stories, chat) -> chat?.let { ringOf(stories, it, me) } }
                            .toImmutableList(),
                    )
                }.distinctUntilChanged()
                .onStart { viewModelScope.launch { storyRepository.loadActiveStories(StoryList.Main) } }

        val uiState: StateFlow<StoryRingsUiState> =
            chatListSettings.settings
                .map { it.showStories }
                .distinctUntilChanged()
                .flatMapLatest { shown -> if (shown) rings else flowOf(StoryRingsUiState()) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), StoryRingsUiState())

        fun onEvent(event: StoryRingsEvent) {
            when (event) {
                StoryRingsEvent.NearEnd -> {
                    if (allLoaded || loadingMore?.isActive == true) return
                    loadingMore =
                        viewModelScope.launch {
                            val loaded = storyRepository.loadMoreActiveStories(StoryList.Main)
                            if (loaded is Outcome.Success) allLoaded = !loaded.value
                        }
                }
            }
        }

        private fun ringOf(
            stories: ActiveStories,
            chat: Chat,
            me: Long?,
        ): StoryRing =
            StoryRing(
                chatId = stories.chatId,
                name = chat.title,
                photo = chat.photo,
                hasUnread = stories.hasUnread,
                storyCount = stories.stories.size,
                isMine = me != null && (chat.type as? ChatType.Private)?.userId == me,
            )

        private companion object {
            /** Keeps the rings through a rotation, as every screen's view model does. */
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
