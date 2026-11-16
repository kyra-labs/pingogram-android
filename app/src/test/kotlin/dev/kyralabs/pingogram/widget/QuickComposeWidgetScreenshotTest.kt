package dev.kyralabs.pingogram.widget

import android.app.Application
import android.appwidget.AppWidgetHostView
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.glance.material3.ColorProviders
import com.github.takahirom.roborazzi.captureRoboImage
import dev.kyralabs.pingogram.core.domain.model.Contrast
import dev.kyralabs.pingogram.core.domain.model.ThemePalette
import dev.kyralabs.pingogram.core.ui.theme.themeColorScheme
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

/**
 * Goldens of the Quick compose widget ([QuickComposeContent], board 33) as the launcher shows it: 2 × 1 with "Message"
 * and "Voice", light, dark and right to left, and 1 × 1 with "New".
 */
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class QuickComposeWidgetScreenshotTest {
    @Test
    fun wideLight() = capture(WIDE)

    @Test
    fun wideDark() = capture(WIDE, night = true)

    @Test
    fun wideRtl() = capture(WIDE, qualifiers = "+ldrtl")

    @Test
    fun small() = capture(SMALL)

    @Test
    fun smallDark() = capture(SMALL, night = true)

    private fun capture(
        size: DpSize,
        night: Boolean = false,
        qualifiers: String? = null,
    ) = runTest {
        if (night) RuntimeEnvironment.setQualifiers("+night")
        qualifiers?.let(RuntimeEnvironment::setQualifiers)
        val context = RuntimeEnvironment.getApplication()
        val result =
            GlanceRemoteViews().compose(context, size) {
                val colors =
                    ColorProviders(
                        light = themeColorScheme(ThemePalette.Pingogram, dark = false, contrast = Contrast.Standard),
                        dark = themeColorScheme(ThemePalette.Pingogram, dark = true, contrast = Contrast.Standard),
                    )
                GlanceTheme(colors) { QuickComposeContent() }
            }
        val host = AppWidgetHostView(context)
        val view = result.remoteViews.apply(context, host)
        val width = size.width.px(context)
        val height = size.height.px(context)
        host.addView(view, FrameLayout.LayoutParams(width, height))
        host.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        host.layout(0, 0, width, height)
        // Drawn into a bitmap: Roborazzi captures views only inside an activity, and a widget lives in the launcher.
        createBitmap(width, height).also { host.draw(Canvas(it)) }.captureRoboImage()
    }

    private fun Dp.px(context: Application) = (value * context.resources.displayMetrics.density).roundToInt()

    private companion object {
        /** A 2 × 1 and a 1 × 1 widget on a phone. */
        val WIDE = DpSize(200.dp, 90.dp)
        val SMALL = DpSize(90.dp, 90.dp)
    }
}
