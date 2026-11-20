package dev.kyralabs.pingogram.feature.profile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * The Stories tab before a profile's shared media tabs: whether it is [selected], [onSelect] to pick it (or, with
 * false, a media tab again), and its [items] for the grid.
 */
@Immutable
internal class StoriesTab(
    val selected: Boolean,
    val onSelect: (Boolean) -> Unit,
    val items: ProfileStoriesItems,
)

/**
 * The Stories tab of chat [chatId]'s profile where `:app` gives its [slot] (owner decision 2026-11-20); none for a null
 * chat, one that keeps no stories or is not read yet. Its selection survives configuration changes.
 */
@Composable
internal fun rememberStoriesTab(
    chatId: Long?,
    slot: ProfileStoriesSlot?,
): StoriesTab? {
    var selected by rememberSaveable { mutableStateOf(false) }
    if (slot == null || chatId == null) return null
    return StoriesTab(selected, onSelect = { selected = it }, items = slot(chatId, selected))
}
