package dev.kyralabs.pingogram.feature.chats.ui.mentions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * The Mentions & replies page (`REQUIREMENTS.md` FR-X1), pushed above the Chats tab: hoists its [MentionsViewModel];
 * a row opens its chat at the message through [onOpenMessage], and [onBack] leaves. Each time the page starts again
 * after its first start (back from a chat), it reads the messages again ([MentionsEvent.Refresh]).
 */
@Composable
internal fun MentionsRoute(
    onBack: () -> Unit,
    onOpenMessage: (chatId: Long, messageId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MentionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnOpenMessage by rememberUpdatedState(onOpenMessage)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is MentionsEffect.OpenMessage -> currentOnOpenMessage(effect.chatId, effect.messageId)
            }
        }
    }
    // The first start reads them already, as the state is first collected.
    val starts = remember { intArrayOf(0) }
    LifecycleStartEffect(viewModel) {
        if (starts[0]++ > 0) viewModel.onEvent(MentionsEvent.Refresh)
        onStopOrDispose {}
    }
    MentionsScreen(state = state, onEvent = viewModel::onEvent, onBack = onBack, modifier = modifier)
}
