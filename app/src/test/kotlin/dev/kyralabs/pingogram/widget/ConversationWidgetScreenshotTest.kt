package dev.kyralabs.pingogram.widget

import android.app.Application
import android.appwidget.AppWidgetHostView
import android.content.Intent
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.glance.GlanceTheme
import androidx.glance.action.Action
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.glance.appwidget.action.actionStartActivity
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
 * Goldens of the Conversation widget ([ConversationWidgetContent], board 33) as the launcher shows it: 4 × 2 with three
 * messages, light, dark and right to left; 2 × 2 with two, and one at 200% font; and its other states.
 */
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class ConversationWidgetScreenshotTest {
    @Test
    fun wideLight() = capture(WIDE)

    @Test
    fun wideDark() = capture(WIDE, night = true)

    @Test
    fun wideRtl() = capture(WIDE, qualifiers = "+ldrtl")

    @Test
    fun small() = capture(SMALL)

    @Test
    fun smallFontScale200() = capture(SMALL, fontScale = 2f)

    @Test
    fun loading() = capture(SMALL, ConversationContent.Loading)

    @Test
    fun noChat() = capture(SMALL, ConversationContent.NoChat)

    @Test
    fun failed() = capture(SMALL, ConversationContent.Failed)

    @Test
    fun signedOut() = capture(SMALL, ConversationContent.SignedOut)

    @Test
    fun secretChat() = capture(SMALL, TRIP.copy(title = "Meera", messages = emptyList(), isSecret = true))

    @Suppress("LongParameterList") // What each golden varies.
    private fun capture(
        size: DpSize,
        content: ConversationContent = TRIP,
        night: Boolean = false,
        qualifiers: String? = null,
        fontScale: Float? = null,
    ) = runTest {
        if (night) RuntimeEnvironment.setQualifiers("+night")
        qualifiers?.let(RuntimeEnvironment::setQualifiers)
        fontScale?.let(RuntimeEnvironment::setFontScale)
        val context = RuntimeEnvironment.getApplication()
        val nothing: Action = actionStartActivity(Intent())
        val result =
            GlanceRemoteViews().compose(context, size) {
                val colors =
                    ColorProviders(
                        light = themeColorScheme(ThemePalette.Pingogram, dark = false, contrast = Contrast.Standard),
                        dark = themeColorScheme(ThemePalette.Pingogram, dark = true, contrast = Contrast.Standard),
                    )
                GlanceTheme(colors) { ConversationWidgetContent(content, onSetUp = nothing, onRetry = nothing) }
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
        /** A 4 × 2 and a 2 × 2 widget on a phone. */
        val WIDE = DpSize(330.dp, 200.dp)
        val SMALL = DpSize(170.dp, 200.dp)

        /** The board's chat. */
        val TRIP =
            ConversationContent.Chat(
                chatId = -100,
                slot = 0,
                title = "Trip \u2014 Coorg 2026",
                unreadCount = 9,
                isMuted = false,
                messages =
                    listOf(
                        WidgetMessage(1, "Meera", "Final plan: Saturday, 6:00 AM"),
                        WidgetMessage(2, "Rahul", "I\u2019ll bring the speaker \uD83D\uDD0A"),
                        WidgetMessage(3, "Aisha", "Can someone book the homestay?"),
                    ),
                canReply = true,
            )
    }
}
