package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ForumTopic
import dev.kyralabs.pingogram.core.domain.model.ForumTopicList
import kotlinx.coroutines.flow.Flow

/**
 * The topics of forum supergroups (`REQUIREMENTS.md` FR-C9), for the forum's topic list and the conversation of a
 * topic, loaded a page at a time and kept up to date with TDLib's updates while someone observes them. Everything is
 * dropped when nobody does any more, and when TDLib closes (logout).
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
}
