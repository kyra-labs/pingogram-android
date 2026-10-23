package dev.kyralabs.pingogram.feature.chat.ui.voice

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue

/**
 * Where the finger is while it holds the microphone, from where it pressed: how far toward the start (to cancel) and
 * how far up (to lock), in pixels, never back past the press. The record button moves with it and the recording bar
 * fades its hints, both reading it in the draw phase. Main thread only.
 */
@Stable
internal class RecordDrag {
    var towardStart by mutableFloatStateOf(0f)
    var up by mutableFloatStateOf(0f)

    fun reset() {
        towardStart = 0f
        up = 0f
    }
}
