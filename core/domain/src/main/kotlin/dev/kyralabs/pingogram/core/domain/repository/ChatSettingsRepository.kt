package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatReactions
import dev.kyralabs.pingogram.core.domain.model.ChatSettings
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow

/**
 * The settings of a group or channel that its administrators change (`REQUIREMENTS.md` FR-P3): reactions, restricting
 * saving content, signing posts, topics, the discussion group, and upgrading a basic group. Refusals come back as
 * `DomainError.AdminRightsRequired` where the user lacks the right.
 */
interface ChatSettingsRepository {
    /** Chat [chatId]'s settings, then each change TDLib announces while collected. */
    fun settings(chatId: Long): Flow<Outcome<ChatSettings>>

    /** The emoji Telegram offers as reactions now (td_api.tl `updateActiveEmojiReactions`), in its order. */
    val activeReactions: Flow<ImmutableList<String>>

    suspend fun setReactions(
        chatId: Long,
        reactions: ChatReactions,
    ): Outcome<Unit>

    /** Whether chat [chatId]'s messages can be forwarded and saved ([protect]: they cannot). */
    suspend fun setProtectedContent(
        chatId: Long,
        protect: Boolean,
    ): Outcome<Unit>

    /** Whether channel [chatId]'s posts carry their author's name, and link to the author's profile. */
    suspend fun setSignMessages(
        chatId: Long,
        sign: Boolean,
        showSender: Boolean,
    ): Outcome<Unit>

    /** Whether supergroup [chatId]'s messages are in topics, and whether they show as tabs. */
    suspend fun setForum(
        chatId: Long,
        isForum: Boolean,
        hasTabs: Boolean,
    ): Outcome<Unit>

    /** The groups the user may link to a channel as its discussion group (`getSuitableDiscussionChats`), by chat id. */
    suspend fun discussionGroupCandidates(): Outcome<ImmutableList<Long>>

    /** Links group [groupChatId] to channel [channelChatId] as its discussion group; 0 unlinks it. */
    suspend fun setDiscussionGroup(
        channelChatId: Long,
        groupChatId: Long,
    ): Outcome<Unit>

    /** Upgrades basic group [chatId] to a supergroup, answering the supergroup's chat id (a new chat). */
    suspend fun upgradeToSupergroup(chatId: Long): Outcome<Long>
}
