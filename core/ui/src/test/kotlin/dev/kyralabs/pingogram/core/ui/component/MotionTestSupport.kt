package dev.kyralabs.pingogram.core.ui.component

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.junit4.ComposeTestRule
import org.junit.rules.ExternalResource
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** Declares [ComponentActivity] to Robolectric, as ui-test-manifest would, so a compose rule can launch it. */
class ComponentActivityRegistration : ExternalResource() {
    override fun before() {
        val application = RuntimeEnvironment.getApplication()
        shadowOf(application.packageManager)
            .addActivityIfNotPresent(ComponentName(application, ComponentActivity::class.java))
    }
}

/**
 * The system's animator duration scale, as Compose applies it to every animation: pass it as the compose rule's
 * effect context, and set [scaleFactor] to 0 for reduced motion (animations off), which ends every spring in its
 * next frame.
 */
class TestDurationScale : MotionDurationScale {
    override var scaleFactor = 1f
}

/**
 * Advances the paused clock a frame at a time until [settled] holds, at most [limit] frames, and returns how many
 * frames it took.
 */
internal fun ComposeTestRule.framesUntil(
    limit: Int = 200,
    settled: () -> Boolean,
): Int {
    // Applies what the test changed first, so the first frame sees it.
    waitForIdle()
    var frames = 0
    while (frames < limit) {
        mainClock.advanceTimeByFrame()
        waitForIdle()
        frames++
        if (settled()) return frames
    }
    return frames
}
