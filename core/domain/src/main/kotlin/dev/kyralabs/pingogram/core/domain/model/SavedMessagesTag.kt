package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.ImmutableList

/**
 * A tag of Saved Messages (td_api.tl `savedMessagesTag`): a reaction the user put on saved messages, which Telegram
 * Premium subscribers use to sort them, with an optional [label] ("Ideas") and the [count] of messages it tags.
 */
data class SavedMessagesTag(
    val reaction: ReactionType,
    val label: String,
    val count: Int,
)

/**
 * A page of Saved Messages a tag filters (td_api.tl `foundChatMessages`): [messages], newest first, how many the tag
 * holds in all ([totalCount]), and where the next page starts ([nextFromMessageId], 0 once there is none).
 */
data class TaggedMessages(
    val messages: ImmutableList<Message>,
    val totalCount: Int,
    val nextFromMessageId: Long,
)
