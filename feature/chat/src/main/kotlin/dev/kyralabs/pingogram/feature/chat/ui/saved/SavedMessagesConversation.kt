package dev.kyralabs.pingogram.feature.chat.ui.saved

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreOwner
import dev.kyralabs.pingogram.core.domain.model.MessageTopic

/**
 * Saved Messages with its tabs (`docs/DESIGN_SYSTEM.md` §6 "Saved Messages tabs"): the [conversation] of the tab
 * selected, "All" (the whole chat, at [messageId] if not null) or one of its topics, and the tabs in its top bar
 * ([LocalConversationTabs]) while there are two topics or more. A tab fades through to the next on the effects
 * springs, as the chat list's folders do. Each tab's conversation has a view model store of its own, cleared once
 * another tab shows, so a tab left keeps no history in memory; the tabs and the selection are [SavedTabsViewModel]'s.
 */
@Composable
internal fun SavedMessagesConversation(
    messageId: Long?,
    conversation: @Composable (topic: MessageTopic?, messageId: Long?) -> Unit,
    viewModel: SavedTabsViewModel = hiltViewModel(key = "saved-tabs"),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val tabs: (@Composable () -> Unit)? =
        remember(state, viewModel) {
            if (state.tabs.isEmpty()) null else ({ SavedTabs(state, viewModel::select, viewModel::loadMore) })
        }
    val motionScheme = MaterialTheme.motionScheme
    CompositionLocalProvider(LocalConversationTabs provides tabs) {
        AnimatedContent(
            targetState = state.selectedTopicId,
            transitionSpec = {
                fadeIn(motionScheme.defaultEffectsSpec()) togetherWith fadeOut(motionScheme.fastEffectsSpec())
            },
            label = "savedTabs",
        ) { selected ->
            CompositionLocalProvider(LocalViewModelStoreOwner provides rememberViewModelStoreOwner()) {
                conversation(selected?.let(MessageTopic::SavedMessages), messageId.takeIf { selected == null })
            }
        }
    }
}
