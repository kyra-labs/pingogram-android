package dev.kyralabs.pingogram.feature.media.storyalbum

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.Story
import dev.kyralabs.pingogram.core.domain.repository.StoryAlbumRepository
import dev.kyralabs.pingogram.core.navigation.StoryAlbumPickerRoute
import dev.kyralabs.pingogram.core.ui.time.TimePatterns
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/**
 * Picking stories for one of the user's own albums (owner decision 2026-11-20), as the route says: for a new album,
 * from the stories on the profile; to add, from those not in the album yet; to remove, from the album's. At most
 * [STORY_LIMIT] of them, read once the screen collects the state, each with its day. Confirming creates the album or
 * changes it ([StoryAlbumRepository]) and ends picking ([StoryAlbumPickerEffect.Done]); a refusal is a snackbar and
 * the picked stories stay. What is picked is in the saved state.
 */
@HiltViewModel
class StoryAlbumPickerViewModel internal constructor(
    private val route: StoryAlbumPickerRoute,
    private val albums: StoryAlbumRepository,
    private val clock: WallClock,
    private val timePatterns: TimePatterns,
    private val savedStateHandle: SavedStateHandle,
    private val locale: () -> Locale,
) : ViewModel() {
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        albums: StoryAlbumRepository,
        clock: WallClock,
        timePatterns: TimePatterns,
    ) : this(
        savedStateHandle.toRoute<StoryAlbumPickerRoute>(),
        albums,
        clock,
        timePatterns,
        savedStateHandle,
        Locale::getDefault,
    )

    private val purpose =
        when {
            route.albumId == null -> StoryAlbumPickPurpose.Create
            route.removing -> StoryAlbumPickPurpose.Remove
            else -> StoryAlbumPickPurpose.Add
        }

    private val state = MutableStateFlow<StoryAlbumPickerUiState>(StoryAlbumPickerUiState.Loading)
    private var started = false

    val uiState: StateFlow<StoryAlbumPickerUiState> =
        state
            .onStart { if (!started) load() }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                StoryAlbumPickerUiState.Loading,
            )

    private val effectChannel = Channel<StoryAlbumPickerEffect>(Channel.BUFFERED)
    val effects: Flow<StoryAlbumPickerEffect> = effectChannel.receiveAsFlow()

    fun onEvent(event: StoryAlbumPickerEvent) {
        when (event) {
            is StoryAlbumPickerEvent.Toggle -> toggle(event.storyId)
            StoryAlbumPickerEvent.Confirm -> confirm()
            StoryAlbumPickerEvent.Retry -> if (state.value is StoryAlbumPickerUiState.Error) load()
        }
    }

    private fun load() {
        started = true
        state.value = StoryAlbumPickerUiState.Loading
        viewModelScope.launch {
            state.value =
                when (val found = stories()) {
                    is Outcome.Failure -> {
                        StoryAlbumPickerUiState.Error(found.error)
                    }

                    is Outcome.Success -> {
                        val shown = mapped(found.value)
                        val picked = savedStateHandle.get<IntArray>(KEY_PICKED)?.toSet().orEmpty()
                        StoryAlbumPickerUiState.Content(
                            purpose = purpose,
                            stories = shown,
                            selected = picked.filterTo(HashSet()) { id -> shown.any { it.id == id } }.toImmutableSet(),
                        )
                    }
                }
        }
    }

    /** The stories to pick from, as [purpose] says. */
    private suspend fun stories(): Outcome<List<Story>> {
        val inAlbum =
            route.albumId?.let { albumId ->
                albums.stories(route.chatId, albumId, offset = 0, limit = STORY_LIMIT).map { it.stories }
            }
        return when {
            inAlbum == null -> {
                profileStories()
            }

            purpose == StoryAlbumPickPurpose.Remove || inAlbum is Outcome.Failure -> {
                inAlbum
            }

            else -> {
                val held = (inAlbum as Outcome.Success).value.mapTo(HashSet()) { it.id }
                profileStories().map { stories -> stories.filter { it.id !in held } }
            }
        }
    }

    private suspend fun profileStories(): Outcome<List<Story>> =
        albums.profileStories(route.chatId, fromStoryId = 0, limit = STORY_LIMIT).map { it.stories }

    private fun toggle(storyId: Int) {
        state.update { current ->
            val content = current as? StoryAlbumPickerUiState.Content ?: return@update current
            if (content.isSaving) return@update current
            val picked = if (storyId in content.selected) content.selected - storyId else content.selected + storyId
            savedStateHandle[KEY_PICKED] = picked.toIntArray()
            content.copy(selected = picked.toImmutableSet())
        }
    }

    private fun confirm() {
        val content = state.value as? StoryAlbumPickerUiState.Content ?: return
        if (!content.canConfirm) return
        state.value = content.copy(isSaving = true)
        // In the order shown, not the order tapped.
        val ids = content.stories.map { it.id }.filter { it in content.selected }
        viewModelScope.launch {
            val albumId = route.albumId
            val saved =
                when (purpose) {
                    StoryAlbumPickPurpose.Create -> {
                        albums.createAlbum(route.chatId, route.name.trim(), ids).map {}
                    }

                    StoryAlbumPickPurpose.Add -> {
                        albums.addStories(route.chatId, checkNotNull(albumId), ids).map {}
                    }

                    StoryAlbumPickPurpose.Remove -> {
                        albums
                            .removeStories(
                                route.chatId,
                                checkNotNull(albumId),
                                ids,
                            ).map {}
                    }
                }
            when (saved) {
                is Outcome.Success -> {
                    effectChannel.send(StoryAlbumPickerEffect.Done)
                }

                is Outcome.Failure -> {
                    state.update { (it as? StoryAlbumPickerUiState.Content)?.copy(isSaving = false) ?: it }
                    effectChannel.send(StoryAlbumPickerEffect.ShowError(saved.error))
                }
            }
        }
    }

    private fun mapped(stories: List<Story>) =
        AlbumDateFormatter(clock.zone(), locale(), timePatterns).let { formatter ->
            val now = clock.now()
            stories.map { it.toAlbumStory(formatter.format(it.date, now)) }.toImmutableList()
        }

    private companion object {
        /** As many as the stories viewer plays of an album: one request. */
        const val STORY_LIMIT = 100
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val KEY_PICKED = "picked"
    }
}
