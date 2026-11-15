package dev.kyralabs.pingogram.feature.chat.ui.transcription

import androidx.compose.runtime.Immutable

/**
 * What a voice or round video message's bubble shows of its transcript (`docs/DESIGN_SYSTEM.md` §6 "Voice message
 * transcription"), made on the device: that it is being made, the text, or why there is none. Bubbles whose text is not
 * shown have none, and show the Transcribe button where the device can transcribe (`ChatUiState.canTranscribe`).
 */
@Immutable
sealed interface BubbleTranscript {
    /** The recognizer is at work: tonal lines and "Transcribing on this phone…". */
    data object Transcribing : BubbleTranscript

    /**
     * The transcript: [text] (empty where no words were heard), made automatically ([isAutomatic]); the first one made
     * on this device says "On this phone" ([showsNotice]); a long one shows whole once [isExpanded].
     */
    data class Shown(
        val text: String,
        val isAutomatic: Boolean = false,
        val showsNotice: Boolean = false,
        val isExpanded: Boolean = false,
    ) : BubbleTranscript

    /** No speech language for it is ready on the device: a link to Android's language settings. */
    data object LanguageUnavailable : BubbleTranscript

    /** It could not be made: "Try again". */
    data object Failed : BubbleTranscript
}
