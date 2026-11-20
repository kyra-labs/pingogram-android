package dev.kyralabs.pingogram.feature.profile.ui

import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import dev.kyralabs.pingogram.core.ui.component.ItemMotion

/** The items of a profile's Stories tab for its grid, [margin] in from the grid's sides, moving on [motion]. */
typealias ProfileStoriesItems = LazyGridScope.(margin: Dp, motion: ItemMotion) -> Unit

/**
 * A profile's Stories tab (owner decision 2026-11-20), which `:app` provides from `feature:media` (features share no
 * code): its items for chat [chatId], read once the tab is [active].
 */
typealias ProfileStoriesSlot = @Composable (chatId: Long, active: Boolean) -> ProfileStoriesItems
