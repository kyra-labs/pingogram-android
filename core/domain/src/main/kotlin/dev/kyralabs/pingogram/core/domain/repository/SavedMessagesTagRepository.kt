package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ReactionType
import dev.kyralabs.pingogram.core.domain.model.SavedMessagesTag
import dev.kyralabs.pingogram.core.domain.model.TaggedMessages
import kotlinx.coroutines.flow.Flow

/**
 * The tags of Saved Messages (the owner's board 8, td_api.tl `savedMessagesTag`): the reactions the user put on saved
 * messages, with their labels and counts, the messages a tag filters, and renaming a tag's label. Tagging and labels
 * are Telegram Premium's; Telegram refuses what the account may not do (`DomainError.PremiumRequired`), never worked
 * around.
 */
interface SavedMessagesTagRepository {
    /**
     * The tags of all of Saved Messages, the most used first as TDLib orders them (`getSavedMessagesTags`), then every
     * change (`updateSavedMessagesTags`) while collected; empty where the account has none, or they cannot be read.
     */
    fun tags(): Flow<List<SavedMessagesTag>>

    /**
     * A page of [limit] saved messages tagged [tag], from [fromMessageId] (0 for the newest) back
     * (`searchSavedMessages`).
     */
    suspend fun taggedMessages(
        tag: ReactionType,
        fromMessageId: Long,
        limit: Int,
    ): Outcome<TaggedMessages>

    /** Sets [tag]'s label (`setSavedMessagesTagLabel`, at most [LABEL_LENGTH_MAX] characters); empty removes it. */
    suspend fun setLabel(
        tag: ReactionType,
        label: String,
    ): Outcome<Unit>

    companion object {
        /** td_api.tl `setSavedMessagesTagLabel.label`: "0-12 characters". */
        const val LABEL_LENGTH_MAX = 12
    }
}
