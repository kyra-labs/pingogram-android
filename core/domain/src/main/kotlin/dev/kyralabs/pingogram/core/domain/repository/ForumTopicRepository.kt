package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ForumTopic
import dev.kyralabs.pingogram.core.domain.model.ForumTopicList
import dev.kyralabs.pingogram.core.domain.model.TopicColor
import kotlinx.coroutines.flow.Flow

/**
 * The topics of forum supergroups (`REQUIREMENTS.md` FR-C9), for the forum's topic list and the conversation of a
 * topic, loaded a page at a time and kept up to date with TDLib's updates while someone observes them. Everything is
 * dropped when nobody does any more, and when TDLib closes (logout).
 *
 * It also manages them, for members with the right (td_api.tl `chatAdministratorRights.can_manage_topics`, the
 * creator of a topic for their own, `chatPermissions.can_create_topics` to create): the changes come back through
 * the same updates, so the topics observed follow them. Telegram's refusals map to `DomainError`s:
 * `AdminRightsRequired` without the right, `TitleInvalid` for a name Telegram does not take, `NotFound` for a topic
 * that is gone, `FloodWait`, `Network`, ….
 */
interface ForumTopicRepository {
    /**
     * The topics of forum [chatId] loaded so far, then every change, for as long as it is collected. Observing loads
     * nothing: [loadMore] loads the pages, so the caller sees every failure.
     */
    fun topics(chatId: Long): Flow<ForumTopicList>

    /**
     * Loads the next page of the forum's topics, the first one when none is loaded; nothing once every topic is.
     * Calls while a page loads share it. Fails with `DomainError.FloodWait`, `DomainError.Network`, … leaving the
     * topics as they were.
     */
    suspend fun loadMore(chatId: Long): Outcome<Unit>

    /**
     * Topic [topicId] of forum [chatId], then every change, for as long as it is collected: from the loaded topics, or
     * fetched from TDLib (`getForumTopic`) when it is not loaded. Null while it is on its way, and when it cannot be
     * fetched (deleted, no access, offline).
     */
    fun topic(
        chatId: Long,
        topicId: Int,
    ): Flow<ForumTopic?>

    /**
     * Creates a topic named [name] (trimmed; blank fails with `DomainError.TitleInvalid` before TDLib is asked) with an
     * icon in [color] in forum [chatId] (`createForumTopic`), and returns its id.
     */
    suspend fun createTopic(
        chatId: Long,
        name: String,
        color: TopicColor,
    ): Outcome<Int>

    /**
     * Renames topic [topicId] (`editForumTopic`, the icon kept); [name] is trimmed, and blank fails with
     * `DomainError.TitleInvalid` before TDLib is asked. Telegram keeps a topic's color: it cannot be changed.
     */
    suspend fun renameTopic(
        chatId: Long,
        topicId: Int,
        name: String,
    ): Outcome<Unit>

    /** Closes topic [topicId], where only administrators may then write, or reopens it (`toggleForumTopicIsClosed`). */
    suspend fun setClosed(
        chatId: Long,
        topicId: Int,
        isClosed: Boolean,
    ): Outcome<Unit>

    /** Pins topic [topicId] at the top of the list, or unpins it (`toggleForumTopicIsPinned`). */
    suspend fun setPinned(
        chatId: Long,
        topicId: Int,
        isPinned: Boolean,
    ): Outcome<Unit>

    /**
     * Puts the pinned topics of forum [chatId] in the order of [topicIds], every pinned topic once
     * (`setPinnedForumTopics`); the topics observed take the order at once once TDLib took it.
     */
    suspend fun reorderPinned(
        chatId: Long,
        topicIds: List<Int>,
    ): Outcome<Unit>

    /**
     * Deletes topic [topicId] with all of its messages, for everyone (`deleteForumTopic`); it leaves the topics
     * observed once TDLib did it.
     */
    suspend fun deleteTopic(
        chatId: Long,
        topicId: Int,
    ): Outcome<Unit>

    /** Hides the General topic of forum [chatId] from the list of its members, or shows it again. */
    suspend fun setGeneralHidden(
        chatId: Long,
        isHidden: Boolean,
    ): Outcome<Unit>
}
