package dev.kyralabs.pingogram.feature.chats.ui

import androidx.lifecycle.SavedStateHandle
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The ids of the selected chats (`REQUIREMENTS.md` FR-L8) in the [SavedStateHandle], so selection mode survives
 * rotation and process death. A chat that leaves the list (archived or deleted elsewhere) stays in it until the next
 * change, which keeps only the chats shown; until then it is not shown as selected ([selectionOf]). Main thread only.
 */
internal class SelectedChats(
    private val savedStateHandle: SavedStateHandle,
) {
    /** The selected ids, then every change. */
    val ids: Flow<Set<Long>> = savedStateHandle.getStateFlow(KEY, LongArray(0)).map { it.toSet() }

    private val current: Set<Long>
        get() = savedStateHandle.get<LongArray>(KEY)?.toSet().orEmpty()

    /** Whether any of the chats [shown] is selected: selection mode. */
    fun isActive(shown: Set<Long>): Boolean = current.any { it in shown }

    /** Selects [chatId], or deselects it, which ends selection mode if it was the last; keeps only chats [shown]. */
    fun toggle(
        chatId: Long,
        shown: Set<Long>,
    ) {
        val ids = current.filterTo(LinkedHashSet()) { it in shown }
        if (!ids.add(chatId)) ids.remove(chatId)
        savedStateHandle[KEY] = ids.toLongArray()
    }

    /** Ends selection mode. */
    fun clear() {
        savedStateHandle[KEY] = LongArray(0)
    }

    private companion object {
        const val KEY = "chatList.selection"
    }
}

/** Selection mode for the selected [ids] among the chats of [content]; null if none of them is shown. */
internal fun selectionOf(
    content: ChatListContent,
    ids: Set<Long>,
): ChatSelection? {
    val rows = if (ids.isEmpty()) emptyList() else (content as? ChatListContent.Content)?.items?.filter { it.id in ids }
    return if (rows.isNullOrEmpty()) {
        null
    } else {
        ChatSelection(
            rows.map { it.id }.toImmutableSet(),
            selectionActions(rows),
        )
    }
}
