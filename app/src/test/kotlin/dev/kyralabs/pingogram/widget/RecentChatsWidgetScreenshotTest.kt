package dev.kyralabs.pingogram.widget

import android.app.Application
import android.appwidget.AppWidgetHostView
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.widget.FrameLayout
import androidx.compose.ui.graphics.toArgb
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
 * Goldens of the home screen widget of recent chats ([RecentChatsWidgetContent]) as the launcher shows it: Glance
 * composes it into RemoteViews, which are applied and drawn at a 3 × 3 widget's size, in the brand's light and dark
 * colors and in another color theme, with chats, signed out and locked (`docs/TESTING.md`).
 */
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class RecentChatsWidgetScreenshotTest {
    @Test
    fun chatsLight() = capture(chats())

    @Test
    fun chatsDark() = capture(chats(), night = true)

    @Test
    fun chatsTeal() = capture(chats(), palette = ThemePalette.Teal)

    @Test
    fun signedOut() = capture(RecentChatsContent.SignedOut)

    @Test
    fun locked() = capture(RecentChatsContent.Locked, night = true)

    private fun capture(
        content: RecentChatsContent,
        night: Boolean = false,
        palette: ThemePalette = ThemePalette.Pingogram,
    ) = runTest {
        if (night) RuntimeEnvironment.setQualifiers("+night")
        val context = RuntimeEnvironment.getApplication()
        val result =
            GlanceRemoteViews().compose(context, DpSize(WIDTH, HEIGHT)) {
                val colors =
                    ColorProviders(
                        light = themeColorScheme(palette, dark = false, contrast = Contrast.Standard),
                        dark = themeColorScheme(palette, dark = true, contrast = Contrast.Standard),
                    )
                GlanceTheme(colors) { RecentChatsWidgetContent(content) }
            }
        // The launcher's host view: Android sets a list's items only inside one.
        val host = AppWidgetHostView(context)
        val view = result.remoteViews.apply(context, host)
        val density = context.resources.displayMetrics.density
        val width = (WIDTH.value * density).roundToInt()
        val height = (HEIGHT.value * density).roundToInt()
        host.addView(view, FrameLayout.LayoutParams(width, height))
        host.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        host.layout(0, 0, width, height)
        // Drawn into a bitmap: Roborazzi captures views only inside an activity, and a widget lives in the launcher.
        createBitmap(width, height).also { host.draw(Canvas(it)) }.captureRoboImage()
    }

    /** Four chats: unread, muted with a mention, read, and a long title and message. */
    private fun chats(): RecentChatsContent {
        val scheme = themeColorScheme(ThemePalette.Pingogram, dark = false, contrast = Contrast.Standard)
        return RecentChatsContent.Chats(
            listOf(
                WidgetChat(1, "Ada Lovelace", "The engine is ready", 3, false, false, avatar(scheme.primary.toArgb())),
                WidgetChat(
                    2,
                    "Climbing club",
                    "Grace: Sunday at nine?",
                    12,
                    true,
                    true,
                    avatar(scheme.tertiary.toArgb()),
                ),
                WidgetChat(3, "Saved Messages", "Photo", 0, false, false, avatar(scheme.secondary.toArgb())),
                WidgetChat(
                    4,
                    "A group with a very long name that does not fit",
                    "And a message that is far too long to fit on one line of the widget",
                    0,
                    false,
                    false,
                    avatar(scheme.primaryContainer.toArgb()),
                ),
            ),
        )
    }

    /** A round avatar of one [color], 40 dp at the test's density. */
    private fun avatar(color: Int): Bitmap {
        val size = (RecentChatsWidgetUpdater.AVATAR_SIZE_DP * DENSITY).roundToInt()
        return createBitmap(size, size).also { bitmap ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
            Canvas(bitmap).drawCircle(size / 2f, size / 2f, size / 2f, paint)
        }
    }

    private companion object {
        /** A 3 × 3 widget on a phone. */
        val WIDTH = 300.dp
        val HEIGHT = 300.dp
        const val DENSITY = 3f
    }
}
