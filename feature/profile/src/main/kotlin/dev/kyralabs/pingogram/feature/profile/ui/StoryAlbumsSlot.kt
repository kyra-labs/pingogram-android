package dev.kyralabs.pingogram.feature.profile.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A profile's story albums (boards 11a, 11b), which `:app` provides from `feature:media` (features share no code): the
 * section for chat [chatId] with [modifier], drawing nothing for a chat without albums.
 */
typealias StoryAlbumsSlot = @Composable (chatId: Long, modifier: Modifier) -> Unit
