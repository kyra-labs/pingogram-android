package dev.kyralabs.pingogram.feature.chat.ui.saved

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.SavedMessagesTopic
import dev.kyralabs.pingogram.core.domain.model.SavedTopicKind
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.SavedMessagesTopicRepository
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.R
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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

/**
 * The tabs of Saved Messages (`docs/DESIGN_SYSTEM.md` §6 "Saved Messages tabs"): "All", then its topics in TDLib's
 * order (pinned first), one per chat the user saved messages from, "My Notes" and "Author hidden" among them
 * ([SavedMessagesTopicRepository]). Only while there are two topics or more: with one, "All" is that topic.
 *
 * - **Loading**: the first topics are asked for once the screen collects [uiState]; more as the tabs' row nears its
 *   end ([loadMore]), until TDLib has announced them all.
 * - **Names**: a chat's tab takes its title, as it changes ([ChatRepository.chat]).
 * - **Selection** ([select]) is kept in the saved state, so it survives process death; a topic that leaves the list
 *   (emptied) gives the selection back to "All".
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SavedTabsViewModel
    @Inject
    constructor(
        private val savedStateHandle: SavedStateHandle,
        private val savedTopics: SavedMessagesTopicRepository,
        private val chats: ChatRepository,
    ) : ViewModel() {
        private var moreToLoad = true
        private var loading = false

        private val tabs: Flow<ImmutableList<SavedTab>> =
            savedTopics.topics
                .flatMapLatest { topics -> if (topics.size < MIN_TOPICS) flowOf(NO_TABS) else tabsOf(topics) }
                .onStart { loadMore() }

        val uiState: StateFlow<SavedTabsUiState> =
            combine(tabs, savedStateHandle.getStateFlow<Long?>(KEY_SELECTED, null)) { tabs, selected ->
                SavedTabsUiState(tabs, selected?.takeIf { id -> tabs.any { it.topicId == id } })
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
                when (val loaded = savedTopics.loadMore()) {
                    is Outcome.Success -> moreToLoad = loaded.value

                    // Asked again the next time the row nears its end.
                    is Outcome.Failure -> Unit
                }
                loading = false
            }
        }

        private fun tabsOf(topics: List<SavedMessagesTopic>): Flow<ImmutableList<SavedTab>> {
            val names: List<Flow<UiText>> =
                topics.map { topic ->
                    when (val kind = topic.kind) {
                        SavedTopicKind.MyNotes -> flowOf(UiText.resource(R.string.saved_tab_my_notes))
                        SavedTopicKind.AuthorHidden -> flowOf(UiText.resource(R.string.saved_tab_author_hidden))
                        is SavedTopicKind.SavedFrom -> chats.chat(kind.chatId).map { it.titleText() }
                    }.distinctUntilChanged()
                }
            return combine(names) { titles ->
                (
                    listOf(SavedTab(null, UiText.resource(R.string.saved_tab_all))) +
                        topics.mapIndexed { index, topic -> SavedTab(topic.id, titles[index]) }
                ).toImmutableList()
            }
        }

        private companion object {
            const val KEY_SELECTED = "saved.selectedTopic"
            const val MIN_TOPICS = 2
            const val STOP_TIMEOUT_MILLIS = 5_000L
            val NO_TABS: ImmutableList<SavedTab> = persistentListOf()
        }
    }

/** A chat's title, as user content; "Deleted chat" for one TDLib does not know (any more). */
private fun Chat?.titleText(): UiText =
    this?.title?.takeIf { it.isNotBlank() }?.let(UiText::Plain) ?: UiText.resource(R.string.saved_tab_unknown_chat)

/** The tabs above Saved Messages, none while there are fewer than two topics; [selectedTopicId] null for "All". */
@Immutable
data class SavedTabsUiState(
    val tabs: ImmutableList<SavedTab> = persistentListOf(),
    val selectedTopicId: Long? = null,
)

/** One tab: topic [topicId] (null for "All") and its [title]. */
@Immutable
data class SavedTab(
    val topicId: Long?,
    val title: UiText,
)
