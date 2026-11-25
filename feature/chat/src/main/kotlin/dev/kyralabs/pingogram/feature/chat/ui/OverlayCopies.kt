package dev.kyralabs.pingogram.feature.chat.ui

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * What one overlay stage of the conversation's state (a translation, the sponsored messages, a search or keyword match)
 * made of the items, kept from one state to the next. The stages run on every state the conversation emits, a typing
 * status's as a new message's: an entry made of the same item, the same object, with an equal overlay is the entry
 * made before, not a new copy; and a list of the same entries as the last one made is that list, so the message list
 * compares it at once. One per stage, run by one coroutine at a time ([OverlayStages]).
 */
internal class OverlayCopies {
    private var made = HashMap<Any, Made>()
    private var next = HashMap<Any, Made>()
    private var lastList: ImmutableList<ChatItem>? = null

    /** The entry [make] makes of [source] with [overlay], or the one it made of the same [source] and an equal one. */
    fun entry(
        key: Any,
        source: Any,
        overlay: Any?,
        make: () -> ChatItem,
    ): ChatItem {
        val kept = made[key]
        val entry = if (kept != null && kept.source === source && kept.overlay == overlay) kept.entry else make()
        next[key] = Made(source, overlay, entry)
        return entry
    }

    /** These entries, as the last list made where they are the same entries; ends the stage's run. */
    fun list(entries: List<ChatItem>): ImmutableList<ChatItem> {
        val previous = made
        made = next
        next = previous.apply { clear() }
        val last = lastList
        val same = last != null && last.size == entries.size && entries.indices.all { last[it] === entries[it] }
        val list = if (same) checkNotNull(last) else entries.toImmutableList()
        lastList = list
        return list
    }

    private class Made(
        val source: Any,
        val overlay: Any?,
        val entry: ChatItem,
    )
}
