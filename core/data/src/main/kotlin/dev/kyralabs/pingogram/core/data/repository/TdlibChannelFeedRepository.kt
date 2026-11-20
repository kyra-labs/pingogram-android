package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.data.mapper.toMessage
import dev.kyralabs.pingogram.core.data.updates.updateRouter
import dev.kyralabs.pingogram.core.domain.model.ChannelFeed
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.FoundMessage
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.repository.ChannelFeedRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.drinkless.tdlib.TdApi
import javax.inject.Inject

/**
 * [ChannelFeedRepository] on TDLib. td_api.tl's `openChat` says a channel's updates come only while it is open, so the
 * feed does not wait for `updateNewMessage`: it reads each channel's newest posts with `getChatHistory`, and follows
 * the channels' last posts, which the chat list keeps current (`updateChatLastMessage`). When a channel's last post
 * is newer than the newest read, the posts since are read then. Read states are the channels' own
 * (`last_read_inbox_message_id`, [isUnreadPost][dev.kyralabs.pingogram.core.domain.model.isUnreadPost]).
 *
 * - **Updates**: while the feed is collected, the posts it shows take the updates TDLib sends anyway
 *   (`updateMessageInteractionInfo`, `updateMessageContent`, `updateMessageEdited`), and a permanent
 *   `updateDeleteMessages` (not `from_cache`) takes them away.
 * - **Refreshing** ([refresh]): `getMessages` a channel; a post it no longer returns leaves the feed.
 *
 * - **Reading**: [PAGE] posts a channel (TDLib may answer a first call with fewer, so it asks once more from the
 *   oldest), at most [PARALLEL_READS] channels at once.
 * - **Seeing**: `viewMessages` with `messageSourceOther` and `force_read`, which marks posts read in a closed channel.
 */
@AccountScoped
class TdlibChannelFeedRepository
    @Inject
    constructor(
        private val client: TdlibClient,
        private val chatRepository: ChatRepository,
        @ApplicationScope private val scope: CoroutineScope,
    ) : ChannelFeedRepository {
        private val held = ChannelFeedPosts()

        private val channels: Flow<List<Chat>> =
            chatRepository.chatList(ChatListType.Main).map { it.filter(Chat::isChannel) }

        override val feed: Flow<ChannelFeed> =
            channelFlow {
                launch { client.updateRouter(scope).updates(FEED_UPDATES).collect(held::apply) }
                channels
                    .onEach { held.channels = it }
                    .onEach(::readSinceRead)
                    .combine(held.read, ::Pair)
                    .combine(held.gone) { (channels, read), gone ->
                        val posts =
                            channels.flatMap { channel ->
                                val hidden = gone[channel.id].orEmpty()
                                (read[channel.id].orEmpty() + listOfNotNull(channel.lastMessage))
                                    .distinctBy { it.id }
                                    .filterNot { it.id in hidden }
                                    .map { FoundMessage(it, channel) }
                            }
                        ChannelFeed(posts.sortedWith(NEWEST_FIRST).take(MAX_POSTS).toImmutableList(), channels.size)
                    }.collect(::send)
            }

        override suspend fun load(): Outcome<Unit> {
            val channels = channels.first()
            if (channels.isEmpty()) return Outcome.Success(Unit)
            val gate = Semaphore(PARALLEL_READS)
            val results =
                coroutineScope {
                    channels
                        .map { channel -> async { gate.withPermit { channel.id to newest(channel.id) } } }
                        .awaitAll()
                }
            val loaded = results.mapNotNull { (id, posts) -> (posts as? Outcome.Success)?.let { id to it.value } }
            held.updateRead { it + loaded }
            return if (loaded.isEmpty()) results.first().second.map {} else Outcome.Success(Unit)
        }

        override suspend fun refresh(posts: Map<Long, List<Long>>): Outcome<Unit> {
            if (posts.isEmpty()) return Outcome.Success(Unit)
            val results =
                posts.map { (chatId, ids) ->
                    client.request(TdApi.GetMessages(chatId, ids.toLongArray())).map { found ->
                        // TDLib answers in the order asked, null where a message is gone.
                        val missing = ids.filterIndexed { index, _ -> found.messages.getOrNull(index) == null }
                        val messages = found.messages.filterNotNull().map { it.toMessage() }
                        held.drop(chatId, missing)
                        held.put(chatId, messages)
                    }
                }
            return results.firstOrNull { it is Outcome.Success } ?: results.first()
        }

        override suspend fun view(
            chatId: Long,
            messageIds: List<Long>,
        ): Outcome<Unit> {
            val view = TdApi.ViewMessages(chatId, messageIds.toLongArray(), TdApi.MessageSourceOther(), true)
            return client.request(view).map {}
        }

        /** For each channel read before whose last post is newer than its newest read one: the posts since. */
        private suspend fun readSinceRead(channels: List<Chat>) {
            channels.filter(::hasUnreadNewPosts).forEach { channel ->
                (newest(channel.id) as? Outcome.Success)?.value?.let { posts ->
                    held.updateRead { read ->
                        val known = read[channel.id].orEmpty()
                        read + (channel.id to (posts + known).distinctBy(Message::id).take(PAGE))
                    }
                }
            }
        }

        /** Whether [channel] was read before and its last post is newer than every post read. */
        private fun hasUnreadNewPosts(channel: Chat): Boolean {
            val known = held.read.value[channel.id]
            val last = channel.lastMessage
            return known != null && last != null && known.none { it.id >= last.id }
        }

        /** The channel's newest [PAGE] posts, newest first. */
        private suspend fun newest(chatId: Long): Outcome<List<Message>> =
            when (val page = history(chatId, fromMessageId = 0)) {
                is Outcome.Success -> Outcome.Success(filledUp(chatId, page.value))
                is Outcome.Failure -> page
            }

        /** [first], and where TDLib answered with fewer than [PAGE], the older posts from its oldest. */
        private suspend fun filledUp(
            chatId: Long,
            first: List<Message>,
        ): List<Message> {
            val oldest = first.lastOrNull()?.id
            if (oldest == null || first.size >= PAGE) return first
            val more = (history(chatId, fromMessageId = oldest) as? Outcome.Success)?.value.orEmpty()
            return (first + more).distinctBy(Message::id).take(PAGE)
        }

        private suspend fun history(
            chatId: Long,
            fromMessageId: Long,
        ): Outcome<List<Message>> =
            client.request(TdApi.GetChatHistory(chatId, fromMessageId, 0, PAGE, false)).map { history ->
                history.messages.filterNotNull().map { it.toMessage() }
            }

        private companion object {
            const val PAGE = 10
            const val PARALLEL_READS = 4
            const val MAX_POSTS = 300

            val NEWEST_FIRST =
                compareByDescending<FoundMessage> { it.message.date }
                    .thenByDescending { it.message.id }
        }
    }

/** A channel, whose posts the feed shows; a supergroup that is not a channel is a group. */
private val Chat.isChannel: Boolean
    get() = (type as? ChatType.Supergroup)?.isChannel == true

/** The updates [ChannelFeedPosts.apply] reads. */
private val FEED_UPDATES =
    setOf(
        TdApi.UpdateDeleteMessages::class,
        TdApi.UpdateMessageContent::class,
        TdApi.UpdateMessageEdited::class,
        TdApi.UpdateMessageInteractionInfo::class,
    )
