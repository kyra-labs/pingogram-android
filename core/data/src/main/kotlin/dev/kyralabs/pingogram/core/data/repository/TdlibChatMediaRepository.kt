package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.data.mapper.toMessage
import dev.kyralabs.pingogram.core.domain.model.ChatMediaFilter
import dev.kyralabs.pingogram.core.domain.model.ChatMediaPage
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.repository.ChatMediaRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import org.drinkless.tdlib.TdApi
import javax.inject.Inject

/**
 * [ChatMediaRepository] on TDLib's `searchChatMessages` with `searchMessagesFilterPhotoAndVideo` or
 * `searchMessagesFilterAnimation`. TDLib answers from its message database where that holds the chat's media (always
 * in secret chats, which the server cannot search), else from the server; either way it leaves self-destructing media
 * out. Nothing is cached, so it is no `TdlibUpdateCache`.
 *
 * - **The opened message** ([opened]) is `getMessage` alone, which TDLib answers from memory for a message on screen,
 *   so the viewer opens on it at once; its filter comes from the message itself, and a deleted message fails with
 *   TDLib's 404, `DomainError.NotFound`. The search goes to the server where TDLib's database does not hold the chat's
 *   media, which can take a while in a large chat.
 * - **Pages** are [PAGE_SIZE] messages: [older] starts at the message (offset 0, which includes it), [newer] ends at
 *   it. The message itself is left out of both.
 * - **Ends**: TDLib may answer with fewer messages than asked although there are more, so only a side with no message
 *   at all counts as reached, as in [TdlibMessageRepository].
 * - **Saving** ([canBeSaved]) is `getMessageProperties`' `can_be_saved`, which TDLib works out locally, as it is when
 *   asked: the chat's protection may change while the message is loaded.
 */
@AccountScoped
class TdlibChatMediaRepository
    @Inject
    constructor(
        private val client: TdlibClient,
    ) : ChatMediaRepository {
        override suspend fun opened(
            chatId: Long,
            messageId: Long,
        ): Outcome<ChatMediaPage> =
            when (val found = client.request(TdApi.GetMessage(chatId, messageId))) {
                is Outcome.Success -> pageOf(found.value.toMessage())
                is Outcome.Failure -> found
            }

        /** The page of [message] alone, of the filter of its kind. */
        private fun pageOf(message: Message): Outcome<ChatMediaPage> {
            val filter = message.content.mediaFilter ?: return Outcome.Failure(DomainError.NotFound)
            return Outcome.Success(
                ChatMediaPage(
                    messages = persistentListOf(message),
                    filter = filter,
                    reachedOldest = false,
                    reachedNewest = false,
                    totalCount = null,
                ),
            )
        }

        override suspend fun older(
            chatId: Long,
            filter: ChatMediaFilter,
            messageId: Long,
        ): Outcome<ChatMediaPage> =
            search(chatId, filter, messageId, offset = 0, limit = PAGE_SIZE).map { found ->
                ChatMediaPage(
                    messages = found.media(filter).filter { it.id < messageId }.toImmutableList(),
                    filter = filter,
                    reachedOldest = found.ids().none { it < messageId },
                    reachedNewest = false,
                    totalCount = found.totalCount.takeIf { it >= 0 },
                )
            }

        override suspend fun newer(
            chatId: Long,
            filter: ChatMediaFilter,
            messageId: Long,
        ): Outcome<ChatMediaPage> =
            // The limit must exceed -offset: the page's newest messages and the message itself.
            search(chatId, filter, messageId, offset = -PAGE_SIZE, limit = PAGE_SIZE + 1).map { found ->
                ChatMediaPage(
                    messages = found.media(filter).filter { it.id > messageId }.toImmutableList(),
                    filter = filter,
                    reachedOldest = false,
                    reachedNewest = found.ids().none { it > messageId },
                    totalCount = found.totalCount.takeIf { it >= 0 },
                )
            }

        override suspend fun position(
            chatId: Long,
            filter: ChatMediaFilter,
            messageId: Long,
        ): Outcome<Int> =
            client.request(TdApi.GetChatMessagePosition(chatId, null, filter.tdlibFilter(), messageId)).map { it.count }

        override suspend fun canBeSaved(
            chatId: Long,
            messageId: Long,
        ): Outcome<Boolean> = client.request(TdApi.GetMessageProperties(chatId, messageId)).map { it.canBeSaved }

        override fun deletedMessages(chatId: Long): Flow<Set<Long>> =
            client.updates
                .filterIsInstance<TdApi.UpdateDeleteMessages>()
                // Not permanent: only unloaded from TDLib's memory (fromCache), or out of reach for now.
                .filter { it.chatId == chatId && it.isPermanent }
                .map { it.messageIds.toSet() }

        /** Searches all of the chat (no topic, sender or query) from [fromMessageId] on, newest first. */
        private suspend fun search(
            chatId: Long,
            filter: ChatMediaFilter,
            fromMessageId: Long,
            offset: Int,
            limit: Int,
        ): Outcome<TdApi.FoundChatMessages> =
            client.request(
                TdApi.SearchChatMessages(chatId, null, "", null, fromMessageId, offset, limit, filter.tdlibFilter()),
            )
    }

/** Messages per page; TDLib allows up to 100. */
private const val PAGE_SIZE = 40

private fun ChatMediaFilter.tdlibFilter(): TdApi.SearchMessagesFilter =
    when (this) {
        ChatMediaFilter.PhotosAndVideos -> TdApi.SearchMessagesFilterPhotoAndVideo()
        ChatMediaFilter.Gifs -> TdApi.SearchMessagesFilterAnimation()
    }

/** The filter whose media this content is; null for other content, and for self-destructing media. */
private val MessageContent.mediaFilter: ChatMediaFilter?
    get() =
        when (this) {
            is MessageContent.Photo -> ChatMediaFilter.PhotosAndVideos.takeUnless { isSecret }
            is MessageContent.Video -> ChatMediaFilter.PhotosAndVideos.takeUnless { isSecret }
            is MessageContent.Animation -> ChatMediaFilter.Gifs.takeUnless { isSecret }
            else -> null
        }

/** The ids TDLib found; td_api.tl allows null entries in the list. */
private fun TdApi.FoundChatMessages.ids(): List<Long> = messages.filterNotNull().map { it.id }

/** The messages found that are media of [filter], newest first; anything else TDLib might mix in is left out. */
private fun TdApi.FoundChatMessages.media(filter: ChatMediaFilter): List<Message> =
    messages
        .filterNotNull()
        .map { it.toMessage() }
        .filter { it.content.mediaFilter == filter }
        .sortedByDescending { it.id }
