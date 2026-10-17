package dev.kyralabs.pingogram.ui

import android.app.Application
import com.github.takahirom.roborazzi.captureRoboImage
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The shell on the Chats tab: a short navigation bar on a compact phone, a collapsed wide navigation rail
 * on an expanded window (`docs/TESTING.md`). A plain [Application] keeps app start-up work out of UI tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = COMPACT)
class PingogramAppScreenshotTest {
    @Test
    fun compactLight() = capture(ThemeMode.Light)

    @Test
    fun compactDark() = capture(ThemeMode.Dark)

    @Test
    @Config(qualifiers = EXPANDED)
    fun expandedLight() = capture(ThemeMode.Light)

    @Test
    @Config(qualifiers = EXPANDED)
    fun expandedDark() = capture(ThemeMode.Dark)

    private fun capture(themeMode: ThemeMode) =
        captureRoboImage {
            PingogramTheme(themeMode = themeMode, dynamicColor = false) { PingogramApp() }
        }
}

private const val COMPACT = "w411dp-h891dp-xxhdpi"
private const val EXPANDED = "w840dp-h891dp-xxhdpi"
