package dev.kyralabs.pingogram.feature.chat.ui.videonote

import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDisplay
import org.robolectric.shadows.ShadowLooper

/** [DisplayRotation]: the rotation now, each change of it, and nothing once closed. */
@RunWith(RobolectricTestRunner::class)
class DisplayRotationTest {
    private val rotations = mutableListOf<Int>()

    @Test
    fun `tells the rotation now and as the display turns`() {
        val following = DisplayRotation(RuntimeEnvironment.getApplication()).follow { rotations += it }

        turn(Surface.ROTATION_90)
        turn(Surface.ROTATION_270)
        following.close()

        assertEquals(listOf(Surface.ROTATION_0, Surface.ROTATION_90, Surface.ROTATION_270), rotations)
    }

    @Test
    fun `tells nothing once closed`() {
        DisplayRotation(RuntimeEnvironment.getApplication()).follow { rotations += it }.close()

        turn(Surface.ROTATION_90)

        assertEquals(listOf(Surface.ROTATION_0), rotations)
    }

    /** Turns the default display to [rotation]; Robolectric announces it to the display's listeners. */
    private fun turn(rotation: Int) {
        shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(rotation)
        ShadowLooper.idleMainLooper()
    }
}
