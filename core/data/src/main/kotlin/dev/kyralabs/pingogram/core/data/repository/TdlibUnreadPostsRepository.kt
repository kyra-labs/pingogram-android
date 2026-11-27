package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.data.mapper.toMessage
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.repository.UnreadPostsRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import org.drinkless.tdlib.TdApi
import javax.inject.Inject

/**
 * [UnreadPostsRepository] on TDLib's `getChatHistory`, from the message to read after with an offset of
 * -([PAGE] - 1), which returns the newer messages first; each page starts at the newest of the one before, until a
 * page has nothing newer or [limit][postsAfter] is reached. `getChatHistory` neither opens the chat nor views its
 * messages, so nothing is marked read. A page that fails after others ends the reading with what was read.
 */
@AccountScoped
class TdlibUnreadPostsRepository
    @Inject
    constructor(
        private val client: TdlibClient,
    ) : UnreadPostsRepository {
        override suspend fun postsAfter(
            chatId: Long,
            afterMessageId: Long,
            limit: Int,
        ): Outcome<List<Message>> {
            val posts = mutableListOf<Message>()
            var failure: Outcome.Failure? = null
            var from = afterMessageId
            while (posts.size < limit && failure == null) {
                when (val page = newerThan(chatId, from)) {
                    is Outcome.Failure -> {
                        failure = page
                    }

                    is Outcome.Success -> {
                        if (page.value.isEmpty()) break
                        posts += page.value
                        from = page.value.last().id
                    }
                }
            }
            return failure?.takeIf { posts.isEmpty() } ?: Outcome.Success(posts.take(limit))
        }

        /** The messages of the page after message [from], oldest first. */
        private suspend fun newerThan(
            chatId: Long,
            from: Long,
        ): Outcome<List<Message>> =
            client.request(TdApi.GetChatHistory(chatId, from, -(PAGE - 1), PAGE, false)).map { history ->
                history.messages
                    .filterNotNull()
                    .filter { it.id > from }
                    .sortedBy { it.id }
                    .map { it.toMessage() }
            }

        private companion object {
            /** The most `getChatHistory` returns at once. */
            const val PAGE = 100
        }
    }
