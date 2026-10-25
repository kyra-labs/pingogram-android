package dev.kyralabs.pingogram.feature.chat.ui.topics

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.feature.chat.R
import kotlinx.coroutines.launch

/**
 * The topic list of forum [chatId] (`REQUIREMENTS.md` FR-C9), where the conversation of a forum opens: hoists its
 * [ForumTopicsViewModel], keyed by the forum like the conversation's. A tapped topic opens through [onOpenTopic], and
 * so does a topic the user created; a deleted one and refusals are snackbars ([ForumTopicsEffect]). [onBack] leaves,
 * null shows no back button.
 */
@Composable
internal fun ForumTopicsRoute(
    chatId: Long,
    onBack: (() -> Unit)?,
    onOpenTopic: (topicId: Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ForumTopicsViewModel =
        hiltViewModel<ForumTopicsViewModel, ForumTopicsViewModel.Factory>(key = "topics-$chatId") { it.create(chatId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val showDetails = isDebuggable()
    val currentOnOpenTopic by rememberUpdatedState(onOpenTopic)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ForumTopicsEffect.OpenTopic -> {
                    currentOnOpenTopic(effect.topicId)
                }

                ForumTopicsEffect.TopicDeleted -> {
                    launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(resources.getString(R.string.topic_deleted))
                    }
                }

                is ForumTopicsEffect.Failed -> {
                    launch {
                        snackbarHostState.showError(errorMessage(resources, effect.error, showDetails), effect.error)
                    }
                }
            }
        }
    }
    ForumTopicsScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onOpenTopic = onOpenTopic,
        onBack = onBack,
        modifier = modifier,
        onTopicEvent = viewModel::onTopicEvent,
        snackbarHostState = snackbarHostState,
    )
}
