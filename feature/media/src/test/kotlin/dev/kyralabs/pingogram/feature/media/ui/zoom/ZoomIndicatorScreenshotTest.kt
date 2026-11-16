package dev.kyralabs.pingogram.feature.media.ui.zoom

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.github.takahirom.roborazzi.captureRoboImage
import dev.kyralabs.pingogram.core.ui.theme.PingogramDarkTheme
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.media.ui.COMPACT
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of the zoom's indicator (board 25): a 4:3 photo zoomed in to 2.5× and panned toward its top start, its level
 * over the mini-map and "Double-tap to fit"; at 200% font scale and right to left too.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ZoomIndicatorScreenshotTest {
    @Test
    fun zoomed() = capture()

    @Test
    @Config(fontScale = 2f)
    fun fontScale200() = capture()

    @Test
    fun rtl() = capture(LayoutDirection.Rtl)

    private fun capture(layoutDirection: LayoutDirection = LayoutDirection.Ltr) {
        val zoom =
            ZoomState().apply {
                bounds = ZoomBounds(container = Size(1000f, 2000f), content = Size(1000f, 750f))
                transform(centroid = Offset.Zero, scaleChange = 2.5f, panChange = Offset(600f, 200f))
            }
        captureRoboImage {
            PingogramTheme(dynamicColor = false) {
                PingogramDarkTheme {
                    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                        Box(
                            Modifier
                                // A photo stands behind it in the viewer: a mid tone here, so the pills show.
                                .background(MaterialTheme.colorScheme.tertiary)
                                .padding(MaterialTheme.spacing.large),
                        ) {
                            ZoomIndicator(zoom, visible = true)
                        }
                    }
                }
            }
        }
    }
}
