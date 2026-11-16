package dev.kyralabs.pingogram.feature.chat.ui.topics

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ForumTopic
import dev.kyralabs.pingogram.core.domain.repository.ForumTopicRepository
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.saved.SavedTab
import dev.kyralabs.pingogram.feature.chat.ui.saved.SavedTabsUiState
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The tabs of a forum that shows its topics as tabs (td_api.tl `supergroup.has_forum_tabs`), in the shape of Saved
 * Messages' ([SavedTabsUiState]): "All", the whole chat, then the topics, General first, the pinned ones next, then
 * the others by their newest message (TDLib's `order`); a General topic hidden from members is left out.
 *
 * - **Loading**: the first topics are asked for once the screen collects [uiState] ([ForumTopicRepository.loadMore]);
 *   more as the tabs' row nears its end ([loadMore]), until every topic is loaded.
 * - **Selection** ([select]) is kept in the saved state, so it survives process death; it starts at [initialTopicId]
 *   where the forum was opened at a message of a topic, and a topic that leaves the list gives it back to "All".
 */
@HiltViewModel(assistedFactory = ForumTabsViewModel.Factory::class)
class ForumTabsViewModel
    @AssistedInject
    constructor(
        @Assisted private val chatId: Long,
        @Assisted initialTopicId: Int?,
        private val savedStateHandle: SavedStateHandle,
        private val topics: ForumTopicRepository,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(
                chatId: Long,
                initialTopicId: Int?,
            ): ForumTabsViewModel
        }

        private var moreToLoad = true
        private var loading = false

        init {
            if (!savedStateHandle.contains(KEY_SELECTED)) savedStateHandle[KEY_SELECTED] = initialTopicId?.toLong()
        }

        val uiState: StateFlow<SavedTabsUiState> =
            combine(
                topics.topics(chatId).onStart { loadMore() },
                savedStateHandle.getStateFlow<Long?>(KEY_SELECTED, null),
            ) { list, selected ->
                moreToLoad = !list.reachedEnd
                val tabs =
                    listOf(SavedTab(null, UiText.resource(R.string.forum_tab_all))) +
                        list.topics.ordered().map { SavedTab(it.id.toLong(), UiText.Plain(it.name)) }
                SavedTabsUiState(tabs.toImmutableList(), selected?.takeIf { id -> tabs.any { it.topicId == id } })
            }.distinctUntilChanged()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SavedTabsUiState())

        /** Shows topic [topicId]; null for "All". */
        fun select(topicId: Long?) {
            savedStateHandle[KEY_SELECTED] = topicId
        }

        /** Asks TDLib for more topics, one request at a time, until all are loaded. */
        fun loadMore() {
            if (!moreToLoad || loading) return
            loading = true
            viewModelScope.launch {
                when (topics.loadMore(chatId)) {
                    is Outcome.Success -> Unit

                    // Asked again the next time the row nears its end.
                    is Outcome.Failure -> Unit
                }
                loading = false
            }
        }

        private companion object {
            const val KEY_SELECTED = "forumTabs.selected"
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

/** General first, then the pinned topics, then the others, newest first; a hidden General left out. */
private fun List<ForumTopic>.ordered(): List<ForumTopic> =
    filterNot { it.isGeneral && it.isHidden }
        .sortedWith(
            compareByDescending<ForumTopic> {
                it.isGeneral
            }.thenByDescending { it.isPinned }.thenByDescending { it.order },
        )
