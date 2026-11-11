package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * A style Telegram's AI rewrites text in (td_api.tl `textCompositionStyle`): Telegram's own, or a custom one a user
 * created ([isCustom]) and others added by its link. [name] is what the AI and links use; [title] is in the app's
 * language; [example] shows the style on a short English text where Telegram gives one.
 */
data class TextCompositionStyle(
    val name: String,
    val title: String,
    val customEmojiId: Long = 0L,
    val isCustom: Boolean = false,
    val isCreator: Boolean = false,
    val installCount: Int = 0,
    val creatorUserId: Long = 0L,
    val example: TextCompositionExample? = null,
)

/** A text before and after a style was applied to it (td_api.tl `textCompositionStyleExample`). */
data class TextCompositionExample(
    val source: FormattedText,
    val result: FormattedText,
)

/**
 * A text Telegram's AI fixed (td_api.tl `fixedText`): the [text] to use, and [diff], the same text with what changed
 * marked, to show the user before they take it.
 */
data class FixedText(
    val text: FormattedText,
    val diff: DiffText = DiffText(text.text),
)

/** A text with its changes marked (td_api.tl `diffText`); the changes never overlap. */
data class DiffText(
    val text: String,
    val changes: ImmutableList<DiffChange> = persistentListOf(),
)

/** One change of a [DiffText], over [length] UTF-16 units from [offset]. */
data class DiffChange(
    val offset: Int,
    val length: Int,
    val kind: DiffKind,
)

/** What a [DiffChange] did to the text. */
sealed interface DiffKind {
    /** The part was added. */
    data object Insert : DiffKind

    /** The part was taken out (it stays in the diff's text, to show it struck through). */
    data object Delete : DiffKind

    /** The part replaced [oldText]. */
    data class Replace(
        val oldText: String,
    ) : DiffKind
}
