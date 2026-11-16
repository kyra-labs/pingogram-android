package dev.kyralabs.pingogram.feature.chat.ui.voice

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import dev.kyralabs.pingogram.core.ui.gesture.SettlingOffset
import kotlinx.coroutines.CoroutineScope

/**
 * Where the finger is while it holds the microphone, from where it pressed: how far toward the start (to cancel) and
 * how far up (to lock), in pixels, never back past the press. The record button moves with it and the recording bar
 * fades its hints, both reading it in the draw phase. Let go ([reset]: a release, a cancel, a lock), they spring back
 * on [settle] in [scope], the fast spatial spring (`docs/DESIGN_SYSTEM.md` §5.1, Q-17), and a new press takes them
 * where the spring has them; without a [scope] (previews) they snap back. Main thread only.
 */
@Stable
internal class RecordDrag(
    private val scope: CoroutineScope? = null,
    private val settle: AnimationSpec<Float>? = null,
) {
    private val towardStartOffset = SettlingOffset()
    private val upOffset = SettlingOffset()

    var towardStart: Float
        get() = towardStartOffset.value
        set(value) = towardStartOffset.snapTo(value)

    var up: Float
        get() = upOffset.value
        set(value) = upOffset.snapTo(value)

    fun reset() {
        if (scope == null || settle == null) {
            towardStartOffset.snapTo(0f)
            upOffset.snapTo(0f)
        } else {
            towardStartOffset.settle(scope, 0f, settle)
            upOffset.settle(scope, 0f, settle)
        }
    }
}

/** The composer's [RecordDrag], springing back on the motion scheme's fast spatial spring. */
@Composable
internal fun rememberRecordDrag(): RecordDrag {
    val scope = rememberCoroutineScope()
    val motionScheme = MaterialTheme.motionScheme
    return remember(scope, motionScheme) { RecordDrag(scope, motionScheme.fastSpatialSpec()) }
}
