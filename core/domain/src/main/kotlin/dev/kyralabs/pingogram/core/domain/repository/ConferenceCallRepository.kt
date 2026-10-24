package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ConferenceChannel
import dev.kyralabs.pingogram.core.domain.model.ConferenceInviteResult
import dev.kyralabs.pingogram.core.domain.model.ConferenceJoin
import dev.kyralabs.pingogram.core.domain.model.ConferenceJoinParameters
import dev.kyralabs.pingogram.core.domain.model.ConferenceTarget
import dev.kyralabs.pingogram.core.domain.model.ConferenceVerification
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow

/**
 * Conference calls (`docs/adr/0025-conference-calls.md`, `docs/ARCHITECTURE.md`, Calls): what they have beyond a
 * voice chat, whose calls, participants, mute and leaving [GroupCallRepository] keeps for them too. TDLib keeps the
 * call's blockchain and encrypts its media: [encrypt] and [decrypt] for every frame the engine sends or receives.
 * TDLib announces the verification and the chain's members only by update, so this repository subscribes at app start;
 * everything is dropped when TDLib closes (logout).
 */
interface ConferenceCallRepository {
    /** Conference call [id]'s emojis to compare, then every change; null until TDLib announced them. */
    fun verification(id: Int): Flow<ConferenceVerification?>

    /**
     * The users in conference call [id]'s blockchain, those who send and receive its media, then every change; shown
     * as participants even before TDLib knows anything more of them.
     */
    fun members(id: Int): Flow<ImmutableList<Long>>

    /** Starts a new conference call and joins it with [join] (`createGroupCall`). */
    suspend fun create(join: ConferenceJoinParameters): Outcome<ConferenceJoin>

    /** Joins the conference call of [target] (`joinGroupCall`). */
    suspend fun join(
        target: ConferenceTarget,
        join: ConferenceJoinParameters,
    ): Outcome<ConferenceJoin>

    /** Invites user [userId] into conference call [id], to a video call where [isVideo]. */
    suspend fun invite(
        id: Int,
        userId: Long,
        isVideo: Boolean,
    ): Outcome<ConferenceInviteResult>

    /** Declines, or as its sender withdraws, the invitation [messageId] in chat [chatId]. */
    suspend fun decline(
        chatId: Long,
        messageId: Long,
    ): Outcome<Unit>

    /**
     * [data] encrypted for conference call [id]'s [channel], its first [unencryptedPrefix] bytes left as they are;
     * null when TDLib cannot (the call's key is not agreed yet, it ended) or does not answer in time: the frame is
     * dropped. Called for every frame, from the engine's threads.
     */
    suspend fun encrypt(
        id: Int,
        channel: ConferenceChannel,
        data: ByteArray,
        unencryptedPrefix: Int,
    ): ByteArray?

    /** [data] from user [userId] in conference call [id], decrypted; null as for [encrypt]. */
    suspend fun decrypt(
        id: Int,
        userId: Long,
        data: ByteArray,
    ): ByteArray?
}
