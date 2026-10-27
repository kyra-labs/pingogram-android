package dev.kyralabs.pingogram.feature.calls

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ConferenceChannel
import dev.kyralabs.pingogram.core.domain.model.ConferenceInviteResult
import dev.kyralabs.pingogram.core.domain.model.ConferenceJoin
import dev.kyralabs.pingogram.core.domain.model.ConferenceJoinParameters
import dev.kyralabs.pingogram.core.domain.model.ConferenceTarget
import dev.kyralabs.pingogram.core.domain.model.ConferenceVerification
import dev.kyralabs.pingogram.core.domain.repository.ConferenceCallRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Conference calls of the fakes: a link joins group call [CONFERENCE_CALL_ID] with [CONFERENCE_PARAMS]. */
const val CONFERENCE_CALL_ID = 9
const val CONFERENCE_PARAMS = "{\"conference\":true}"
const val CONFERENCE_LINK = "https://t.me/call/abc"

class FakeConferenceCallRepository : ConferenceCallRepository {
    val joins = mutableListOf<Pair<ConferenceTarget, ConferenceJoinParameters>>()
    var joinResult: Outcome<ConferenceJoin> = Outcome.Success(ConferenceJoin(CONFERENCE_CALL_ID, CONFERENCE_PARAMS))
    val verification = MutableStateFlow<ConferenceVerification?>(null)
    val members = MutableStateFlow<ImmutableList<Long>>(persistentListOf())

    /** The invitations asked for: call, user, video. */
    val invites = mutableListOf<Triple<Int, Long, Boolean>>()

    /** What [invite] answers per user; an invitation sent otherwise. */
    var inviteResults: Map<Long, Outcome<ConferenceInviteResult>> = emptyMap()

    /** While set, a join waits for it to complete. */
    var holdJoin: CompletableDeferred<Unit>? = null

    /** The frames handed over, as "encrypt <id>" and "decrypt <id> <user>". */
    val frames = mutableListOf<String>()

    override fun verification(id: Int): Flow<ConferenceVerification?> = verification

    override fun members(id: Int): Flow<ImmutableList<Long>> = members

    override suspend fun create(join: ConferenceJoinParameters): Outcome<ConferenceJoin> = joinResult

    override suspend fun join(
        target: ConferenceTarget,
        join: ConferenceJoinParameters,
    ): Outcome<ConferenceJoin> {
        joins += target to join
        holdJoin?.await()
        return joinResult
    }

    override suspend fun invite(
        id: Int,
        userId: Long,
        isVideo: Boolean,
    ): Outcome<ConferenceInviteResult> {
        invites += Triple(id, userId, isVideo)
        return inviteResults[userId] ?: Outcome.Success(ConferenceInviteResult.Invited(userId, userId * 10))
    }

    override suspend fun decline(
        chatId: Long,
        messageId: Long,
    ): Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun encrypt(
        id: Int,
        channel: ConferenceChannel,
        data: ByteArray,
        unencryptedPrefix: Int,
    ): ByteArray {
        frames += "encrypt $id"
        return data
    }

    override suspend fun decrypt(
        id: Int,
        userId: Long,
        data: ByteArray,
    ): ByteArray {
        frames += "decrypt $id $userId"
        return data
    }
}
