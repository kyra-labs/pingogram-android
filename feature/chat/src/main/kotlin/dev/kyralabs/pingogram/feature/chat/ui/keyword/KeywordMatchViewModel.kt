package dev.kyralabs.pingogram.feature.chat.ui.keyword

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.domain.model.KeywordHit
import dev.kyralabs.pingogram.core.domain.repository.KeywordHitRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn

/**
 * The conversation's keyword match bar (board 30): where a keyword alert opened chat [chatId] at message [openedAt],
 * "Keyword: launch · 1 of 4" steps through the chat's matches of the last week, the newest first, until closed. Each
 * step asks the conversation to show its message ([showMessage]). Nothing for a chat opened any other way, nor once
 * the matches leave the phone (read, or a week old). The match shown and whether the bar was closed are in
 * [savedStateHandle].
 */
@HiltViewModel(assistedFactory = KeywordMatchViewModel.Factory::class)
class KeywordMatchViewModel
    @AssistedInject
    constructor(
        @Assisted private val chatId: Long,
        @Assisted private val openedAt: Long?,
        private val savedStateHandle: SavedStateHandle,
        hits: KeywordHitRepository,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(
                chatId: Long,
                openedAt: Long?,
            ): KeywordMatchViewModel
        }

        /** The chat's matches, the newest first. */
        private val matches: StateFlow<List<KeywordHit>> =
            hits.recent
                .map { all -> all.filter { it.chatId == chatId }.sortedByDescending { it.messageId } }
                .distinctUntilChanged()
                .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

        /** The bar; null while there is none. */
        val bar: StateFlow<KeywordMatchBar?> =
            combine(
                matches,
                savedStateHandle.getStateFlow<Long?>(KEY_SHOWN, null),
                savedStateHandle.getStateFlow(KEY_CLOSED, false),
            ) { found, shown, closed ->
                val opened = found.indexOfFirst { it.messageId == openedAt }
                if (closed || openedAt == null || opened < 0) return@combine null
                val index = found.indexOfFirst { it.messageId == shown }.takeIf { it >= 0 } ?: opened
                KeywordMatchBar(
                    word = found[index].word,
                    messageId = found[index].messageId,
                    position = index + 1,
                    count = found.size,
                )
            }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

        private val showChannel = Channel<Long>(Channel.BUFFERED)

        /** The message the conversation is to show, as a tapped reply quote's. */
        val showMessage: Flow<Long> = showChannel.receiveAsFlow()

        fun onEvent(event: KeywordMatchEvent) {
            when (event) {
                KeywordMatchEvent.Older -> step(1)
                KeywordMatchEvent.Newer -> step(-1)
                KeywordMatchEvent.Close -> savedStateHandle[KEY_CLOSED] = true
            }
        }

        /** Shows the match [by] places older (newer where negative) than the one shown, read now, not from [bar]. */
        private fun step(by: Int) {
            val found = matches.value.takeIf { bar.value != null }.orEmpty()
            val shown = savedStateHandle.get<Long>(KEY_SHOWN) ?: openedAt
            val index = found.indexOfFirst { it.messageId == shown }
            val hit = found.getOrNull(index + by)?.takeIf { index >= 0 } ?: return
            savedStateHandle[KEY_SHOWN] = hit.messageId
            showChannel.trySend(hit.messageId)
        }

        private companion object {
            const val KEY_SHOWN = "keyword_match_shown"
            const val KEY_CLOSED = "keyword_match_closed"
        }
    }

/** What the match bar shows: [word], the match [position] (from 1, the newest) of [count], its [messageId]. */
@Immutable
data class KeywordMatchBar(
    val word: String,
    val messageId: Long,
    val position: Int,
    val count: Int,
) {
    val hasOlder: Boolean get() = position < count
    val hasNewer: Boolean get() = position > 1

    override fun toString() = "KeywordMatchBar(position=$position, count=$count)"
}

/** The match bar's buttons. */
sealed interface KeywordMatchEvent {
    data object Older : KeywordMatchEvent

    data object Newer : KeywordMatchEvent

    data object Close : KeywordMatchEvent
}
