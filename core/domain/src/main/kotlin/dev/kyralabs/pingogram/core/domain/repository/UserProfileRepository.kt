package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.UserFullInfo
import dev.kyralabs.pingogram.core.domain.model.UserProfilePhotosPage
import kotlinx.coroutines.flow.Flow

/**
 * Other users' profiles (`REQUIREMENTS.md` FR-P2): what TDLib knows about a user beyond their `User` (bio, birthday,
 * block state, a bot's description) and their profile photos; a secret chat with them starts through
 * `SecretChatRepository`. The logged-in user's own profile, which they edit, is `ProfileRepository`'s. Nothing is
 * cached here: TDLib keeps the full info itself and reports its changes.
 *
 * Requests that get no answer in time fail with `DomainError.Network`; a user TDLib does not know (or may not reach)
 * fails with `DomainError.NotFound`.
 */
interface UserProfileRepository {
    /**
     * The full info of user [userId] (TDLib asks the server the first time, answers from memory after), then the info
     * again after every change TDLib reports, while collected: a bio edited on another device, the user blocked or
     * unblocked here or elsewhere. A failed first request is the flow's first value; changes still follow it.
     */
    fun fullInfo(userId: Long): Flow<Outcome<UserFullInfo>>

    /** Up to [limit] (at most 100) of user [userId]'s profile photos, after the first [offset] of them. */
    suspend fun profilePhotos(
        userId: Long,
        offset: Int = 0,
        limit: Int = PHOTOS_PAGE_SIZE,
    ): Outcome<UserProfilePhotosPage>

    companion object {
        /** The profile photos a page holds: enough for the carousel's first screens. */
        const val PHOTOS_PAGE_SIZE = 20
    }
}
