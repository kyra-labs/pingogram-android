package dev.kyralabs.pingogram.feature.chats.ui.feed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * The Channel feed (`REQUIREMENTS.md` FR-X5), pushed above the Chats tab from its Feed tab: hoists its
 * [FeedViewModel]; a card opens its channel at the post through [onOpenPost], and [onBack] returns to the chats.
 * TDLib keeps the posts current, so coming back from a channel reads nothing again.
 */
@Composable
internal fun FeedRoute(
    onBack: () -> Unit,
    onOpenPost: (chatId: Long, messageId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FeedViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnOpenPost by rememberUpdatedState(onOpenPost)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is FeedEffect.OpenPost -> currentOnOpenPost(effect.chatId, effect.messageId)
            }
        }
    }
    FeedScreen(state = state, onEvent = viewModel::onEvent, onBack = onBack, modifier = modifier)
}
