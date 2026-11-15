package dev.kyralabs.pingogram.feature.chat.ui.saved

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.domain.repository.BookmarkRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** How many messages are bookmarked on this phone, for Saved Messages' Bookmarks card; 0 until read. */
@HiltViewModel
internal class SavedBookmarksViewModel
    @Inject
    constructor(
        bookmarks: BookmarkRepository,
    ) : ViewModel() {
        val count: StateFlow<Int> =
            bookmarks.bookmarks
                .map { it.size }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), 0)

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
