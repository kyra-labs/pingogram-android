package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.ChatVideoChat
import dev.kyralabs.pingogram.core.domain.model.GroupCall
import dev.kyralabs.pingogram.core.domain.model.GroupCallParticipant
import dev.kyralabs.pingogram.core.domain.model.GroupCallSpeaker
import dev.kyralabs.pingogram.core.domain.model.GroupCallStream
import dev.kyralabs.pingogram.core.domain.model.GroupCallVideoQuality
import kotlinx.collections.immutable.toImmutableList
import org.drinkless.tdlib.TdApi

/** Null when the chat has no voice chat (td_api.tl: `group_call_id` 0). */
internal fun TdApi.VideoChat.toChatVideoChat(): ChatVideoChat? =
    if (groupCallId == 0) {
        null
    } else {
        ChatVideoChat(
            groupCallId = groupCallId,
            hasParticipants = hasParticipants,
            defaultParticipant = defaultParticipantId?.toMessageSender(),
        )
    }

internal fun TdApi.GroupCall.toGroupCall(): GroupCall =
    GroupCall(
        id = id,
        title = title.orEmpty(),
        isActive = isActive,
        scheduledStartDate = scheduledStartDate.toLong(),
        isRtmpStream = isRtmpStream,
        isJoined = isJoined,
        needRejoin = needRejoin,
        canBeManaged = canBeManaged,
        participantCount = participantCount,
        loadedAllParticipants = loadedAllParticipants,
        recentSpeakers =
            recentSpeakers
                .orEmpty()
                .mapNotNull { speaker ->
                    speaker.participantId?.toMessageSender()?.let { GroupCallSpeaker(it, speaker.isSpeaking) }
                }.toImmutableList(),
        muteNewParticipants = muteNewParticipants,
        duration = duration,
        canEnableVideo = canEnableVideo,
        isMyVideoEnabled = isMyVideoEnabled,
    )

/** Null for a participant type added by a newer TDLib. */
internal fun TdApi.GroupCallParticipant.toGroupCallParticipant(): GroupCallParticipant? {
    val sender = participantId?.toMessageSender() ?: return null
    return GroupCallParticipant(
        participant = sender,
        audioSourceId = audioSourceId,
        bio = bio.orEmpty(),
        isCurrentUser = isCurrentUser,
        isSpeaking = isSpeaking,
        isHandRaised = isHandRaised,
        isMutedForAllUsers = isMutedForAllUsers,
        isMutedForCurrentUser = isMutedForCurrentUser,
        canUnmuteSelf = canUnmuteSelf,
        canBeMutedForAllUsers = canBeMutedForAllUsers,
        canBeUnmutedForAllUsers = canBeUnmutedForAllUsers,
        order = order.orEmpty(),
    )
}

internal fun TdApi.GroupCallStream.toGroupCallStream(): GroupCallStream =
    GroupCallStream(channelId = channelId, scale = scale, timeOffset = timeOffset)

internal fun GroupCallVideoQuality.toTdApi(): TdApi.GroupCallVideoQuality =
    when (this) {
        GroupCallVideoQuality.Thumbnail -> TdApi.GroupCallVideoQualityThumbnail()
        GroupCallVideoQuality.Medium -> TdApi.GroupCallVideoQualityMedium()
        GroupCallVideoQuality.Full -> TdApi.GroupCallVideoQualityFull()
    }
