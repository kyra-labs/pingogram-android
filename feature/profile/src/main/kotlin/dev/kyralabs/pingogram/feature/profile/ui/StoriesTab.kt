package dev.kyralabs.pingogram.feature.profile.ui

import androidx.compose.runtime.Immutable

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
