package dev.kyralabs.pingogram.feature.media.storyalbum

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.StoryAlbumRepository
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A chat's story albums for its profile (board 11a), keyed by the chat (`hiltViewModel(key = …)`), read once
 * [start] names the chat. Nothing shows until they are there; a chat without albums, or whose albums fail to load,
 * shows nothing, as the profile works without them.
 */
@HiltViewModel
class StoryAlbumsViewModel
    @Inject
    constructor(
        private val albums: StoryAlbumRepository,
    ) : ViewModel() {
        private val state = MutableStateFlow<StoryAlbumsUiState>(StoryAlbumsUiState.Hidden)
        val uiState: StateFlow<StoryAlbumsUiState> = state.asStateFlow()

        private var chatId: Long? = null

        /** Reads chat [chatId]'s albums, once per chat. */
        fun start(chatId: Long) {
            if (this.chatId == chatId) return
            this.chatId = chatId
            viewModelScope.launch {
                val found = albums.albums(chatId)
                state.value =
                    when {
                        found !is Outcome.Success || found.value.isEmpty() -> StoryAlbumsUiState.Hidden
                        else -> StoryAlbumsUiState.Content(found.value.map { it.toTile() }.toImmutableList())
                    }
            }
        }
    }
