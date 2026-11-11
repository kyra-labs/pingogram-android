package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Community
import dev.kyralabs.pingogram.core.domain.model.CommunityDetails
import kotlinx.coroutines.flow.Flow

/**
 * Communities (td_api.tl `community`): chats gathered under one name, which a supergroup or channel names in its full
 * info. TDLib announces each community the account knows with `updateCommunity`; what one holds comes from
 * `getCommunityFullInfo`. td_api.tl (a94b83d) has no function to add a chat to a community or to list the user's
 * communities, so neither is offered.
 */
interface CommunityRepository {
    /** Community [id] as TDLib announced it, then each change; null while TDLib has not announced it. */
    fun community(id: Long): Flow<Community?>

    /** What community [id] holds (`getCommunityFullInfo`), then each `updateCommunityFullInfo` while collected. */
    fun details(id: Long): Flow<Outcome<CommunityDetails>>

    /** Renames community [id]; [name] must not be blank (`DomainError.TitleInvalid`). */
    suspend fun rename(
        id: Long,
        name: String,
    ): Outcome<Unit>

    /** Deletes community [id], which its owner alone may do; its chats stay as they are. */
    suspend fun delete(id: Long): Outcome<Unit>
}
