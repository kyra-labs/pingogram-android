package dev.kyralabs.pingogram.feature.chat.ui.listen

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.domain.repository.PlaybackSpeed

/**
 * The player pill of Listen to posts while it reads: post [position] (from 1) of [count], paused or not, at [speed].
 */
@Immutable
data class ListenPlayer(
    val position: Int,
    val count: Int,
    val isPaused: Boolean,
    val speed: PlaybackSpeed,
) {
    /** Whether a post follows the one read now. */
    val hasNext: Boolean get() = position < count
}
