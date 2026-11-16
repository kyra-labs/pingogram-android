package dev.kyralabs.pingogram.feature.chat.ui.topics

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
import dev.kyralabs.pingogram.feature.chat.ui.saved.LocalConversationTabs
import dev.kyralabs.pingogram.feature.chat.ui.saved.SavedTabs

/**
 * A forum that shows its topics as tabs (`docs/DESIGN_SYSTEM.md` §6 "Forum topic tabs"): the [conversation] of the
 * tab selected, "All" (the whole chat, at [messageId] if not null) or one topic, with the tabs in its top bar
 * ([LocalConversationTabs]), as Saved Messages shows its own ([SavedTabs]). A tab fades through to the next on the
 * effects springs; each tab's conversation has a view model store of its own, cleared once another tab shows. It opens
 * at [initialTopicId]'s tab where the forum was opened at a message of a topic.
 */
@Composable
internal fun ForumTabsConversation(
    chatId: Long,
    initialTopicId: Int?,
    messageId: Long?,
    conversation: @Composable (topic: MessageTopic?, messageId: Long?) -> Unit,
    viewModel: ForumTabsViewModel =
        hiltViewModel<ForumTabsViewModel, ForumTabsViewModel.Factory>(key = "forum-tabs-$chatId") {
            it.create(chatId, initialTopicId)
        },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val tabs: (@Composable () -> Unit)? =
        remember(state, viewModel) {
            if (state.tabs.size < 2) null else ({ SavedTabs(state, viewModel::select, viewModel::loadMore) })
        }
    val motionScheme = MaterialTheme.motionScheme
    CompositionLocalProvider(LocalConversationTabs provides tabs) {
        AnimatedContent(
            targetState = state.selectedTopicId,
            transitionSpec = {
                fadeIn(motionScheme.defaultEffectsSpec()) togetherWith fadeOut(motionScheme.fastEffectsSpec())
            },
            label = "forumTabs",
        ) { selected ->
            CompositionLocalProvider(LocalViewModelStoreOwner provides rememberViewModelStoreOwner()) {
                val topic = selected?.let { MessageTopic.Forum(it.toInt()) }
                // The message the forum opened at shows in its topic's tab, or in "All".
                conversation(topic, messageId.takeIf { selected == null || selected == initialTopicId?.toLong() })
            }
        }
    }
}
