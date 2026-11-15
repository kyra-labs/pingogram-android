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
 * Goldens of the unread counter widget ([UnreadCounterContent], board 33) as the launcher shows it, at a 2 × 1
 * widget's size: unread messages with mentions and avatars, light and dark, all read, and locked.
 */
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class UnreadCounterWidgetScreenshotTest {
    @Test
    fun unreadLight() = capture(unread())

    @Test
    fun unreadDark() = capture(unread(), night = true)

    @Test
    fun allRead() = capture(RecentChatsContent.Chats(emptyList()))

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
                GlanceTheme(colors) { UnreadCounterContent(content) }
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

    /** 47 unread messages, 3 mentions, three unread chats and a muted one, which shows no avatar. */
    private fun unread(): RecentChatsContent {
        val scheme = themeColorScheme(ThemePalette.Pingogram, dark = false, contrast = Contrast.Standard)
        val chat = { id: Long, unread: Int, muted: Boolean, color: Int ->
            WidgetChat(id, "Chat $id", "", unread, false, muted, avatar(color))
        }
        return RecentChatsContent.Chats(
            listOf(
                chat(1, 3, false, scheme.primary.toArgb()),
                chat(2, 12, true, scheme.error.toArgb()),
                chat(3, 30, false, scheme.tertiary.toArgb()),
                chat(4, 2, false, scheme.secondary.toArgb()),
                chat(5, 1, false, scheme.error.toArgb()),
            ),
            unreadMessages = 47,
            mentions = 3,
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
        /** A 2 × 1 widget on a phone. */
        val WIDTH = 200.dp
        val HEIGHT = 90.dp
        const val DENSITY = 3f
    }
}
