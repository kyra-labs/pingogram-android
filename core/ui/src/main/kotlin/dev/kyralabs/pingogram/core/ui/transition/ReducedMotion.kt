package dev.kyralabs.pingogram.core.ui.transition

import android.animation.ValueAnimator
import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

/**
 * Whether the system asks for reduced motion: animations are off (an animator duration scale of 0, which Compose
 * applies to every animation by ending it at once). Content that moves in ways that carry no information, such as a
 * container transform or a shape morph, steps aside then (`docs/DESIGN_SYSTEM.md` §5).
 *
 * It follows the setting while the app runs: turning animations off or on recomposes whatever read it
 * ([AnimatorScaleObserver]). Previews never reduce motion.
 */
@Composable
fun isReducedMotion(): Boolean {
    if (LocalInspectionMode.current) return false
    return ReducedMotion.state(LocalContext.current).value
}

/**
 * [spec], or a snap while the system asks for reduced motion ([isReducedMotion]): for shape morphs, which then jump to
 * their end instead of morphing (`docs/DESIGN_SYSTEM.md` §5).
 */
@Composable
fun <T> snapWhenReducedMotion(spec: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> =
    if (isReducedMotion()) snap() else spec

/** The process's one [AnimatorScaleObserver], created when first read. */
internal object ReducedMotion {
    @Volatile
    private var observer: AnimatorScaleObserver? = null

    fun state(context: Context): State<Boolean> {
        val current =
            observer ?: synchronized(this) { observer ?: AnimatorScaleObserver(context).also { observer = it } }
        return current.reduced
    }
}

/**
 * Follows the system's animator duration scale: [reduced] is true while it is 0 (animations off). From Android 13 on it
 * listens to [ValueAnimator]'s duration scale, which also sees Battery Saver turning animations off; before, it
 * observes [Settings.Global.ANIMATOR_DURATION_SCALE], as Compose does for its own animations. Either is registered once
 * and kept for the process's lifetime, and changes reach [reduced] on the main thread.
 */
internal class AnimatorScaleObserver(
    context: Context,
) {
    private val state = mutableStateOf(!ValueAnimator.areAnimatorsEnabled())
    val reduced: State<Boolean> get() = state

    private val main = Handler(Looper.getMainLooper())

    // Never read, held only: ValueAnimator keeps its listeners through weak references.
    @Suppress("UnusedPrivateProperty")
    private val listener: Any =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listenToDurationScale()
        } else {
            observeSetting(context.applicationContext.contentResolver)
        }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun listenToDurationScale(): ValueAnimator.DurationScaleChangeListener {
        // Called on the thread that changed the scale, a binder thread when the system did.
        val listener =
            ValueAnimator.DurationScaleChangeListener { scale ->
                val reduced = scale == 0f
                if (Looper.myLooper() == main.looper) state.value = reduced else main.post { state.value = reduced }
            }
        ValueAnimator.registerDurationScaleChangeListener(listener)
        return listener
    }

    private fun observeSetting(resolver: ContentResolver): ContentObserver {
        val observer =
            object : ContentObserver(main) {
                override fun onChange(selfChange: Boolean) {
                    state.value =
                        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
                }
            }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        return observer
    }
}
