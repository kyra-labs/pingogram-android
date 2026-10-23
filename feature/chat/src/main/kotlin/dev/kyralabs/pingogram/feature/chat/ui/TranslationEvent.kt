package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.common.result.DomainError

/** What the user does with message translation (`REQUIREMENTS.md` FR-C3), sent to [ChatViewModel]. */
sealed interface TranslationEvent : ChatEvent {
    /** "Translate" in a message's actions: its bubble shows Telegram's translation. */
    data class Translate(
        val messageId: Long,
    ) : TranslationEvent

    /** "Show original" in a message's actions or its bubble's custom action: its bubble shows its own text again. */
    data class ShowOriginal(
        val messageId: Long,
    ) : TranslationEvent

    /** The translate bar was tapped: the messages on screen are translated as they show, or no longer. */
    data object ToggleChat : TranslationEvent

    /** The translate bar's close button: it closes for this chat on this device, and the chat's translation ends. */
    data object CloseBar : TranslationEvent
}

/** One-off effects of message translation: snackbars. */
sealed interface TranslationEffect : ChatEffect {
    /** Telegram did not translate: the error's text, or that translation failed where Telegram gave no reason. */
    data class Failed(
        val error: DomainError,
    ) : TranslationEffect

    /** The translate bar was closed: a snackbar says single messages still translate from their actions. */
    data object BarHidden : TranslationEffect
}
