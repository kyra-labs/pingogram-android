package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.FixedText
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.TextCompositionStyle
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.StateFlow

/**
 * Telegram's AI text editor (td_api.tl `fixTextWithAi`, `composeTextWithAi`): the text leaves the device for
 * Telegram's AI only on the user's tap, never for a secret chat, and nothing reaches a chat until the user sends it.
 * Telegram limits how often an account may ask without Telegram Premium: past that, requests fail with
 * `DomainError.AiEditLimitReached`, which the app shows as information, never an offer.
 */
interface AiTextRepository {
    /** The styles the user can rewrite in, Telegram's and the custom ones they added, in Telegram's order. */
    val styles: StateFlow<ImmutableList<TextCompositionStyle>>

    /** The language codes Telegram translates into ("de", "zh-TW"), the same as message translation's. */
    val languages: ImmutableList<String>

    /** [text] with its spelling, grammar and punctuation fixed, and what changed. */
    suspend fun fix(text: FormattedText): Outcome<FixedText>

    /**
     * [text] rewritten: in the style named [styleName] (null keeps its own), translated into [translateTo] (a code of
     * [languages]; null keeps its language), with emoji added where [addEmoji].
     */
    suspend fun compose(
        text: FormattedText,
        styleName: String?,
        translateTo: String?,
        addEmoji: Boolean,
    ): Outcome<FormattedText>

    /** The custom style named [name], from a shared link (`searchTextCompositionStyle`). */
    suspend fun findStyle(name: String): Outcome<TextCompositionStyle>

    /**
     * Adds the custom style [name] to the user's styles; fails with `DomainError.TooManyTextStyles` past
     * [addedStyleLimit].
     */
    suspend fun addStyle(name: String): Outcome<Unit>

    /** Removes the custom style [name] from the user's styles. */
    suspend fun removeStyle(name: String): Outcome<Unit>

    /** How many custom styles the account may add (TDLib's option `added_text_composition_style_count_max`). */
    suspend fun addedStyleLimit(): Int?
}
