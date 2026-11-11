package dev.kyralabs.pingogram.feature.profile.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A profile's music (boards 13a, 13b), which `:app` provides from `feature:media` (features share no code): the songs
 * of user [userId] ([isSelf]: the user's own profile, where songs are added) with [modifier], drawing nothing for
 * another user without songs.
 */
typealias ProfileMusicSlot = @Composable (userId: Long, isSelf: Boolean, modifier: Modifier) -> Unit
