package dev.kyralabs.pingogram.feature.media.storyalbum

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.Story
import dev.kyralabs.pingogram.core.domain.model.StoryAlbum
import dev.kyralabs.pingogram.core.domain.repository.StoryAlbumRepository
import dev.kyralabs.pingogram.core.ui.time.TimePatterns
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/**
 * A profile's Stories tab (owner decision 2026-11-20), keyed by the chat (`hiltViewModel(key = …)`), read once [start]
 * names the chat: its albums with each one's story count (one request per album, as td_api.tl gives none with an
 * album), and the stories it keeps on its profile, [PAGE_SIZE] at a time as the grid nears its end
 * ([ProfileStoriesEvent.LoadMore]), each with its day in the zone and language of the moment. Neither stories nor
 * albums is [ProfileStoriesUiState.Empty]; a failed first page is an error to retry, and failed albums are left out.
 */
@HiltViewModel
class ProfileStoriesViewModel internal constructor(
    private val albums: StoryAlbumRepository,
    private val clock: WallClock,
    private val timePatterns: TimePatterns,
    private val locale: () -> Locale,
) : ViewModel() {
    @Inject
    constructor(
        albums: StoryAlbumRepository,
        clock: WallClock,
        timePatterns: TimePatterns,
    ) : this(albums, clock, timePatterns, Locale::getDefault)

    private val state = MutableStateFlow<ProfileStoriesUiState>(ProfileStoriesUiState.Loading)
    val uiState: StateFlow<ProfileStoriesUiState> = state.asStateFlow()

    private var chatId: Long? = null

    /** Reads chat [chatId]'s albums and first stories, once per chat. */
    fun start(chatId: Long) {
        if (this.chatId == chatId) return
        this.chatId = chatId
        load(chatId)
    }

    fun onEvent(event: ProfileStoriesEvent) {
        val chatId = chatId ?: return
        when (event) {
            ProfileStoriesEvent.LoadMore -> {
                loadMore(chatId)
            }

            ProfileStoriesEvent.Retry -> {
                if (state.value is ProfileStoriesUiState.Error) {
                    load(
                        chatId,
                    )
                } else {
                    loadMore(chatId, retry = true)
                }
            }
        }
    }

    private fun load(chatId: Long) {
        state.value = ProfileStoriesUiState.Loading
        viewModelScope.launch {
            coroutineScope {
                val tiles = async { albumTiles(chatId) }
                val page = albums.profileStories(chatId, fromStoryId = 0, limit = PAGE_SIZE)
                state.value =
                    when (page) {
                        is Outcome.Failure -> {
                            ProfileStoriesUiState.Error(page.error)
                        }

                        is Outcome.Success -> {
                            val shown = tiles.await()
                            if (page.value.stories.isEmpty() && shown.isEmpty()) {
                                ProfileStoriesUiState.Empty
                            } else {
                                ProfileStoriesUiState.Content(shown, mapped(page.value.stories), page.value.totalCount)
                            }
                        }
                    }
            }
        }
    }

    /** The chat's album covers with their counts; none when they fail, as the stories show without them. */
    private suspend fun albumTiles(chatId: Long): ImmutableList<StoryAlbumTile> {
        val found = (albums.albums(chatId) as? Outcome.Success)?.value ?: return persistentListOf()
        return coroutineScope {
            found
                .map { album -> async { album.toTile(count(chatId, album)) } }
                .awaitAll()
                .toImmutableList()
        }
    }

    private suspend fun count(
        chatId: Long,
        album: StoryAlbum,
    ): Int? = (albums.storyCount(chatId, album.id) as? Outcome.Success)?.value

    private fun loadMore(
        chatId: Long,
        retry: Boolean = false,
    ) {
        val content = state.value as? ProfileStoriesUiState.Content ?: return
        val waiting = content.moreError != null && !retry
        if (content.loadingMore || content.endReached || waiting) return
        state.value = content.copy(loadingMore = true, moreError = null)
        viewModelScope.launch {
            val after = content.stories.lastOrNull()?.id ?: 0
            val page = albums.profileStories(chatId, fromStoryId = after, limit = PAGE_SIZE)
            state.update { current ->
                val shown = current as? ProfileStoriesUiState.Content ?: return@update current
                when (page) {
                    is Outcome.Failure -> {
                        shown.copy(loadingMore = false, moreError = page.error)
                    }

                    is Outcome.Success -> {
                        val known = shown.stories.mapTo(HashSet()) { it.id }
                        val new = mapped(page.value.stories).filter { it.id !in known }
                        shown.copy(
                            // A page that brings nothing new ends the stories, so it is not asked for again.
                            totalCount = if (new.isEmpty()) shown.stories.size else page.value.totalCount,
                            stories = (shown.stories + new).toImmutableList(),
                            loadingMore = false,
                        )
                    }
                }
            }
        }
    }

    private fun mapped(stories: List<Story>): ImmutableList<AlbumStory> {
        val formatter = AlbumDateFormatter(clock.zone(), locale(), timePatterns)
        val now = clock.now()
        return stories.map { it.toAlbumStory(formatter.format(it.date, now)) }.toImmutableList()
    }

    private companion object {
        /** About four screens of a phone's grid. */
        const val PAGE_SIZE = 30
    }
}
