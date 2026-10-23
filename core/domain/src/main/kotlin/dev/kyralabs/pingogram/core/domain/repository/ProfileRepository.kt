package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.EditableProfile
import dev.kyralabs.pingogram.core.domain.model.ProfileLimits
import dev.kyralabs.pingogram.core.domain.model.ProfileLink
import dev.kyralabs.pingogram.core.domain.model.UsernameCheck
import kotlinx.coroutines.flow.StateFlow

/**
 * The logged-in user's own profile (`REQUIREMENTS.md` FR-S1, Account): reading and changing their name, username,
 * bio and photo. Changes reach `UserRepository` through TDLib's `updateUser`, so everything showing the account
 * follows by itself.
 *
 * Requests that get no answer in time fail with `DomainError.Network`.
 */
interface ProfileRepository {
    /**
     * The longest bio Telegram accepts from the user, in characters (TDLib's option `bio_length_max`, larger with
     * Telegram Premium); [ProfileLimits.BIO_LENGTH_MAX_DEFAULT] until TDLib reported it.
     */
    val bioLengthMax: StateFlow<Int>

    /** The user's names, editable username and bio as TDLib has them now. */
    suspend fun editableProfile(): Outcome<EditableProfile>

    /** Whether the user could take [username] (without `@`), asking Telegram. */
    suspend fun checkUsername(username: String): Outcome<UsernameCheck>

    /** Sets the user's names; [lastName] may be empty. */
    suspend fun setName(
        firstName: String,
        lastName: String,
    ): Outcome<Unit>

    /** Sets the user's editable username (without `@`); an empty one removes it. */
    suspend fun setUsername(username: String): Outcome<Unit>

    /** Sets the user's bio, plain text; an empty one removes it. */
    suspend fun setBio(bio: String): Outcome<Unit>

    /**
     * Uploads the image at [path], a JPEG, and makes it the user's profile photo, visible to whoever the user's
     * privacy settings allow. Calls [onProgress] with the share uploaded so far, from 0 to 1, as TDLib reports it.
     * Succeeds once Telegram set the photo. It waits as long as the upload makes progress; no answer and no progress
     * in time fails with `DomainError.Network`. TDLib reads the file until then.
     */
    suspend fun setProfilePhoto(
        path: String,
        onProgress: (progress: Float) -> Unit,
    ): Outcome<Unit>

    /**
     * Deletes the user's current profile photo; their previous one, if any, becomes current again. Succeeds at once
     * when the user has no photo.
     */
    suspend fun deleteProfilePhoto(): Outcome<Unit>

    /**
     * The link to the user's profile for their QR code (`REQUIREMENTS.md` FR-P5): `t.me/<username>`, or without a
     * username a temporary one that Telegram makes for it.
     */
    suspend fun profileLink(): Outcome<ProfileLink>
}
