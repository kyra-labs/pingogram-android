package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.domain.model.ConferenceChannel
import dev.kyralabs.pingogram.core.domain.model.ConferenceInviteResult
import dev.kyralabs.pingogram.core.domain.model.ConferenceJoin
import dev.kyralabs.pingogram.core.domain.model.ConferenceJoinParameters
import dev.kyralabs.pingogram.core.domain.model.ConferenceTarget
import dev.kyralabs.pingogram.core.domain.model.ConferenceVerification
import dev.kyralabs.pingogram.core.domain.repository.ConferenceCallRepository
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

/**
 * [ConferenceCallRepository] on TDLib's group calls outside chats. TDLib announces the verification
 * (`updateGroupCallVerificationState`) and the blockchain's members (`updateGroupCallParticipants`) only by update,
 * which this cache keeps per call; the frames' encryption is a request each (`encryptGroupCallData`,
 * `decryptGroupCallData`), which TDLib answers only asynchronously, waited for at most [FRAME_TIMEOUT].
 */
@Singleton
class TdlibConferenceCallRepository
    @Inject
    constructor(
        private val client: TdlibClient,
        @ApplicationScope scope: CoroutineScope,
    ) : ConferenceCallRepository,
        TdlibUpdateCache {
        private val store = UpdateStore(client, scope, ConferenceState(), ::reduce)

        override fun verification(id: Int): Flow<ConferenceVerification?> =
            store.state.map { it.verifications[id] }.distinctUntilChanged()

        override fun members(id: Int): Flow<ImmutableList<Long>> =
            store.state.map { it.members[id] ?: persistentListOf() }.distinctUntilChanged()

        override suspend fun create(join: ConferenceJoinParameters): Outcome<ConferenceJoin> =
            client.request(TdApi.CreateGroupCall(join.toTdApi())).map { it.toConferenceJoin() }

        override suspend fun join(
            target: ConferenceTarget,
            join: ConferenceJoinParameters,
        ): Outcome<ConferenceJoin> =
            client.request(TdApi.JoinGroupCall(target.toTdApi(), join.toTdApi())).map { it.toConferenceJoin() }

        override suspend fun invite(
            id: Int,
            userId: Long,
            isVideo: Boolean,
        ): Outcome<ConferenceInviteResult> =
            client.request(TdApi.InviteGroupCallParticipant(id, userId, isVideo)).map { result ->
                when (result) {
                    is TdApi.InviteGroupCallParticipantResultSuccess -> {
                        ConferenceInviteResult.Invited(result.chatId, result.messageId)
                    }

                    is TdApi.InviteGroupCallParticipantResultUserPrivacyRestricted -> {
                        ConferenceInviteResult.PrivacyRestricted
                    }

                    is TdApi.InviteGroupCallParticipantResultUserAlreadyParticipant -> {
                        ConferenceInviteResult.AlreadyParticipant
                    }

                    else -> {
                        ConferenceInviteResult.Banned
                    }
                }
            }

        override suspend fun decline(
            chatId: Long,
            messageId: Long,
        ): Outcome<Unit> = client.request(TdApi.DeclineGroupCallInvitation(chatId, messageId)).map {}

        override suspend fun encrypt(
            id: Int,
            channel: ConferenceChannel,
            data: ByteArray,
            unencryptedPrefix: Int,
        ): ByteArray? = frame(TdApi.EncryptGroupCallData(id, channel.toTdApi(), data, unencryptedPrefix))

        override suspend fun decrypt(
            id: Int,
            userId: Long,
            data: ByteArray,
        ): ByteArray? =
            // The channel is in the data's header: td_api.tl lets it be left out.
            frame(TdApi.DecryptGroupCallData(id, TdApi.MessageSenderUser(userId), null, data))

        private suspend fun frame(function: TdApi.Function<TdApi.Data>): ByteArray? =
            when (val answer = withTimeoutOrNull(FRAME_TIMEOUT) { client.send(function) }) {
                is TdResult.Success -> answer.value.data
                is TdResult.Failure, null -> null
            }
    }

/** How long a frame may wait for TDLib's encryption: an audio frame lasts 20 ms, a late one is useless. */
internal val FRAME_TIMEOUT = 100.milliseconds

/** What [TdlibConferenceCallRepository] keeps, per group call id. */
internal data class ConferenceState(
    val verifications: PersistentMap<Int, ConferenceVerification> = persistentMapOf(),
    val members: PersistentMap<Int, ImmutableList<Long>> = persistentMapOf(),
)

private fun reduce(
    state: ConferenceState,
    update: TdApi.Update,
): ConferenceState =
    when (update) {
        is TdApi.UpdateGroupCallVerificationState -> {
            val verification =
                ConferenceVerification(
                    update.generation,
                    update.emojis
                        .orEmpty()
                        .toList()
                        .toImmutableList(),
                )
            if (state.verifications[update.groupCallId] == verification) {
                state
            } else {
                state.copy(verifications = state.verifications.put(update.groupCallId, verification))
            }
        }

        is TdApi.UpdateGroupCallParticipants -> {
            val members =
                update.participantUserIds
                    ?.toList()
                    .orEmpty()
                    .toImmutableList()
            if (state.members[update.groupCallId] == members) {
                state
            } else {
                state.copy(members = state.members.put(update.groupCallId, members))
            }
        }

        is TdApi.UpdateGroupCall -> {
            // An ended call keeps nothing to compare or list.
            if (update.groupCall.isActive) {
                state
            } else {
                state.copy(
                    verifications = state.verifications.remove(update.groupCall.id),
                    members = state.members.remove(update.groupCall.id),
                )
            }
        }

        else -> {
            state
        }
    }

private fun ConferenceJoinParameters.toTdApi() = TdApi.GroupCallJoinParameters(audioSourceId, payload, isMuted, isVideo)

private fun ConferenceTarget.toTdApi(): TdApi.InputGroupCall =
    when (this) {
        is ConferenceTarget.Link -> TdApi.InputGroupCallLink(link)
        is ConferenceTarget.Invitation -> TdApi.InputGroupCallMessage(chatId, messageId)
    }

private fun ConferenceChannel.toTdApi(): TdApi.GroupCallDataChannel =
    when (this) {
        ConferenceChannel.Main -> TdApi.GroupCallDataChannelMain()
        ConferenceChannel.ScreenSharing -> TdApi.GroupCallDataChannelScreenSharing()
    }

private fun TdApi.GroupCallInfo.toConferenceJoin() = ConferenceJoin(groupCallId, joinPayload.orEmpty())
