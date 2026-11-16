package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Community
import dev.kyralabs.pingogram.core.domain.model.CommunityAddChatRequest
import dev.kyralabs.pingogram.core.domain.model.CommunityAddResult
import dev.kyralabs.pingogram.core.domain.model.CommunityDetails
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow

/**
 * Communities (td_api.tl `community`): chats gathered under one name, which a supergroup or channel names in its full
 * info. TDLib announces each community the account knows with `updateCommunity`; what one holds comes from
 * `getCommunityFullInfo`. TDLib (1.8.70) has no invite link for a community and gives no member count.
 */
@Suppress("TooManyFunctions") // One function per request TDLib offers for a community.
interface CommunityRepository {
    /** Community [id] as TDLib announced it, then each change; null while TDLib has not announced it. */
    fun community(id: Long): Flow<Community?>

    /**
     * What community [id] holds (`getCommunityFullInfo`), then each `updateCommunityFullInfo` while collected; first
     * what it held when last read in this session, if it was.
     */
    fun details(id: Long): Flow<Outcome<CommunityDetails>>

    /** Renames community [id]; [name] must not be blank (`DomainError.TitleInvalid`). */
    suspend fun rename(
        id: Long,
        name: String,
    ): Outcome<Unit>

    /** Deletes community [id], which its owner alone may do; its chats stay as they are. */
    suspend fun delete(id: Long): Outcome<Unit>

    /**
     * The communities the user joined (`getJoinedCommunities` once per collection, then every community TDLib
     * announces the user in), in Telegram's order, then the ones joined since; a community they left drops out. The
     * first value comes at once, before TDLib answers: the joined communities TDLib announced so far, in the order read
     * last in this session, so it may be empty.
     */
    fun joined(): Flow<Outcome<ImmutableList<Community>>>

    /** The user's supergroups and channels that may be added to a community (`getSuitableCommunityChats`). */
    suspend fun suitableChats(): Outcome<ImmutableList<Long>>

    /**
     * Adds chat [chatId] to community [id], visible to its administrators only where [isHidden]; a user who may not
     * change the list sends its administrators a request instead ([CommunityAddResult.RequestSent]).
     */
    suspend fun addChat(
        id: Long,
        chatId: Long,
        isHidden: Boolean,
    ): Outcome<CommunityAddResult>

    /** Takes chat [chatId] out of community [id]. */
    suspend fun removeChat(
        id: Long,
        chatId: Long,
    ): Outcome<Unit>

    /** The first requests to add a chat to community [id] (`getCommunityAddChatRequests`, at most 100). */
    suspend fun addChatRequests(id: Long): Outcome<ImmutableList<CommunityAddChatRequest>>

    /** Approves ([approve]) or declines the request to add chat [chatId] to community [id]. */
    suspend fun processAddChatRequest(
        id: Long,
        chatId: Long,
        approve: Boolean,
    ): Outcome<Unit>

    /** Mutes community [id]'s chats ([muted]) or follows each kind of chat's defaults again. */
    suspend fun setMuted(
        id: Long,
        muted: Boolean,
    ): Outcome<Unit>

    /** Shows community [id]'s chats as one entry in the chat list ([collapsed]) or each on its own. */
    suspend fun setCollapsed(
        id: Long,
        collapsed: Boolean,
    ): Outcome<Unit>
}
