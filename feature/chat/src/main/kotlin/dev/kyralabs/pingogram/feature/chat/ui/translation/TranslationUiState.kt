package dev.kyralabs.pingogram.feature.chat.ui.translation

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.domain.model.FormattedText

/**
 * How a bubble shows a translation (message translation, `REQUIREMENTS.md` FR-C3) or Telegram's summary of it
 * (`ChatSummaries`): the translation or summary in place of its text, or that one is on its way. Bubbles that show
 * their own text have none.
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

    /** Telegram is summarizing the message: the bubble keeps its text, its meta says "Summarizing…". */
    data object Summarizing : BubbleTranslation

    /** The bubble shows [text], Telegram's summary of the message, with "Summary by Telegram" in its meta. */
    data class Summary(
        val text: FormattedText,
    ) : BubbleTranslation
}

/** The text the bubble shows in place of its own; null while it waits for one. */
internal val BubbleTranslation.shownText: FormattedText?
    get() =
        when (this) {
            is BubbleTranslation.Shown -> text
            is BubbleTranslation.Summary -> text
            BubbleTranslation.Translating, BubbleTranslation.Summarizing -> null
        }

/** Whether Telegram is still working on it. */
internal val BubbleTranslation.isPending: Boolean
    get() = this == BubbleTranslation.Translating || this == BubbleTranslation.Summarizing

/** Whether it is a summary, or one on its way. */
internal val BubbleTranslation.isSummary: Boolean
    get() = this is BubbleTranslation.Summary || this == BubbleTranslation.Summarizing

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
