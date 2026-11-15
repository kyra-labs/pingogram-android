package dev.kyralabs.pingogram.feature.chat.ui.transcription

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.feature.chat.ui.ChatEvent
import dev.kyralabs.pingogram.feature.chat.ui.ChatItem

/**
 * What a voice or round video bubble needs to show its transcript (`docs/DESIGN_SYSTEM.md` §6 "Voice message
 * transcription"): its message ([messageId]), the [transcript] the view model copied into it, whether the Transcribe
 * button shows ([showsButton], where the device can transcribe), and where its events go ([onEvent]).
 */
@Immutable
internal data class MessageTranscription(
    val messageId: Long,
    val transcript: BubbleTranscript?,
    val showsButton: Boolean,
    val onEvent: (ChatEvent) -> Unit,
)

/** [bubble]'s transcription, where it shows a button ([canTranscribe]) or a transcript; null otherwise. */
internal fun transcriptionOf(
    bubble: ChatItem.Bubble,
    canTranscribe: Boolean,
    onEvent: (ChatEvent) -> Unit,
): MessageTranscription? =
    if (canTranscribe || bubble.transcript != null) {
        MessageTranscription(bubble.id, bubble.transcript, canTranscribe, onEvent)
    } else {
        null
    }

/** Whether the bubble shows its text, or why there is none, below its audio; the button then hides it. */
internal val BubbleTranscript?.isOpen: Boolean
    get() = this != null && this != BubbleTranscript.Transcribing
