package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.error.toOutcome
import dev.kyralabs.pingogram.core.domain.model.EditableProfile
import dev.kyralabs.pingogram.core.domain.model.ProfileLimits
import dev.kyralabs.pingogram.core.domain.model.UsernameCheck
import dev.kyralabs.pingogram.core.domain.repository.ProfileRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
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

        override suspend fun setProfilePhoto(
            path: String,
            onProgress: (progress: Float) -> Unit,
        ): Outcome<Unit> =
            coroutineScope {
                val progress = Channel<Float>(Channel.CONFLATED)
                // Subscribed before the request is sent, so its first updateFile is not missed.
                val watcher =
                    launch(start = CoroutineStart.UNDISPATCHED) {
                        client.updates.collect { update ->
                            if (update is TdApi.UpdateFile && update.file.local?.path == path) {
                                update.file.uploadProgress()?.let(progress::trySend)
                            }
                        }
                    }
                val photo = TdApi.InputChatPhotoStatic(TdApi.InputFileLocal(path))
                val answer = async { client.send(TdApi.SetProfilePhoto(photo, false)).toOutcome().map {} }
                try {
                    awaitUpload(answer, progress, onProgress)
                } finally {
                    watcher.cancel()
                    answer.cancel()
                }
            }

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

/**
 * Waits for the upload's [answer], handing its [progress] to [onProgress] meanwhile. Each progress gives it another
 * [REQUEST_TIMEOUT]; a whole timeout without either fails with [DomainError.Network].
 */
private suspend fun awaitUpload(
    answer: Deferred<Outcome<Unit>>,
    progress: ReceiveChannel<Float>,
    onProgress: (Float) -> Unit,
): Outcome<Unit> {
    while (true) {
        val step =
            withTimeoutOrNull(REQUEST_TIMEOUT) {
                select<UploadStep> {
                    answer.onAwait { UploadStep.Answered(it) }
                    progress.onReceive { UploadStep.Progressed(it) }
                }
            } ?: return Outcome.Failure(DomainError.Network)
        when (step) {
            is UploadStep.Answered -> return step.outcome
            is UploadStep.Progressed -> onProgress(step.progress)
        }
    }
}

private sealed interface UploadStep {
    data class Answered(
        val outcome: Outcome<Unit>,
    ) : UploadStep

    data class Progressed(
        val progress: Float,
    ) : UploadStep
}

/** The share of the file uploaded, from 0 to 1; null while its size is unknown. */
private fun TdApi.File.uploadProgress(): Float? {
    val uploaded = remote ?: return null
    val total = if (size > 0) size else expectedSize
    return when {
        uploaded.isUploadingCompleted -> 1f
        total > 0 -> (uploaded.uploadedSize.toFloat() / total).coerceIn(0f, 1f)
        else -> null
    }
}

private fun TdApi.CheckChatUsernameResult.toUsernameCheck(): UsernameCheck =
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
