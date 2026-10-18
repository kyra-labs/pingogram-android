package dev.kyralabs.pingogram.feature.chat.ui.composer

import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import kotlinx.collections.immutable.toImmutableList

// The composer's text in a SavedStateHandle, which keeps only Bundle types: each entity as "offset|length|kind" and,
// for a kind with one, "|argument" (a link's address, a code block's language, an id).

/**
 * The entities of this text as strings for a `SavedStateHandle`. The kinds a sender may set are kept (the ones TDLib
 * finds in the text itself, and dates, are found again or shown plain).
 */
internal fun FormattedText.savedEntities(): ArrayList<String> =
    entities.mapNotNullTo(ArrayList()) { entity ->
        entity.type.savedKind()?.let { (kind, argument) ->
            listOfNotNull(entity.offset, entity.length, kind, argument).joinToString(SEPARATOR)
        }
    }

/** The text [savedEntities] made the entity strings of; null for no saved [text]. */
internal fun restoredText(
    text: String?,
    entities: List<String>?,
): FormattedText? =
    text?.let {
        val restored = entities.orEmpty().mapNotNull { saved -> saved.restoredEntity(text.length) }
        FormattedText(text, restored.toImmutableList())
    }

/** The kinds without an argument, by their saved name. */
private val PLAIN_KINDS: Map<String, TextEntityType> =
    mapOf(
        "bold" to TextEntityType.Bold,
        "italic" to TextEntityType.Italic,
        "underline" to TextEntityType.Underline,
        "strikethrough" to TextEntityType.Strikethrough,
        "spoiler" to TextEntityType.Spoiler,
        "code" to TextEntityType.Code,
        "pre" to TextEntityType.Pre,
        "quote" to TextEntityType.BlockQuote,
        "expandablequote" to TextEntityType.ExpandableBlockQuote,
    )

/** The saved name of this kind, and its argument; null for a kind that is not saved. */
private fun TextEntityType.savedKind(): Pair<String, String?>? =
    PLAIN_KINDS.entries.firstOrNull { it.value == this }?.let { it.key to null }
        ?: when (this) {
            is TextEntityType.PreCode -> "precode" to language
            is TextEntityType.TextUrl -> "link" to url
            is TextEntityType.MentionName -> "mention" to userId.toString()
            is TextEntityType.CustomEmoji -> "emoji" to customEmojiId.toString()
            else -> null
        }

/** The kind saved as [kind] with [argument]; null for one that cannot be read. */
private fun restoredKind(
    kind: String,
    argument: String?,
): TextEntityType? =
    PLAIN_KINDS[kind]
        ?: when (kind) {
            "precode" -> TextEntityType.PreCode(argument.orEmpty())
            "link" -> argument?.let(TextEntityType::TextUrl)
            "mention" -> argument?.toLongOrNull()?.let(TextEntityType::MentionName)
            "emoji" -> argument?.toLongOrNull()?.let(TextEntityType::CustomEmoji)
            else -> null
        }

/** The entity this string describes, if it lies within a text of [textLength]; null for anything else. */
private fun String.restoredEntity(textLength: Int): TextEntity? {
    // A link's address may hold the separator: it is the rest of the string.
    val parts = split(SEPARATOR, limit = PARTS)
    val offset = parts.getOrNull(0)?.toIntOrNull()?.takeIf { it in 0..textLength }
    val length = parts.getOrNull(1)?.toIntOrNull()?.takeIf { offset != null && it in 1..textLength - offset }
    val type = parts.getOrNull(2)?.let { restoredKind(it, parts.getOrNull(PARTS - 1)) }
    return if (offset != null && length != null && type != null) TextEntity(offset, length, type) else null
}

private const val SEPARATOR = "|"

/** Offset, length, kind and argument. */
private const val PARTS = 4
