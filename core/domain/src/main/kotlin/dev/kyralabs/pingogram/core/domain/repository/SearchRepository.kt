package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.FoundMessages
import dev.kyralabs.pingogram.core.domain.model.MessageSearchFilter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow

/**
 * Global search (`REQUIREMENTS.md` FR-L6): chats, contacts, public chats and messages that match what the user typed,
 * and the chats the user opened from search before ("recently found"). Every search is one the user started: nothing
 * runs by itself, nothing is kept, and cancelling a call drops its answer (Telegram's API terms: no scraping).
 *
 * Chats come as the `ChatRepository` keeps them. A blank query finds nothing, without asking TDLib, except messages of
 * one kind ([MessageSearchFilter.PhotoAndVideo], [MessageSearchFilter.Url]), which it finds all of. Failures are
 * `DomainError.Network` (no answer in time) and `DomainError.FloodWait`, and others as TDLib reports them; a query
 * Telegram finds too short to search is no failure, it finds nothing.
 */
interface SearchRepository {
    /**
     * The chats whose title or username matches [query]: the local matches first, as soon as TDLib found them on the
     * device, then the list again with the server's matches after them, without duplicates. A server that fails or
     * does not answer leaves the local matches; only without any does its failure come instead.
     */
    fun searchChats(query: String): Flow<Outcome<ImmutableList<Chat>>>

    /**
     * The private chats of the user's contacts whose name or username matches [query]. TDLib creates the chat of a
     * contact the user never wrote to on the device, so every result opens like any chat.
     */
    suspend fun searchContacts(query: String): Outcome<ImmutableList<Chat>>

    /**
     * Every contact of the user as its private chat, as [searchContacts] finds them, sorted by name: the people the
     * New chat screen offers to write to (`REQUIREMENTS.md` FR-L7). A contact whose chat cannot be read is left out.
     */
    suspend fun contacts(): Outcome<ImmutableList<Chat>>

    /**
     * Public chats, channels and users whose username or title matches [query], outside the user's chats and
     * contacts: the "Global search" of the official apps.
     */
    suspend fun searchPublicChats(query: String): Outcome<ImmutableList<Chat>>

    /**
     * A page of the messages in all chats (secret chats aside, which only the device could search) that match [query]
     * and [filter], newest first: the first page for a null [offset], then the page after the one whose
     * [FoundMessages.nextOffset] is passed. With a filter other than [MessageSearchFilter.All], a blank query finds
     * every message of that kind.
     */
    suspend fun searchMessages(
        query: String,
        offset: String?,
        filter: MessageSearchFilter = MessageSearchFilter.All,
    ): Outcome<FoundMessages>

    /** The chats the user opened from search, the last one first, up to [RECENT_LIMIT]. */
    suspend fun recentChats(): Outcome<ImmutableList<Chat>>

    /** Puts chat [chatId] at the top of [recentChats], as when it is opened from search. */
    suspend fun addRecentChat(chatId: Long): Outcome<Unit>

    /** Takes chat [chatId] out of [recentChats]. */
    suspend fun removeRecentChat(chatId: Long): Outcome<Unit>

    /** Empties [recentChats]. */
    suspend fun clearRecentChats(): Outcome<Unit>

    /**
     * The private chats with the people the user writes to most, up to [TOP_PEOPLE_LIMIT], as TDLib ranks them; empty
     * when the user turned that ranking off.
     */
    suspend fun topPeople(): Outcome<ImmutableList<Chat>>

    companion object {
        /** How many recently found chats [recentChats] returns. */
        const val RECENT_LIMIT = 20

        /** How many people [topPeople] returns. */
        const val TOP_PEOPLE_LIMIT = 10
    }
}
