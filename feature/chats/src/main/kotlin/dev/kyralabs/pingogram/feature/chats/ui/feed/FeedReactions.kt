package dev.kyralabs.pingogram.feature.chats.ui.feed

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.ChannelFeedRepository
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The reactions bar of a lifted feed card (`docs/DESIGN_SYSTEM.md` §6 "Channel feed", frame 6): the reactions the post
 * takes ([show], into [choice]), and a tap that adds one or takes the user's away ([react]), after which the post is
 * read again for its counts, as the channel is not open to send them (owner decision 2026-11-19).
 */
internal class FeedReactions(
    private val actions: FeedCardActions,
    private val repository: ChannelFeedRepository,
    private val scope: CoroutineScope,
    private val onEffect: (FeedEffect) -> Unit,
) {
    private val mutableChoice = MutableStateFlow<FeedReactionChoice?>(null)

    /** The reactions of the post whose menu opened last; null until they are read. */
    val choice: StateFlow<FeedReactionChoice?> = mutableChoice.asStateFlow()

    /** Reads the emoji reactions [post] takes, for its bar. */
    fun show(post: FeedPost) {
        scope.launch {
            val emojis = (actions.reactions(post) as? Outcome.Success)?.value ?: return@launch
            mutableChoice.value = FeedReactionChoice(post.key, emojis.toImmutableList())
        }
    }

    /** Adds reaction [emoji] to [post], or takes it away if it is the user's, then reads the post again. */
    fun react(
        post: FeedPost,
        emoji: String,
    ) {
        val chose = post.reactions.any { it.emoji == emoji && it.isChosen }
        scope.launch {
            when (val outcome = actions.react(post, emoji, chose)) {
                is Outcome.Success -> repository.refresh(mapOf(post.chatId to post.messageIds))
                is Outcome.Failure -> onEffect(FeedEffect.ShowError(outcome.error))
            }
        }
    }
}
