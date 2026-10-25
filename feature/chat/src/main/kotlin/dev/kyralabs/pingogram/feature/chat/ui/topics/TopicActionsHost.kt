package dev.kyralabs.pingogram.feature.chat.ui.topics

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalResources
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * The topic actions of topic [topicId]'s conversation in forum [chatId] (`REQUIREMENTS.md` FR-C9): hoists its
 * [TopicActionsViewModel], keyed by the topic like the conversation's, and handles its effects: a deleted topic leaves
 * the conversation through [onDeleted], a refusal is a snackbar in [snackbarHostState]. The conversation puts
 * [TopicActionsSlot.button] in its top bar and shows the editor and the question over itself
 * ([TopicManagementDialogs] with [TopicActionsSlot.management]).
 */
@Composable
internal fun rememberTopicActions(
    chatId: Long,
    topicId: Int,
    snackbarHostState: SnackbarHostState,
    onDeleted: () -> Unit,
): TopicActionsSlot {
    val viewModel =
        hiltViewModel<TopicActionsViewModel, TopicActionsViewModel.Factory>(key = "topic-actions-$chatId-$topicId") {
            it.create(chatId, topicId)
        }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnDeleted by rememberUpdatedState(onDeleted)
    TopicEffects(viewModel.effects, snackbarHostState) { effect ->
        if (effect is TopicEffect.Deleted) currentOnDeleted()
    }
    return TopicActionsSlot(state, viewModel::onEvent)
}

/** What [rememberTopicActions] gives the conversation: the top bar's button, and the editor and the question. */
@Stable
internal class TopicActionsSlot(
    state: TopicActionsUiState,
    val onEvent: (TopicEvent) -> Unit,
) {
    /** "Manage topic", or null where the user may do nothing with the topic. */
    val button: (@Composable () -> Unit)? =
        state.menu?.let { menu -> { TopicActionButton(menu, onEvent) } }

    /** The editor and the question before deleting, while they show. */
    val management: TopicManagementUiState = state.management
}

/**
 * Handles managing topics' [effects]: a refusal is a snackbar in [snackbarHostState] (none for TDLib's silent errors);
 * the others go to [onEffect].
 */
@Composable
internal fun TopicEffects(
    effects: Flow<TopicEffect>,
    snackbarHostState: SnackbarHostState,
    onEffect: (TopicEffect) -> Unit,
) {
    val resources = LocalResources.current
    val showDetails = isDebuggable()
    val currentOnEffect by rememberUpdatedState(onEffect)
    LaunchedEffect(effects) {
        effects.collect { effect ->
            if (effect is TopicEffect.Failed) {
                launch { snackbarHostState.showError(errorMessage(resources, effect.error, showDetails), effect.error) }
            } else {
                currentOnEffect(effect)
            }
        }
    }
}

/** Shows [message] for [error], replacing the snackbar shown; nothing for TDLib's silent errors. */
internal suspend fun SnackbarHostState.showError(
    message: String,
    error: DomainError,
) {
    if (error == DomainError.Silent) return
    currentSnackbarData?.dismiss()
    showSnackbar(message)
}
