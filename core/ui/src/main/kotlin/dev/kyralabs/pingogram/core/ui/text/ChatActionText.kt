package dev.kyralabs.pingogram.core.ui.text

import dev.kyralabs.pingogram.core.domain.model.ChatAction
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.SenderAction
import dev.kyralabs.pingogram.core.ui.R

/**
 * What people are doing in a chat right now, in place of its last message in the chat list and under the
 * conversation's title (`REQUIREMENTS.md` FR-L2, FR-C8); null if nobody is doing anything.
 *
 * - In a private chat, the action alone: "typing…", "recording a voice message…".
 * - In a group, with the name: "Alice is typing…"; two or more people typing: "Alice and Bob are typing…",
 *   "3 people are typing…". When several people do different things, the action that started first is shown.
 *
 * @param actions the current actions in the order they started (`ChatActionRepository`).
 * @param name the short name of a sender ([shortName] for users, the title for chats); null if not known, which reads
 *   "Someone".
 */
fun chatActionText(
    actions: List<SenderAction>,
    isGroup: Boolean,
    name: (MessageSender) -> UiText?,
): UiText? {
    val first = actions.firstOrNull() ?: return null

    fun nameOf(action: SenderAction) = name(action.sender)?.bidiIsolated() ?: UiText.resource(R.string.preview_someone)
    return when {
        !isGroup -> {
            actionText(first.action)
        }

        actions.size == 1 || actions.any { it.action != ChatAction.Typing } -> {
            namedActionText(nameOf(first), first.action)
        }

        actions.size == 2 -> {
            UiText.resource(R.string.typing_two, nameOf(first), nameOf(actions[1]))
        }

        else -> {
            UiText.plural(R.plurals.typing_many, actions.size, actions.size)
        }
    }
}

/** [action] without a name, for private chats. */
private fun actionText(action: ChatAction): UiText =
    when (action) {
        ChatAction.Typing -> UiText.resource(R.string.action_typing)
        ChatAction.RecordingVoiceNote -> UiText.resource(R.string.action_recording_voice)
        is ChatAction.UploadingVoiceNote -> UiText.resource(R.string.action_sending_voice)
        ChatAction.RecordingVideo -> UiText.resource(R.string.action_recording_video)
        is ChatAction.UploadingVideo -> UiText.resource(R.string.action_sending_video)
        ChatAction.RecordingVideoNote -> UiText.resource(R.string.action_recording_video_message)
        is ChatAction.UploadingVideoNote -> UiText.resource(R.string.action_sending_video_message)
        is ChatAction.UploadingPhoto -> UiText.resource(R.string.action_sending_photo)
        is ChatAction.UploadingDocument -> UiText.resource(R.string.action_sending_file)
        ChatAction.ChoosingSticker -> UiText.resource(R.string.action_choosing_sticker)
        ChatAction.ChoosingLocation -> UiText.resource(R.string.action_choosing_location)
        ChatAction.ChoosingContact -> UiText.resource(R.string.action_choosing_contact)
        ChatAction.PlayingGame -> UiText.resource(R.string.action_playing_game)
        is ChatAction.WatchingAnimations -> UiText.resource(R.string.action_watching_animation, action.emoji)
    }

/** [name] doing [action], for groups. */
private fun namedActionText(
    name: UiText,
    action: ChatAction,
): UiText =
    when (action) {
        ChatAction.Typing -> {
            UiText.resource(R.string.named_action_typing, name)
        }

        ChatAction.RecordingVoiceNote -> {
            UiText.resource(R.string.named_action_recording_voice, name)
        }

        is ChatAction.UploadingVoiceNote -> {
            UiText.resource(R.string.named_action_sending_voice, name)
        }

        ChatAction.RecordingVideo -> {
            UiText.resource(R.string.named_action_recording_video, name)
        }

        is ChatAction.UploadingVideo -> {
            UiText.resource(R.string.named_action_sending_video, name)
        }

        ChatAction.RecordingVideoNote -> {
            UiText.resource(R.string.named_action_recording_video_message, name)
        }

        is ChatAction.UploadingVideoNote -> {
            UiText.resource(R.string.named_action_sending_video_message, name)
        }

        is ChatAction.UploadingPhoto -> {
            UiText.resource(R.string.named_action_sending_photo, name)
        }

        is ChatAction.UploadingDocument -> {
            UiText.resource(R.string.named_action_sending_file, name)
        }

        ChatAction.ChoosingSticker -> {
            UiText.resource(R.string.named_action_choosing_sticker, name)
        }

        ChatAction.ChoosingLocation -> {
            UiText.resource(R.string.named_action_choosing_location, name)
        }

        ChatAction.ChoosingContact -> {
            UiText.resource(R.string.named_action_choosing_contact, name)
        }

        ChatAction.PlayingGame -> {
            UiText.resource(R.string.named_action_playing_game, name)
        }

        is ChatAction.WatchingAnimations -> {
            UiText.resource(R.string.named_action_watching_animation, name, action.emoji)
        }
    }
