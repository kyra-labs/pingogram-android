package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.ImmutableList

/**
 * A chat's voice chat as TDLib announces it with the chat (td_api.tl `videoChat`, `updateChatVideoChat`,
 * `docs/adr/0023-group-voice-chats-and-live-streams.md`): the group call it is, whether anyone is in it, and whom the
 * user joins as.
 */
data class ChatVideoChat(
    /** TDLib's id of the group call, valid in this session only. */
    val groupCallId: Int,
    val hasParticipants: Boolean,
    /** Whom the user joins as (themselves, or a chat they manage); null to let TDLib choose. */
    val defaultParticipant: MessageSender?,
)

/** A group call (td_api.tl `groupCall`): a group's or channel's voice chat, or a live stream. */
data class GroupCall(
    val id: Int,
    /** Its title; empty for none, where the chat's title stands in. */
    val title: String,
    /** False once it ended, and while it is scheduled. */
    val isActive: Boolean,
    /** When a scheduled call starts (Unix time, seconds); 0 if it is not scheduled. */
    val scheduledStartDate: Long,
    /** A channel's live stream from an RTMP source: everyone only listens and watches. */
    val isRtmpStream: Boolean,
    val isJoined: Boolean,
    /** The user was in it and must join again (the connection or TDLib restarted). */
    val needRejoin: Boolean,
    /** The user may start, end and manage it (an administrator who may manage voice chats). */
    val canBeManaged: Boolean,
    val participantCount: Int,
    /** Whether TDLib has announced every participant (`loadGroupCallParticipants` has nothing more). */
    val loadedAllParticipants: Boolean,
    /** Up to three participants who spoke recently, as Telegram reports them, and whether each is speaking now. */
    val recentSpeakers: ImmutableList<GroupCallSpeaker>,
    /** New participants join muted and can't unmute themselves. */
    val muteNewParticipants: Boolean,
    /** How long it has been going (seconds), for an active call. */
    val duration: Int,
    /** The user may send their camera in it (`docs/adr/0024-video-in-voice-chats.md`). */
    val canEnableVideo: Boolean = false,
    /** The user sends their camera, as Telegram knows it. */
    val isMyVideoEnabled: Boolean = false,
)

/** One of a [GroupCall]'s recent speakers. */
data class GroupCallSpeaker(
    val participant: MessageSender,
    val isSpeaking: Boolean,
)

/** Someone in the group call the user is in (td_api.tl `groupCallParticipant`). */
data class GroupCallParticipant(
    /** A user, or a chat a user joined as. */
    val participant: MessageSender,
    /** The participant's audio source (SSRC) in the call. */
    val audioSourceId: Int,
    /** What the participant wrote about themselves; empty for nothing. */
    val bio: String,
    val isCurrentUser: Boolean,
    val isSpeaking: Boolean,
    val isHandRaised: Boolean,
    /** An administrator muted them for everyone. */
    val isMutedForAllUsers: Boolean,
    /** The user muted them on this device only. */
    val isMutedForCurrentUser: Boolean,
    /** Muted by an administrator, but allowed to unmute themselves. */
    val canUnmuteSelf: Boolean,
    val canBeMutedForAllUsers: Boolean,
    val canBeUnmutedForAllUsers: Boolean,
    /** TDLib's sort key, descending: speakers and raised hands first. */
    val order: String,
    /** The participant's camera, while they send it (`docs/adr/0024-video-in-voice-chats.md`); null otherwise. */
    val video: GroupCallParticipantVideo? = null,
    /** The participant's shared screen, while they share it (`docs/adr/0026-screen-sharing.md`); null otherwise. */
    val screen: GroupCallParticipantVideo? = null,
) {
    /** Whether the participant is silent for the user: muted by an administrator, by themselves, or locally. */
    val isMuted: Boolean get() = isMutedForAllUsers || isMutedForCurrentUser
}

/**
 * A participant's video in a group call (td_api.tl `groupCallParticipantVideoInfo`): what the engine needs to receive
 * it ([endpointId], [sourceGroups]), and whether it is held back ([isPaused]).
 */
data class GroupCallParticipantVideo(
    val endpointId: String,
    val sourceGroups: ImmutableList<GroupCallVideoSourceGroup>,
    val isPaused: Boolean,
)

/** One of a video's source groups (td_api.tl `groupCallVideoSourceGroup`): its SSRCs and their [semantics] ("SIM"). */
data class GroupCallVideoSourceGroup(
    val semantics: String,
    val sourceIds: ImmutableList<Int>,
)

/** One of a live stream's channels (td_api.tl `groupCallStream`): its newest segment's time. */
data class GroupCallStream(
    val channelId: Int,
    /** The segments' length: 1000 ms divided by 2 to the power of [scale] (0: one second). */
    val scale: Int,
    /** The newest segment's start, in milliseconds since the stream began. */
    val timeOffset: Long,
)

/** The quality of a live stream's video segment (td_api.tl `GroupCallVideoQuality`). */
enum class GroupCallVideoQuality { Thumbnail, Medium, Full }

/** A live stream's segment, or why there is none. */
sealed interface StreamSegment {
    class Data(
        val bytes: ByteArray,
    ) : StreamSegment

    /** Telegram has not recorded it yet (or asks to wait a little): ask again shortly. */
    data object NotReady : StreamSegment

    /** It cannot be had at this time (too old, or the time is off): find the stream's time again. */
    data object Unavailable : StreamSegment
}
