package dev.kyralabs.pingogram.core.ui.transition

import android.os.Looper
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * [AnimatorScaleObserver] follows the animator duration scale while the app runs (`docs/DESIGN_SYSTEM.md` §5), through
 * the duration scale listener on Android 13+ and the setting before.
 */
@RunWith(RobolectricTestRunner::class)
class ReducedMotionTest {
    private val context = RuntimeEnvironment.getApplication()

    @After
    fun restoreScale() = setAnimatorDurationScale(1f)

    @Test
    fun `follows the animator duration scale through its listener`() = assertFollowsScale()

    @Test
    @Config(sdk = [32])
    fun `follows the animator duration scale setting before Android 13`() = assertFollowsScale()

    private fun assertFollowsScale() {
        val observer = AnimatorScaleObserver(context)
        assertFalse(observer.reduced.value)

        setAnimatorDurationScale(0f)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(observer.reduced.value)

        setAnimatorDurationScale(1f)
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(observer.reduced.value)
    }
}
