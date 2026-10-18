package dev.kyralabs.pingogram.core.domain.model

/**
 * What someone is doing in a chat right now, shown in place of the last message in the chat list and under the
 * conversation's title ("typing…", `REQUIREMENTS.md` FR-L2, FR-C8). TDLib's `ChatAction` without its cancellation:
 * an action that ended is gone from `ChatActionRepository`.
 *
 * The `progress` of an upload is a percentage, 0–100.
 */
sealed interface ChatAction {
    data object Typing : ChatAction

    data object RecordingVoiceNote : ChatAction

    data class UploadingVoiceNote(
        val progress: Int,
    ) : ChatAction

    data object RecordingVideo : ChatAction

    data class UploadingVideo(
        val progress: Int,
    ) : ChatAction

    /** A round video message. */
    data object RecordingVideoNote : ChatAction

    data class UploadingVideoNote(
        val progress: Int,
    ) : ChatAction

    data class UploadingPhoto(
        val progress: Int,
    ) : ChatAction

    data class UploadingDocument(
        val progress: Int,
    ) : ChatAction

    data object ChoosingSticker : ChatAction

    data object ChoosingLocation : ChatAction

    data object ChoosingContact : ChatAction

    data object PlayingGame : ChatAction

    /** Watching the animation of [emoji], an animated emoji the current user clicked. */
    data class WatchingAnimations(
        val emoji: String,
    ) : ChatAction
}

/** [sender] is doing [action] in a chat: a user, or a chat (a group's anonymous admins, a channel). */
data class SenderAction(
    val sender: MessageSender,
    val action: ChatAction,
)
