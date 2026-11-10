package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.toImmutableList

/**
 * This text with the parts of some entities written anew: [replacement] gives an entity's new text, or null to keep
 * it. Every entity's offsets follow, so styles, links and mentions after a replaced part stay on their words; an
 * entity around a replaced part grows or shrinks with it, and one inside it covers the whole new part. Entities that
 * overlap a part replaced before them are kept as they are. Replacing nothing returns this text.
 */
fun FormattedText.replacingEntities(replacement: (TextEntity) -> String?): FormattedText {
    val parts =
        entities
            .sortedBy { it.offset }
            .mapNotNull { entity ->
                val inText = entity.offset >= 0 && entity.length > 0 && entity.offset + entity.length <= text.length
                if (!inText) return@mapNotNull null
                replacement(entity)?.let { Replaced(entity.offset, entity.offset + entity.length, it) }
            }.fold(emptyList<Replaced>()) { kept, part ->
                if (kept.lastOrNull()?.let { it.end > part.start } == true) kept else kept + part
            }
    if (parts.isEmpty()) return this
    val newText =
        buildString {
            var at = 0
            for (part in parts) {
                append(text, at, part.start)
                append(part.text)
                at = part.end
            }
            append(text, at, text.length)
        }
    val moved =
        entities.map { entity ->
            val start = parts.newStart(entity.offset)
            val end = parts.newEnd(entity.offset + entity.length)
            entity.copy(offset = start, length = (end - start).coerceAtLeast(0))
        }
    return FormattedText(newText, moved.toImmutableList())
}

/** The old text's [start] until [end], written as [text]. */
private class Replaced(
    val start: Int,
    val end: Int,
    val text: String,
) {
    val delta: Int get() = text.length - (end - start)
}

/** Where an entity starting at old [position] starts in the new text: inside a replaced part, at its start. */
private fun List<Replaced>.newStart(position: Int): Int {
    var shift = 0
    for (part in this) {
        when {
            position >= part.end -> shift += part.delta
            position > part.start -> return part.start + shift
            else -> break
        }
    }
    return position + shift
}

/** Where an entity ending at old [position] ends in the new text: inside a replaced part, at its new end. */
private fun List<Replaced>.newEnd(position: Int): Int {
    var shift = 0
    for (part in this) {
        when {
            position >= part.end -> shift += part.delta
            position > part.start -> return part.start + shift + part.text.length
            else -> break
        }
    }
    return position + shift
}
