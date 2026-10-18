package dev.kyralabs.pingogram.feature.chat.ui.composer

import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType

// How the formatting of the composer's text follows its edits and the formatting the user applies. Offsets count UTF-16
// code units, as TextEntity's do; every function returns entities that are not empty, sorted by offset.

/**
 * These entities after the text's characters [start] until [end] were replaced by [length] new ones (an insertion when
 * [start] equals [end], a deletion when [length] is 0):
 *
 * - Text typed inside an entity joins it; text typed at either end of it does not, so formatting does not run on
 *   after the formatted word.
 * - Text that replaces a part of an entity takes its formatting: an autocorrected word stays bold.
 * - A replacement that only overlaps an entity cuts it back to the part that stays; one that covers it removes it.
 */
internal fun List<TextEntity>.afterReplace(
    start: Int,
    end: Int,
    length: Int,
): List<TextEntity> {
    val delta = length - (end - start)
    return mapNotNull { entity ->
        val entityStart = entity.offset
        val entityEnd = entity.offset + entity.length
        val (newStart, newEnd) =
            when {
                start == end && start <= entityStart -> entityStart + delta to entityEnd + delta
                start == end && start < entityEnd -> entityStart to entityEnd + delta
                end <= entityStart && start < end -> entityStart + delta to entityEnd + delta
                start >= entityEnd -> entityStart to entityEnd
                entityStart <= start && end <= entityEnd -> entityStart to entityEnd + delta
                start < entityStart && end < entityEnd -> start + length to entityEnd + delta
                entityStart < start -> entityStart to start
                else -> return@mapNotNull null
            }
        entity.copy(offset = newStart, length = newEnd - newStart).takeIf { it.length > 0 }
    }.sortedForText()
}

/**
 * These entities, made for [old], after the text became [new] without the composer seeing the edit (an undo, a
 * replaced text): the edit is taken to be the one replacement between the common start and end of both texts.
 */
internal fun List<TextEntity>.afterChange(
    old: String,
    new: String,
): List<TextEntity> {
    if (old == new) return this
    val prefix = old.commonPrefixWith(new).length
    val maxSuffix = minOf(old.length, new.length) - prefix
    val suffix = old.commonSuffixWith(new).length.coerceAtMost(maxSuffix)
    return afterReplace(prefix, old.length - suffix, new.length - prefix - suffix)
}

/**
 * Whether every character from [start] until [end] has an entity of [type] (any link, for a [TextEntityType.TextUrl]);
 * false when there is no character.
 */
internal fun List<TextEntity>.covers(
    type: TextEntityType,
    start: Int,
    end: Int,
): Boolean {
    // How far from start the entities reach without a gap; by offset, so one after a gap ends the run.
    var covered = start
    for (entity in filter { it.type.sameKindAs(type) }.sortedBy { it.offset }) {
        if (entity.offset <= covered) covered = maxOf(covered, entity.offset + entity.length)
    }
    return start < end && covered >= end
}

/**
 * These entities with [type] toggled from [start] until [end]: removed there when that part has it everywhere, else
 * applied to all of it, joined with the entities of the same type it touches. A link ([TextEntityType.TextUrl])
 * replaces any other link there instead, so a part of the text links to one address.
 */
internal fun List<TextEntity>.toggling(
    type: TextEntityType,
    start: Int,
    end: Int,
): List<TextEntity> =
    when {
        start >= end -> this
        type is TextEntityType.TextUrl -> removing(type, start, end).applying(type, start, end)
        covers(type, start, end) -> removing(type, start, end)
        else -> applying(type, start, end)
    }

/** These entities without any of [type]'s kind from [start] until [end]; the parts outside stay. */
internal fun List<TextEntity>.removing(
    type: TextEntityType,
    start: Int,
    end: Int,
): List<TextEntity> =
    flatMap { entity ->
        val entityEnd = entity.offset + entity.length
        if (!entity.type.sameKindAs(type) || entityEnd <= start || entity.offset >= end) {
            listOf(entity)
        } else {
            listOfNotNull(
                entity.copy(length = start - entity.offset).takeIf { it.length > 0 },
                entity.copy(offset = end, length = entityEnd - end).takeIf { it.length > 0 },
            )
        }
    }.sortedForText()

/** These entities with [type] from [start] until [end], merged with the equal entities it overlaps or touches. */
private fun List<TextEntity>.applying(
    type: TextEntityType,
    start: Int,
    end: Int,
): List<TextEntity> {
    val (joined, others) = partition { it.type == type && it.offset <= end && it.offset + it.length >= start }
    val newStart = joined.minOfOrNull { it.offset }?.coerceAtMost(start) ?: start
    val newEnd = joined.maxOfOrNull { it.offset + it.length }?.coerceAtLeast(end) ?: end
    return (others + TextEntity(newStart, newEnd - newStart, type)).sortedForText()
}

/** The link of the entity that covers [start] until [end] (the cursor, when they are equal); null if none does. */
internal fun List<TextEntity>.linkAt(
    start: Int,
    end: Int,
): String? =
    firstNotNullOfOrNull { entity ->
        val url = (entity.type as? TextEntityType.TextUrl)?.url
        url?.takeIf { entity.offset <= start && end <= entity.offset + entity.length }
    }

/** Both are of one kind: equal types, or links whatever their addresses. */
private fun TextEntityType.sameKindAs(other: TextEntityType): Boolean =
    this == other || (this is TextEntityType.TextUrl && other is TextEntityType.TextUrl)

/** By offset, then the longer first, so an outer entity comes before what it holds. */
private fun List<TextEntity>.sortedForText(): List<TextEntity> = sortedWith(compareBy({ it.offset }, { -it.length }))
