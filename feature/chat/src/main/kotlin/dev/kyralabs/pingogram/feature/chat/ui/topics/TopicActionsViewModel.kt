package dev.kyralabs.pingogram.feature.chat.ui.topics

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.Default
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ForumTopic
import dev.kyralabs.pingogram.core.domain.repository.ForumTopicRepository
import dev.kyralabs.pingogram.core.domain.repository.GroupInfoRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/**
 * What the top bar of topic [topicId]'s conversation in forum [chatId] offers to manage it (`REQUIREMENTS.md` FR-C9,
 * `docs/DESIGN_SYSTEM.md` §6 "Forum topics"), as [uiState]: the topic's menu (rename, close or reopen, pin or unpin,
 * delete, hide or show General), only what the user's rights in the forum allow ([ForumRights]), and the editor and
 * the question before deleting while they show ([TopicManagement]). Apart from the conversation's own view model, as
 * the list's long press offers the same; keyed by the topic like it. A deleted topic leaves the conversation, a
 * refusal is a snackbar ([effects]).
 *
 * Mapped on [dispatcher]; it runs while the screen collects, and 5 s after, so a rotation does not restart it.
 */
@HiltViewModel(assistedFactory = TopicActionsViewModel.Factory::class)
class TopicActionsViewModel
    @AssistedInject
    constructor(
        @Assisted chatId: Long,
        @Assisted topicId: Int,
        repository: ForumTopicRepository,
        groupInfoRepository: GroupInfoRepository,
        savedStateHandle: SavedStateHandle,
        @Dispatcher(Default) dispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        /** Creates the view model of the actions of topic `topicId` in forum `chatId`. */
        @AssistedFactory
        interface Factory {
            fun create(
                chatId: Long,
                topicId: Int,
            ): TopicActionsViewModel
        }

        /** The topic as TDLib last reported it, for the menu's actions. */
        @Volatile
        private var latestTopic: ForumTopic? = null

        private val management =
            TopicManagement(
                chatId,
                repository,
                viewModelScope,
                savedStateHandle,
            ) { id -> latestTopic?.takeIf { it.id == id } }

        private val rights =
            groupInfoRepository
                .groupInfo(chatId)
                .map { (it as? Outcome.Success)?.value?.forumRights() ?: ForumRights.None }
                .onStart { emit(ForumRights.None) }

        val uiState: StateFlow<TopicActionsUiState> =
            combine(repository.topic(chatId, topicId), rights, management.state) { topic, rights, management ->
                latestTopic = topic
                TopicActionsUiState(topic?.menuFor(rights), management)
            }.flowOn(dispatcher)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), TopicActionsUiState())

        /** A deleted topic, which the conversation leaves, and refusals, each once. */
        val effects: Flow<TopicEffect> = management.effects

        /** What the user does in the topic's menu, the editor and the question. */
        fun onEvent(event: TopicEvent) = management.onEvent(event)

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

/**
 * The top bar's topic actions: the topic's [menu] (null while the topic is on its way, or the user may do nothing with
 * it: then there is no button), and the editor and the question while they show ([management]).
 */
@Immutable
data class TopicActionsUiState(
    val menu: TopicMenuState? = null,
    val management: TopicManagementUiState = TopicManagementUiState(),
)
