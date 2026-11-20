package dev.kyralabs.pingogram.feature.media.storyalbum

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.StoryAlbumPage
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.StoryAlbumRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.navigation.StoryAlbumRoute
import dev.kyralabs.pingogram.core.ui.time.TimePatterns
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/**
 * An opened story album (board 11b): its name (from the chat's albums, as td_api.tl has no request for one album), the
 * chat that posted it, how many stories it holds, and its stories, [PAGE_SIZE] at a time as the grid nears its end
 * ([StoryAlbumEvent.LoadMore]), each with the day it was posted in the zone and language of the moment. Its work
 * starts once the screen first collects the state. Nothing is in its saved state but the route: the album loads again
 * after process death. The user's own album (a chat's id is its user's, owner decision 2026-11-20) can be renamed and
 * deleted ([StoryAlbumEffect.Deleted]); refusals are [StoryAlbumEffect.ShowError]s. Coming back to it reads it again
 * quietly ([StoryAlbumEvent.Refresh]), as its stories may have been changed meanwhile.
 */
@HiltViewModel
class StoryAlbumViewModel internal constructor(
    private val route: StoryAlbumRoute,
    private val albums: StoryAlbumRepository,
    private val chats: ChatRepository,
    private val users: UserRepository,
    private val clock: WallClock,
    private val timePatterns: TimePatterns,
    private val locale: () -> Locale = Locale::getDefault,
) : ViewModel() {
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        albums: StoryAlbumRepository,
        chats: ChatRepository,
        users: UserRepository,
        clock: WallClock,
        timePatterns: TimePatterns,
    ) : this(savedStateHandle.toRoute<StoryAlbumRoute>(), albums, chats, users, clock, timePatterns)

    private val state = MutableStateFlow<StoryAlbumUiState>(StoryAlbumUiState.Loading)

    val uiState: StateFlow<StoryAlbumUiState> =
        state
            .onStart { if (loading == null) load() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), StoryAlbumUiState.Loading)

    private var loading: Job? = null

    private val effectChannel = Channel<StoryAlbumEffect>(Channel.BUFFERED)
    val effects: Flow<StoryAlbumEffect> = effectChannel.receiveAsFlow()

    fun onEvent(event: StoryAlbumEvent) {
        when (event) {
            StoryAlbumEvent.LoadMore -> loadMore()
            StoryAlbumEvent.Retry -> if (state.value is StoryAlbumUiState.Error) load() else loadMore(retry = true)
            StoryAlbumEvent.Refresh -> if (state.value is StoryAlbumUiState.Content) load(quietly = true)
            is StoryAlbumEvent.Rename -> rename(event.name.trim())
            StoryAlbumEvent.Delete -> delete()
        }
    }

    private fun load(quietly: Boolean = false) {
        loading?.cancel()
        if (!quietly) state.value = StoryAlbumUiState.Loading
        loading =
            viewModelScope.launch {
                val page = albums.stories(route.chatId, route.albumId, offset = 0, limit = PAGE_SIZE)
                state.value =
                    when (page) {
                        is Outcome.Failure -> if (quietly) state.value else StoryAlbumUiState.Error(page.error)
                        is Outcome.Success -> firstPage(page.value)
                    }
            }
    }

    private suspend fun firstPage(page: StoryAlbumPage): StoryAlbumUiState.Content {
        val name = (albums.albums(route.chatId) as? Outcome.Success)?.value?.find { it.id == route.albumId }?.name
        return StoryAlbumUiState.Content(
            name = name.orEmpty(),
            posterName =
                chats
                    .chat(route.chatId)
                    .first()
                    ?.title
                    .orEmpty(),
            totalCount = page.totalCount,
            stories = mapped(page).toImmutableList(),
            canEdit = users.myUserId.value == route.chatId,
        )
    }

    private fun rename(name: String) {
        val content = state.value as? StoryAlbumUiState.Content ?: return
        if (!content.canEdit || name.isEmpty() || name == content.name) return
        viewModelScope.launch {
            when (val renamed = albums.renameAlbum(route.chatId, route.albumId, name)) {
                is Outcome.Success -> {
                    state.update { (it as? StoryAlbumUiState.Content)?.copy(name = renamed.value.name) ?: it }
                }

                is Outcome.Failure -> {
                    effectChannel.send(StoryAlbumEffect.ShowError(renamed.error))
                }
            }
        }
    }

    private fun delete() {
        if ((state.value as? StoryAlbumUiState.Content)?.canEdit != true) return
        viewModelScope.launch {
            when (val deleted = albums.deleteAlbum(route.chatId, route.albumId)) {
                is Outcome.Success -> effectChannel.send(StoryAlbumEffect.Deleted)
                is Outcome.Failure -> effectChannel.send(StoryAlbumEffect.ShowError(deleted.error))
            }
        }
    }

    private fun loadMore(retry: Boolean = false) {
        val content = state.value as? StoryAlbumUiState.Content ?: return
        val waiting = content.moreError != null && !retry
        if (content.loadingMore || content.endReached || waiting) return
        state.value = content.copy(loadingMore = true, moreError = null)
        loading =
            viewModelScope.launch {
                val page = albums.stories(route.chatId, route.albumId, content.stories.size, PAGE_SIZE)
                state.update { current ->
                    val shown = current as? StoryAlbumUiState.Content ?: return@update current
                    when (page) {
                        is Outcome.Failure -> {
                            shown.copy(loadingMore = false, moreError = page.error)
                        }

                        is Outcome.Success -> {
                            shown.copy(
                                // A page that brings nothing new ends the album, so it is not asked for again.
                                totalCount =
                                    if (page.value.stories.isEmpty()) shown.stories.size else page.value.totalCount,
                                stories = (shown.stories + mapped(page.value)).distinctBy { it.id }.toImmutableList(),
                                loadingMore = false,
                            )
                        }
                    }
                }
            }
    }

    private fun mapped(page: StoryAlbumPage): List<AlbumStory> {
        val formatter = AlbumDateFormatter(clock.zone(), locale(), timePatterns)
        val now = clock.now()
        return page.stories.map { it.toAlbumStory(formatter.format(it.date, now)) }
    }

    private companion object {
        /** About four screens of a phone's grid. */
        const val PAGE_SIZE = 30
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
