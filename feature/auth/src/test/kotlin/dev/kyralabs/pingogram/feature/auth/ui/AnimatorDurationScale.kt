package dev.kyralabs.pingogram.feature.auth.ui

import android.animation.ValueAnimator
import android.provider.Settings
import org.robolectric.RuntimeEnvironment
import org.robolectric.util.reflector.Direct
import org.robolectric.util.reflector.ForType
import org.robolectric.util.reflector.Reflector.reflector
import org.robolectric.util.reflector.Static

/**
 * Turns animations off (0) or on (1) as the system does, as `core:ui`'s test helper of the same name: the platform's
 * [ValueAnimator] duration scale, which tells its listeners (Android 13+), and the setting, which tells its observers.
 */
internal fun setAnimatorDurationScale(scale: Float) {
    reflector(ValueAnimatorDirect::class.java).setDurationScale(scale)
    val resolver = RuntimeEnvironment.getApplication().contentResolver
    Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
    resolver.notifyChange(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), null)
}

@ForType(ValueAnimator::class)
private interface ValueAnimatorDirect {
    @Static
    @Direct
    fun setDurationScale(scale: Float)
}
