package dev.kyralabs.pingogram.feature.chats.ui.drafts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * The Drafts page (`REQUIREMENTS.md` FR-X16), pushed above the Chats tab: hoists its [DraftsViewModel]; a row opens
 * its chat through [onOpenChat], and [onBack] leaves. The rows follow the chats, so a draft sent or cleared in a chat
 * opened from here is gone on the way back.
 */
@Composable
internal fun DraftsRoute(
    onBack: () -> Unit,
    onOpenChat: (chatId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DraftsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnOpenChat by rememberUpdatedState(onOpenChat)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is DraftsEffect.OpenChat -> currentOnOpenChat(effect.chatId)
            }
        }
    }
    DraftsScreen(state = state, onEvent = viewModel::onEvent, onBack = onBack, modifier = modifier)
}
