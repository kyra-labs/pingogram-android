package dev.kyralabs.pingogram.feature.scanner

import androidx.camera.core.InitializationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * The scanner's camera starting ([startedOrNull]): where CameraX cannot start, the scanner says the camera is
 * unavailable instead of failing.
 */
class ScannerCameraStartTest {
    @Test
    fun `a camera that starts is used`() =
        runTest {
            assertEquals("camera", startedOrNull { "camera" })
        }

    @Test
    fun `CameraX failing to initialize, or refusing in its state, leaves no camera`() =
        runTest {
            assertNull(startedOrNull<String> { throw InitializationException("no camera service") })
            assertNull(startedOrNull<String> { throw IllegalStateException("closed") })
        }
}
