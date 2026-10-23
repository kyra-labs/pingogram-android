package dev.kyralabs.pingogram.feature.chat.ui.translation

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.domain.model.FormattedText

/**
 * How a bubble shows a translation (message translation, `REQUIREMENTS.md` FR-C3): the translation in place of its
 * text, or that one is on its way. Bubbles that show their own text have none.
 */
@Immutable
sealed interface BubbleTranslation {
    /** Telegram is translating the message: the bubble keeps its text, its meta says "Translating…". */
    data object Translating : BubbleTranslation

    /**
     * The bubble shows [text], Telegram's translation, with "Translated from Spanish" ([fromLanguage], an IETF BCP 47
     * tag the device detected) or "Translated" in its meta.
     */
    data class Shown(
        val text: FormattedText,
        val fromLanguage: String?,
    ) : BubbleTranslation
}

/**
 * The translate bar at the top of the conversation (`docs/DESIGN_SYSTEM.md` §6 "Translate bar"), where Telegram
 * suggests translating the chat and its messages are in a language the user does not read: "Translate to English"
 * ([toLanguage]) while [isOn] is false, "Show original" while the messages on screen are translated. [fromLanguage] is
 * the chat's language as the device detected it; null once unknown.
 */
@Immutable
data class TranslateBar(
    val toLanguage: String,
    val fromLanguage: String?,
    val isOn: Boolean,
)
