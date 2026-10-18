package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.ImmutableList

/** A message that a search across chats found (`REQUIREMENTS.md` FR-L6), with the [chat] it is in, for its row. */
data class FoundMessage(
    val message: Message,
    val chat: Chat,
)

/** A page of messages found by a search across chats, from `SearchRepository.searchMessages`. */
data class FoundMessages(
    /** Newest first, as TDLib returns them (by date, then chat and message id); never the same message twice. */
    val messages: ImmutableList<FoundMessage>,
    /** What to pass to the next call for the page after this one; null once there is nothing more to find. */
    val nextOffset: String?,
    /** About how many messages match in all, as TDLib counts them; null while it does not know. */
    val totalCount: Int?,
)
