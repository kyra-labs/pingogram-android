package dev.kyralabs.pingogram.feature.media.ui

import dev.kyralabs.pingogram.core.domain.model.ChatMediaFilter
import dev.kyralabs.pingogram.core.domain.model.ChatMediaPage
import dev.kyralabs.pingogram.core.domain.model.Message
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.toPersistentList

/**
 * The part of a chat's media of [filter] that the viewer has loaded, for [MediaViewerViewModel]: [messages] oldest
 * first, as the pager shows them, without gaps or duplicates, and what is known of the rest. Pure, so paging and
 * positions are tested without coroutines.
 *
 * - **Pages** merge in by id, the newer copy of a message winning. Messages the viewer cannot show (a photo without
 *   sizes) are left out, but the next page still starts past them ([olderFrom], [newerFrom]). A side's end is reached
 *   when a page says so, or brings nothing beyond what was loaded: asking again would bring the same.
 * - **Positions** ([count]): from the oldest end once it is reached; else from the total and [newerCount], the media
 *   newer than the loaded ones: none once the newest end is reached, else what TDLib's position of a loaded message
 *   ([withPosition]) leaves, less the newer pages loaded since. The total is TDLib's estimate until both ends are
 *   reached, then the loaded messages themselves.
 * - **Deletions** ([without]) drop messages and lower the total.
 */
internal data class MediaWindow(
    val filter: ChatMediaFilter,
    val messages: PersistentList<Message>,
    /** The message the next older page starts before: the oldest one any page brought. */
    val olderFrom: Long,
    /** The message the next newer page starts after: the newest one any page brought. */
    val newerFrom: Long,
    val reachedOldest: Boolean,
    val reachedNewest: Boolean,
    /** About how many of the chat's messages match [filter]; null while unknown. */
    val totalCount: Int?,
    /** How many of the chat's messages matching [filter] are newer than the newest of [messages]; null if unknown. */
    val newerCount: Int?,
) {
    /** The index of message [messageId] in [messages], -1 if it is not there. */
    fun indexOf(messageId: Long): Int = messages.indexOfFirst { it.id == messageId }

    /** With an older [page] merged in. */
    fun withOlder(page: ChatMediaPage): MediaWindow {
        val broughtOlder = page.messages.any { it.id < olderFrom }
        return merged(page).copy(reachedOldest = reachedOldest || page.reachedOldest || !broughtOlder)
    }

    /** With a newer [page] merged in; the media it adds after the newest loaded message are no longer newer. */
    fun withNewer(page: ChatMediaPage): MediaWindow {
        val merged = merged(page)
        val added = page.messages.count { it.id > newerFrom }
        val reached = reachedNewest || page.reachedNewest || added == 0
        return merged.copy(
            reachedNewest = reached,
            newerCount = if (reached) 0 else newerCount?.let { (it - added).coerceAtLeast(0) },
        )
    }

    /**
     * With the position of loaded message [messageId] among the chat's media, counted from the newest (1), as TDLib
     * tells it: the media newer than the loaded ones follow from it.
     */
    fun withPosition(
        messageId: Long,
        positionFromNewest: Int,
    ): MediaWindow {
        val index = indexOf(messageId)
        if (index < 0 || reachedNewest) return this
        val loadedNewer = messages.lastIndex - index
        return copy(newerCount = (positionFromNewest - 1 - loadedNewer).coerceAtLeast(0))
    }

    /** Without the deleted messages [ids]; the total drops by those it held. */
    fun without(ids: Set<Long>): MediaWindow {
        val kept = messages.removingAll { it.id in ids }
        val removed = messages.size - kept.size
        if (removed == 0) return this
        return copy(messages = kept, totalCount = totalCount?.let { (it - removed).coerceAtLeast(kept.size) })
    }

    /** Where [messages] are among the chat's media of [filter]; null while that is not known. */
    fun count(): MediaCount? {
        val estimate = if (reachedOldest && reachedNewest) messages.size else totalCount
        val total = estimate?.coerceAtLeast(messages.size)?.takeIf { it > 0 }
        val firstNumber =
            when {
                total == null -> null
                reachedOldest -> 1
                newerCount != null -> total - newerCount - messages.lastIndex
                else -> null
            }
        return if (total != null && firstNumber != null) MediaCount(firstNumber, total) else null
    }

    private fun merged(page: ChatMediaPage): MediaWindow {
        // The page's copy last, so it wins: it is the newer one.
        val byId = (messages + page.messages.shown()).associateBy { it.id }
        return copy(
            messages = byId.values.sortedBy { it.id }.toPersistentList(),
            olderFrom = minOf(olderFrom, page.messages.minOfOrNull { it.id } ?: olderFrom),
            newerFrom = maxOf(newerFrom, page.messages.maxOfOrNull { it.id } ?: newerFrom),
            totalCount = page.totalCount ?: totalCount,
        )
    }

    companion object {
        /** The window of the first [page], the one around the opened message, which must hold some message. */
        fun of(page: ChatMediaPage) =
            MediaWindow(
                filter = page.filter,
                messages =
                    page.messages
                        .shown()
                        .sortedBy { it.id }
                        .toPersistentList(),
                olderFrom = page.messages.minOf { it.id },
                newerFrom = page.messages.maxOf { it.id },
                reachedOldest = page.reachedOldest,
                reachedNewest = page.reachedNewest,
                totalCount = page.totalCount,
                newerCount = if (page.reachedNewest) 0 else null,
            )
    }
}

/** The messages the viewer can show. */
private fun List<Message>.shown(): List<Message> = filter { it.content.toViewerMedia() != null }
