package dev.kyralabs.pingogram.feature.calls.ui

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.feature.calls.AudioRoute
import dev.kyralabs.pingogram.feature.calls.CameraFacing
import dev.kyralabs.pingogram.feature.calls.GroupCallEndReason
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** What the voice chat's screen shows (`docs/adr/0023-group-voice-chats-and-live-streams.md`). */
@Immutable
sealed interface GroupCallUiState {
    /** Joining: nothing to show yet. */
    data object Loading : GroupCallUiState

    /** No voice chat: the screen closes. */
    data object Closed : GroupCallUiState

    /** A conference call's link opened the screen: it asks before it joins (`docs/adr/0025-conference-calls.md`). */
    data object ConfirmConference : GroupCallUiState

    /**
     * Before joining: the microphone permission is missing, which a voice chat needs to speak. The screen says why,
     * then asks Android ([canAsk]) or, after a final refusal, offers the app's settings.
     */
    data class NeedsMicrophone(
        val chat: GroupCallChat,
        val canAsk: Boolean,
    ) : GroupCallUiState

    /**
     * Could not join: the user is in another call ([isBusy]), or the chat has no voice chat (anymore). Shown for a
     * moment, then the screen closes.
     */
    data class Unavailable(
        val chat: GroupCallChat,
        val isBusy: Boolean,
    ) : GroupCallUiState

    data class InCall(
        val chat: GroupCallChat,
        val stage: GroupCallStage,
        /** A conference call (`docs/adr/0025-conference-calls.md`): no chat, and [emojis] to compare. */
        val isConference: Boolean = false,
        val emojis: ImmutableList<String> = persistentListOf(),
        /** "01:23" once connected. */
        val duration: String? = null,
        /** How many are in it; null while TDLib has not said. */
        val participantCount: Int? = null,
        /** The participants TDLib announced, speakers and raised hands first. */
        val participants: ImmutableList<GroupCallParticipantUi> = persistentListOf(),
        /** A live stream: watched, its video full screen, nothing sent. */
        val isStream: Boolean = false,
        val isMuted: Boolean = true,
        /** The user may unmute; otherwise they raise their hand. */
        val canUnmute: Boolean = true,
        val isHandRaised: Boolean = false,
        /** The user may end it for everyone. */
        val canEnd: Boolean = false,
        val route: AudioRoute? = null,
        val routes: ImmutableList<AudioRoute> = persistentListOf(),
        val video: GroupCallVideoUi = GroupCallVideoUi(),
    ) : GroupCallUiState {
        /** The controls apply: joined and not over. */
        val hasControls: Boolean get() = stage !is GroupCallStage.Ended

        /** There is video to watch, which picture-in-picture keeps showing: a live stream, or others' videos. */
        fun watches(): Boolean = isStream || video.remote.isNotEmpty()
    }
}

/** The user's camera in the voice chat (`docs/adr/0024-video-in-voice-chats.md`). */
@Immutable
data class GroupCallVideoUi(
    /** The panel offers the camera: Telegram allows video in the call and the device has a camera. */
    val canSend: Boolean = false,
    /** The camera the user sends (the self-view shows), or null with it off. */
    val camera: CameraFacing? = null,
    val canSwitchCamera: Boolean = false,
    /** The others' videos the screen shows, the pinned one first: at most four (the manager's choice). */
    val remote: ImmutableList<ParticipantVideoUi> = persistentListOf(),
    /** "Share screen" shows: connected, not a stream (`docs/adr/0026-screen-sharing.md`). */
    val canShareScreen: Boolean = false,
    /** The user shares their screen: the "You're sharing your screen" pill shows. */
    val isSharingScreen: Boolean = false,
)

/**
 * A participant's video tile: whose it is, where its frames come from, whether the user pinned it, and whether it is
 * their shared screen ([isScreen]: shown whole, not cropped).
 */
@Immutable
data class ParticipantVideoUi(
    /** The participant's row key: "user:42" or "chat:-100"; pinning goes by it. */
    val key: String,
    /** The video's endpoint, which the tile's renderer receives the frames of. */
    val endpointId: String,
    val name: String,
    /** The participant held their video back: the tile says so. */
    val isPaused: Boolean,
    val isPinned: Boolean,
    val isSpeaking: Boolean,
    val isScreen: Boolean = false,
) {
    /** Unique among the tiles: a participant may show their camera and their screen. */
    val tileKey: String get() = if (isScreen) "$key:screen" else key
}

/** The voice chat's chat: its title and photo. */
@Immutable
data class GroupCallChat(
    val chatId: Long,
    val title: String,
    val photo: ProfilePhoto?,
)

/** Where the voice chat is, as the screen says it. */
@Immutable
sealed interface GroupCallStage {
    data object Joining : GroupCallStage

    data object Connected : GroupCallStage

    data object Reconnecting : GroupCallStage

    data class Ended(
        val reason: GroupCallEndReason,
    ) : GroupCallStage
}

/** One participant's row. */
@Immutable
data class GroupCallParticipantUi(
    /** Stable across updates: "user:42" or "chat:-100". */
    val key: String,
    /** For the avatar's placeholder color: the user's or the chat's id. */
    val id: Long,
    val name: String,
    val photo: ProfilePhoto?,
    val isCurrentUser: Boolean,
    val state: ParticipantState,
)

/** What a participant is doing, as their row says it. */
enum class ParticipantState {
    Speaking,

    /** Unmuted and silent. */
    Listening,

    Muted,

    /** Muted, asking to speak. */
    HandRaised,
}

/** What the user does on the voice chat's screen. */
sealed interface GroupCallEvent {
    data object ToggleMute : GroupCallEvent

    data object ToggleHand : GroupCallEvent

    data class SelectRoute(
        val route: AudioRoute,
    ) : GroupCallEvent

    data object Leave : GroupCallEvent

    /** Confirmed: ends the voice chat for everyone. */
    data object EndForEveryone : GroupCallEvent

    /** Android answered the permission request: [granted], and whether it would ask again ([canAskAgain]). */
    data class MicrophoneResult(
        val granted: Boolean,
        val canAskAgain: Boolean,
    ) : GroupCallEvent

    /** "Not now" before the permission request: nothing is joined. */
    data object DismissMicrophone : GroupCallEvent

    /** "Join" on a conference call's link. */
    data object JoinConference : GroupCallEvent

    /** "Not now" on a conference call's link: nothing is joined, and the screen closes. */
    data object DeclineConference : GroupCallEvent

    /** Turns the camera on (asking for the permission first where needed) or off. */
    data object ToggleCamera : GroupCallEvent

    /** The other camera: front after back, and back. */
    data object SwitchCamera : GroupCallEvent

    /** Pins the video of the participant with this [key] on this device, or unpins it if it is pinned. */
    data class TogglePin(
        val key: String,
    ) : GroupCallEvent

    /** Shares the screen (Android's consent dialog first) or stops sharing it. */
    data object ToggleScreenSharing : GroupCallEvent

    /** Android's consent dialog closed: the user agreed ([granted]; its result reached the engine) or not. */
    data class ScreenSharingResult(
        val granted: Boolean,
    ) : GroupCallEvent

    /** Android answered the camera's permission request. */
    data class CameraResult(
        val granted: Boolean,
        val canAskAgain: Boolean,
    ) : GroupCallEvent
}
