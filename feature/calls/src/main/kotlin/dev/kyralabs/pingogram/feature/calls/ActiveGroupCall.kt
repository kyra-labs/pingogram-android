package dev.kyralabs.pingogram.feature.calls

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * The voice chat or live stream the user is in, as [GroupCallManager] runs it
 * (`docs/adr/0023-group-voice-chats-and-live-streams.md`): what the voice chat's screen, its notification and the
 * foreground service show. The call's title, its participants and who speaks come from `GroupCallRepository`.
 */
@Immutable
data class ActiveGroupCall(
    val chatId: Long,
    /** TDLib's id of the group call. */
    val groupCallId: Int,
    val phase: GroupCallPhase,
    /** A live stream (or a call too large to speak in): received only, nothing sent. */
    val isStream: Boolean = false,
    val isMuted: Boolean = true,
    /** The user may unmute: not muted by an administrator, or allowed to unmute themselves. */
    val canUnmute: Boolean = true,
    val isHandRaised: Boolean = false,
    /** The user may end the call for everyone (an administrator who may manage voice chats). */
    val canEnd: Boolean = false,
    val audio: CallAudio = CallAudio(),
    /** When it first connected, on the elapsed-realtime clock in milliseconds; null before. */
    val connectedAtMillis: Long? = null,
    val video: GroupCallVideo = GroupCallVideo(),
)

/** The user's camera in a voice chat (`docs/adr/0024-video-in-voice-chats.md`). */
@Immutable
data class GroupCallVideo(
    /** Telegram lets the user send their camera in this call. */
    val canSend: Boolean = false,
    /** The camera the user sends, or null with it off (and while the call is held). */
    val camera: CameraFacing? = null,
    /** The device has a front and a back camera. */
    val canSwitchCamera: Boolean = false,
    /** The others' videos received, at most four, in the order they show: pinned, then speakers. */
    val remote: ImmutableList<RemoteParticipantVideo> = persistentListOf(),
    /** The participant the user pinned on this device, or null. */
    val pinned: MessageSender? = null,
    /** The voice chat lets the user share their screen now: connected, and not a stream. */
    val canShareScreen: Boolean = false,
    /** The user shares their screen (`docs/adr/0026-screen-sharing.md`). */
    val isSharingScreen: Boolean = false,
)

/**
 * Another participant's video the voice chat receives: whose, its endpoint for the frames' sink, held back or not, and
 * whether it is their shared screen rather than their camera.
 */
@Immutable
data class RemoteParticipantVideo(
    val participant: MessageSender,
    val endpointId: String,
    val isPaused: Boolean,
    val isScreen: Boolean = false,
)

/** Where an [ActiveGroupCall] is. */
@Immutable
sealed interface GroupCallPhase {
    /** Asking Telegram to join, then connecting the media. */
    data object Joining : GroupCallPhase

    data object Connected : GroupCallPhase

    /** Connected before, the connection was lost, and the engine is looking for a new one. */
    data object Reconnecting : GroupCallPhase

    /** Over; the screen shows why for a moment, then closes. */
    data class Ended(
        val reason: GroupCallEndReason,
    ) : GroupCallPhase
}

/** Why an [ActiveGroupCall] ended. */
@Immutable
sealed interface GroupCallEndReason {
    /** The user left. */
    data object Left : GroupCallEndReason

    /** The voice chat ended: an administrator ended it (the user, or someone else). */
    data object Ended : GroupCallEndReason

    /** The connection was lost and could not be found again. */
    data object Disconnected : GroupCallEndReason

    /** Could not join ([error] says why). */
    data class Failed(
        val error: DomainError,
    ) : GroupCallEndReason
}

/** What came of asking to join or start a voice chat. */
enum class JoinResult {
    /** Joining: the voice chat's screen shows it. */
    Joining,

    /** The user is in another call, which ends first. */
    Busy,

    /** The chat has no voice chat (anymore). */
    NoVoiceChat,
}
