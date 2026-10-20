package dev.kyralabs.pingogram

import android.animation.ValueAnimator
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.material3.MotionScheme
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.core.splashscreen.SplashScreenViewProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The splash screen's exit (`docs/DESIGN_SYSTEM.md` §8): its icon grows on the expressive motion scheme's default
 * spatial spring while the whole splash screen fades out on its default effects spring, over the app's first frame;
 * then it is removed. No frame of the app waits for it.
 *
 * Material's springs outside composition: Compose's animation runtime on [AndroidUiDispatcher.Main], which brings the
 * frame clock, with the system's animator duration scale ([AnimatorDurationScale]) as Compose applies it to its own
 * animations, so with animations off the splash screen goes at once.
 */
internal fun SplashScreenViewProvider.animateExit(scope: CoroutineScope) {
    scope.launch(AndroidUiDispatcher.Main + AnimatorDurationScale) {
        try {
            val motion = MotionScheme.expressive()
            val icon = iconView
            val growing =
                launch {
                    Animatable(1f).animateTo(ICON_EXIT_SCALE, motion.defaultSpatialSpec()) {
                        icon.scaleX = value
                        icon.scaleY = value
                    }
                }
            Animatable(1f).animateTo(0f, motion.defaultEffectsSpec()) { view.alpha = value }
            // Invisible now: the spatial spring's settling would only keep the view above the app.
            growing.cancel()
        } finally {
            remove()
        }
    }
}

/**
 * How much the icon grows as the splash screen fades out: a literal, as Material's tokens have no splash screen exit
 * (durations and curves come from the motion scheme above).
 */
private const val ICON_EXIT_SCALE = 1.2f

/**
 * The system's animator duration scale, read when an animation starts, as Compose's own [MotionDurationScale] is.
 * Before Android 13 only whether animations are off is public.
 */
private object AnimatorDurationScale : MotionDurationScale {
    override val scaleFactor: Float
        get() =
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> ValueAnimator.getDurationScale()
                ValueAnimator.areAnimatorsEnabled() -> 1f
                else -> 0f
            }
}
