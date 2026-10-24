package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.domain.model.Birthdate
import dev.kyralabs.pingogram.core.domain.model.EditableProfile
import dev.kyralabs.pingogram.core.domain.model.ProfileLimits
import dev.kyralabs.pingogram.core.domain.model.ProfileLink
import dev.kyralabs.pingogram.core.domain.model.UsernameCheck
import dev.kyralabs.pingogram.core.domain.repository.ProfileRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ProfileRepository] on TDLib. It keeps two options from TDLib's updates: `bio_length_max`, which TDLib announces
 * once at start, and `my_id`, whose private chat ("Saved Messages") checks usernames: td_api.tl takes "a private chat
 * with self" for the user's own username (0 would check one for a chat being created), and a private chat's id is its
 * user's id in TDLib.
 *
 * - The profile comes from `getMe` and the bio from `getUserFullInfo`, both answered from TDLib's memory.
 * - A profile photo is uploaded by `setProfilePhoto` from the local file; its progress comes from the `updateFile`s of
 *   that file, recognized by its path.
 * - Deleting the photo deletes the one `getMe` reports as current.
 *
 * Nothing about the profile (names, username, bio, paths) is logged.
 */
@Singleton
class TdlibProfileRepository
    @Inject
    constructor(
        private val client: TdlibClient,
        @ApplicationScope scope: CoroutineScope,
    ) : ProfileRepository,
        TdlibUpdateCache {
        private val store = UpdateStore(client, scope, ProfileOptions(), ProfileOptions::reduce)

        override val bioLengthMax: StateFlow<Int> =
            store.state
                .map { it.bioLengthMax ?: ProfileLimits.BIO_LENGTH_MAX_DEFAULT }
                .stateIn(scope, SharingStarted.Eagerly, ProfileLimits.BIO_LENGTH_MAX_DEFAULT)

        override suspend fun editableProfile(): Outcome<EditableProfile> {
            val me =
                when (val result = client.request(TdApi.GetMe())) {
                    is Outcome.Success -> result.value
                    is Outcome.Failure -> return result
                }
            return client.request(TdApi.GetUserFullInfo(me.id)).map { info ->
                EditableProfile(
                    firstName = me.firstName.orEmpty(),
                    lastName = me.lastName.orEmpty(),
                    username = me.usernames?.editableUsername.orEmpty(),
                    bio = info.bio?.text.orEmpty(),
                    birthdate = info.birthdate?.toBirthdate(),
                )
            }
        }

        override suspend fun checkUsername(username: String): Outcome<UsernameCheck> {
            val myId = store.state.value.myUserId ?: return Outcome.Failure(DomainError.Unauthorized)
            return client.request(TdApi.CheckChatUsername(myId, username)).map { it.toUsernameCheck() }
        }

        override suspend fun setName(
            firstName: String,
            lastName: String,
        ): Outcome<Unit> = client.request(TdApi.SetName(firstName, lastName)).map {}

        override suspend fun setUsername(username: String): Outcome<Unit> =
            client.request(TdApi.SetUsername(username)).map {}

        override suspend fun setBio(bio: String): Outcome<Unit> = client.request(TdApi.SetBio(bio)).map {}

        override suspend fun setBirthdate(birthdate: Birthdate?): Outcome<Unit> =
            client
                .request(TdApi.SetBirthdate(birthdate?.let { TdApi.Birthdate(it.day, it.month, it.year ?: 0) }))
                .map {}

        override suspend fun setProfilePhoto(
            path: String,
            onProgress: (progress: Float) -> Unit,
        ): Outcome<Unit> = client.uploadPhoto(path, onProgress) { photo -> TdApi.SetProfilePhoto(photo, false) }

        override suspend fun profileLink(): Outcome<ProfileLink> =
            client.request(TdApi.GetUserLink()).map { ProfileLink(it.url, it.expiresIn) }

        override suspend fun deleteProfilePhoto(): Outcome<Unit> =
            when (val me = client.request(TdApi.GetMe())) {
                is Outcome.Failure -> {
                    me
                }

                is Outcome.Success -> {
                    when (val photoId = me.value.profilePhoto?.id) {
                        null -> Outcome.Success(Unit)
                        else -> client.request(TdApi.DeleteProfilePhoto(photoId)).map {}
                    }
                }
            }
    }

internal fun TdApi.CheckChatUsernameResult.toUsernameCheck(): UsernameCheck =
    when (this) {
        is TdApi.CheckChatUsernameResultOk -> UsernameCheck.Available

        is TdApi.CheckChatUsernameResultUsernameInvalid -> UsernameCheck.Invalid

        is TdApi.CheckChatUsernameResultUsernameOccupied -> UsernameCheck.Taken

        is TdApi.CheckChatUsernameResultUsernamePurchasable -> UsernameCheck.ForSale

        // PublicChatsTooMany, PublicGroupsUnavailable, and results a newer TDLib may add.
        else -> UsernameCheck.Unavailable
    }

/** The options this repository keeps; null until TDLib reported them, and after logout. */
private data class ProfileOptions(
    val myUserId: Long? = null,
    val bioLengthMax: Int? = null,
) {
    fun reduce(update: TdApi.Update): ProfileOptions {
        if (update !is TdApi.UpdateOption) return this
        // OptionValueEmpty once an option is gone.
        val value = (update.value as? TdApi.OptionValueInteger)?.value
        return when (update.name) {
            MY_ID_OPTION -> copy(myUserId = value)
            BIO_LENGTH_MAX_OPTION -> copy(bioLengthMax = value?.toInt())
            else -> this
        }
    }
}

private const val MY_ID_OPTION = "my_id"
private const val BIO_LENGTH_MAX_OPTION = "bio_length_max"

/** TDLib's birthday as Pingogram's: a year of 0 means the user left it out. */
private fun TdApi.Birthdate.toBirthdate() = Birthdate(day, month, year.takeIf { it > 0 })
