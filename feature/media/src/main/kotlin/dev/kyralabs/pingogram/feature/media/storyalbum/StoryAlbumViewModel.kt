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
import dev.kyralabs.pingogram.core.navigation.StoryAlbumRoute
import dev.kyralabs.pingogram.core.ui.time.TimePatterns
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
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
 * after process death.
 */
@HiltViewModel
class StoryAlbumViewModel internal constructor(
    private val route: StoryAlbumRoute,
    private val albums: StoryAlbumRepository,
    private val chats: ChatRepository,
    private val clock: WallClock,
    private val timePatterns: TimePatterns,
    private val locale: () -> Locale,
) : ViewModel() {
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        albums: StoryAlbumRepository,
        chats: ChatRepository,
        clock: WallClock,
        timePatterns: TimePatterns,
    ) : this(savedStateHandle.toRoute<StoryAlbumRoute>(), albums, chats, clock, timePatterns, Locale::getDefault)

    private val state = MutableStateFlow<StoryAlbumUiState>(StoryAlbumUiState.Loading)

    val uiState: StateFlow<StoryAlbumUiState> =
        state
            .onStart { if (loading == null) load() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), StoryAlbumUiState.Loading)

    private var loading: Job? = null

    fun onEvent(event: StoryAlbumEvent) {
        when (event) {
            StoryAlbumEvent.LoadMore -> loadMore()
            StoryAlbumEvent.Retry -> if (state.value is StoryAlbumUiState.Error) load() else loadMore(retry = true)
        }
    }

    private fun load() {
        state.value = StoryAlbumUiState.Loading
        loading =
            viewModelScope.launch {
                val page = albums.stories(route.chatId, route.albumId, offset = 0, limit = PAGE_SIZE)
                state.value =
                    when (page) {
                        is Outcome.Failure -> StoryAlbumUiState.Error(page.error)
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
        )
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
