package dev.kyralabs.pingogram.feature.calls

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatVideoChat
import dev.kyralabs.pingogram.core.domain.model.GroupCall
import dev.kyralabs.pingogram.core.domain.model.GroupCallParticipant
import dev.kyralabs.pingogram.core.domain.model.GroupCallStream
import dev.kyralabs.pingogram.core.domain.model.GroupCallVideoQuality
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.StreamSegment
import dev.kyralabs.pingogram.core.domain.repository.GroupCallRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** The voice chat of the fakes: chat [CHAT_ID]'s group call [CALL_ID], the user [SELF_ID] its default participant. */
const val CHAT_ID = -100L
const val CALL_ID = 5
const val SELF_ID = 42L

data class Join(
    val id: Int,
    val participant: MessageSender?,
    val audioSourceId: Int,
    val payload: String,
    val isMuted: Boolean,
)

data class MuteRequest(
    val participant: MessageSender,
    val isMuted: Boolean,
)

data class SegmentRequest(
    val timeOffset: Long,
    val scale: Int,
    val channelId: Int,
    val quality: GroupCallVideoQuality?,
)

class FakeGroupCallRepository : GroupCallRepository {
    val videoChats =
        MutableStateFlow(mapOf(CHAT_ID to ChatVideoChat(CALL_ID, true, MessageSender.User(SELF_ID))))
    val calls = MutableStateFlow(mapOf(CALL_ID to groupCall()))
    private val participants = MutableStateFlow<Map<MessageSender, GroupCallParticipant>>(emptyMap())
    val joins = mutableListOf<Join>()
    var joinResult: Outcome<String> = Outcome.Success(PARAMS)
    val loaded = mutableListOf<Int>()
    val left = mutableListOf<Int>()
    val ended = mutableListOf<Int>()
    val started = mutableListOf<Long>()
    val mutes = mutableListOf<MuteRequest>()
    val hands = mutableListOf<Pair<MessageSender, Boolean>>()
    val segments = mutableListOf<SegmentRequest>()
    var segment: StreamSegment = StreamSegment.Data(byteArrayOf(7))

    fun participant(participant: GroupCallParticipant) {
        participants.value = participants.value + (participant.participant to participant)
    }

    override fun videoChat(chatId: Long): Flow<ChatVideoChat?> = videoChats.map { it[chatId] }

    override fun groupCall(id: Int): Flow<GroupCall?> = calls.map { it[id] }

    override fun participants(id: Int): Flow<ImmutableList<GroupCallParticipant>> =
        participants.map { it.values.sortedByDescending { participant -> participant.order }.toImmutableList() }

    override suspend fun loadParticipants(
        id: Int,
        limit: Int,
    ): Outcome<Unit> {
        loaded += id
        return Outcome.Success(Unit)
    }

    override suspend fun startVideoChat(chatId: Long): Outcome<Int> {
        started += chatId
        videoChats.value = mapOf(chatId to ChatVideoChat(CALL_ID, false, MessageSender.User(SELF_ID)))
        return Outcome.Success(CALL_ID)
    }

    override suspend fun join(
        id: Int,
        participant: MessageSender?,
        audioSourceId: Int,
        payload: String,
        isMuted: Boolean,
    ): Outcome<String> {
        joins += Join(id, participant, audioSourceId, payload, isMuted)
        return joinResult
    }

    override suspend fun leave(id: Int): Outcome<Unit> {
        left += id
        return Outcome.Success(Unit)
    }

    override suspend fun end(id: Int): Outcome<Unit> {
        ended += id
        return Outcome.Success(Unit)
    }

    override suspend fun setMuted(
        id: Int,
        participant: MessageSender,
        isMuted: Boolean,
    ): Outcome<Unit> {
        mutes += MuteRequest(participant, isMuted)
        return Outcome.Success(Unit)
    }

    override suspend fun setHandRaised(
        id: Int,
        participant: MessageSender,
        isHandRaised: Boolean,
    ): Outcome<Unit> {
        hands += participant to isHandRaised
        return Outcome.Success(Unit)
    }

    override suspend fun streams(id: Int): Outcome<ImmutableList<GroupCallStream>> =
        Outcome.Success(persistentListOf(GroupCallStream(0, 0, 8000), GroupCallStream(2, 0, 9000)))

    override suspend fun streamSegment(
        id: Int,
        timeOffset: Long,
        scale: Int,
        channelId: Int,
        quality: GroupCallVideoQuality?,
    ): StreamSegment {
        segments += SegmentRequest(timeOffset, scale, channelId, quality)
        return segment
    }

    companion object {
        const val PARAMS = "{\"transport\":{}}"
    }
}

fun groupCall(
    isActive: Boolean = true,
    canBeManaged: Boolean = false,
) = GroupCall(
    id = 5,
    title = "",
    isActive = isActive,
    scheduledStartDate = 0,
    isRtmpStream = false,
    isJoined = true,
    needRejoin = false,
    canBeManaged = canBeManaged,
    participantCount = 3,
    loadedAllParticipants = false,
    recentSpeakers = persistentListOf(),
    muteNewParticipants = false,
    duration = 0,
)
