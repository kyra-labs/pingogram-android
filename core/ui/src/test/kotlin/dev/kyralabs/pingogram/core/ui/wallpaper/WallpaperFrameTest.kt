package dev.kyralabs.pingogram.core.ui.wallpaper

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import dev.kyralabs.pingogram.core.domain.model.Wallpaper
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.math.abs

/**
 * Layers in a [WallpaperFrame] draw one wallpaper across it: a layer lower in the frame shows the part of the
 * wallpaper the frame has there, as a conversation's sheet below its top bar does; without a frame it starts over.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp-mdpi")
class WallpaperFrameTest {
    // Declares the activity to Robolectric, as ui-test-manifest would.
    @get:Rule(order = 0)
    val activityRegistration =
        object : ExternalResource() {
            override fun before() {
                val application = RuntimeEnvironment.getApplication()
                shadowOf(application.packageManager)
                    .addActivityIfNotPresent(ComponentName(application, ComponentActivity::class.java))
            }
        }

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** A red-to-blue fill over a frame two boxes tall, and a layer in its lower box; the lower layer's pixels. */
    private fun lowerLayer(framed: Boolean): PixelMap {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                val frame = rememberWallpaperFrame()
                Box(Modifier.size(SIDE.dp, (2 * SIDE).dp).wallpaperFrame(frame)) {
                    ChatWallpaperLayer(GRADIENT, 0, Modifier.fillMaxSize(), frame)
                    Box(Modifier.offset(y = SIDE.dp).size(SIDE.dp, SIDE.dp)) {
                        ChatWallpaperLayer(GRADIENT, 0, Modifier.fillMaxSize().testTag(LOWER), frame.takeIf { framed })
                    }
                }
            }
        }
        composeRule.waitForIdle()
        return composeRule.onNodeWithTag(LOWER).captureToImage().toPixelMap()
    }

    @Test
    fun `a lower layer in the frame shows the frame's wallpaper where it sits`() {
        val lower = lowerLayer(framed = true)

        // Halfway down the frame, the gradient is halfway from red to blue; at its bottom, blue.
        assertClose(Color(HALF, 0f, HALF), lower[SIDE / 2, 0])
        assertClose(Color.Blue, lower[SIDE / 2, SIDE - 1])
    }

    @Test
    fun `without a frame a layer draws the wallpaper from its own top`() {
        val lower = lowerLayer(framed = false)

        assertClose(Color.Red, lower[SIDE / 2, 0])
        assertClose(Color.Blue, lower[SIDE / 2, SIDE - 1])
    }

    private fun assertClose(
        expected: Color,
        actual: Color,
    ) {
        val distance =
            abs(expected.red - actual.red) + abs(expected.green - actual.green) + abs(expected.blue - actual.blue)
        assertEquals("$expected vs $actual", 0f, distance, TOLERANCE)
    }

    private companion object {
        const val SIDE = 100
        const val LOWER = "lower"
        const val TOLERANCE = 0.06f
        const val HALF = 0.5f
        val GRADIENT = Wallpaper.Fill(1, listOf(0xFF0000, 0x0000FF))
    }
}
