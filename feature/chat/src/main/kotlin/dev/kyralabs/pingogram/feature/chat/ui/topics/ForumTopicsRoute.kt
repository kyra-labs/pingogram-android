package dev.kyralabs.pingogram.feature.chat.ui.topics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * The topic list of forum [chatId] (`REQUIREMENTS.md` FR-C9), where the conversation of a forum opens: hoists its
 * [ForumTopicsViewModel], keyed by the forum like the conversation's. A tapped topic opens through [onOpenTopic];
 * [onBack] leaves, null shows no back button.
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
    ForumTopicsScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onOpenTopic = onOpenTopic,
        onBack = onBack,
        modifier = modifier,
    )
}
