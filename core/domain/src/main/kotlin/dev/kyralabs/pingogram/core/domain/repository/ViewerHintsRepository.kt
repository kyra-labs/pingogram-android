package dev.kyralabs.pingogram.core.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * The media viewer's one-time hints, kept on this phone (board 25): whether "Tap to show controls" has shown, the first
 * time the user hid the viewer's controls.
 */
interface ViewerHintsRepository {
    /** Whether the controls' hint has shown, then every change. Settings that cannot be read say it has not. */
    val controlsHintShown: Flow<Boolean>

    /** The controls' hint has shown: it never shows again on this phone. */
    suspend fun setControlsHintShown()
}
