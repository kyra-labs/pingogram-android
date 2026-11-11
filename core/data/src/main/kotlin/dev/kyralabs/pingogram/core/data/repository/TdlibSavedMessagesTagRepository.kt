package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.getOrNull
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.data.mapper.toMessage
import dev.kyralabs.pingogram.core.data.mapper.toReactionType
import dev.kyralabs.pingogram.core.data.mapper.toTdReactionType
import dev.kyralabs.pingogram.core.domain.model.ReactionType
import dev.kyralabs.pingogram.core.domain.model.SavedMessagesTag
import dev.kyralabs.pingogram.core.domain.model.TaggedMessages
import dev.kyralabs.pingogram.core.domain.repository.SavedMessagesTagRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.drinkless.tdlib.TdApi
import javax.inject.Inject

/**
 * [SavedMessagesTagRepository] on TDLib: `getSavedMessagesTags` of all of Saved Messages (topic 0), read again on its
 * `updateSavedMessagesTags`; `searchSavedMessages` by tag, without words; `setSavedMessagesTagLabel`. No cache: TDLib
 * keeps the tags and announces their changes.
 */
@AccountScoped
class TdlibSavedMessagesTagRepository
    @Inject
    constructor(
        private val client: TdlibClient,
    ) : SavedMessagesTagRepository {
        override fun tags(): Flow<List<SavedMessagesTag>> =
            client
                .readOn({ it is TdApi.UpdateSavedMessagesTags && it.savedMessagesTopicId == ALL_TOPICS }) {
                    client.request(TdApi.GetSavedMessagesTags(ALL_TOPICS)).map { it.toTags() }
                }.map { it.getOrNull().orEmpty() }

        override suspend fun taggedMessages(
            tag: ReactionType,
            fromMessageId: Long,
            limit: Int,
        ): Outcome<TaggedMessages> {
            val reaction = tag.toTdReactionType() ?: return Outcome.Failure(DomainError.NotFound)
            return client
                .request(TdApi.SearchSavedMessages(ALL_TOPICS, reaction, "", fromMessageId, 0, limit))
                .map { found ->
                    TaggedMessages(
                        messages =
                            found.messages
                                .orEmpty()
                                .map { it.toMessage() }
                                .toImmutableList(),
                        totalCount = found.totalCount,
                        nextFromMessageId = found.nextFromMessageId,
                    )
                }
        }

        override suspend fun setLabel(
            tag: ReactionType,
            label: String,
        ): Outcome<Unit> {
            val reaction = tag.toTdReactionType() ?: return Outcome.Failure(DomainError.NotFound)
            return client.request(TdApi.SetSavedMessagesTagLabel(reaction, label.trim())).map { }
        }

        private companion object {
            /** td_api.tl `saved_messages_topic_id` 0: all of Saved Messages. */
            const val ALL_TOPICS = 0L
        }
    }

/** The tags TDLib can describe, in its order; a reaction of a kind this version does not know is left out. */
private fun TdApi.SavedMessagesTags.toTags(): List<SavedMessagesTag> =
    tags.orEmpty().mapNotNull { tag ->
        tag.tag?.toReactionType()?.let { SavedMessagesTag(it, tag.label.orEmpty(), tag.count) }
    }
