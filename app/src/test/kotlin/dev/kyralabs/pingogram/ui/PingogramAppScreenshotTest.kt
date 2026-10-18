package dev.kyralabs.pingogram.ui

import android.app.Application
import com.github.takahirom.roborazzi.captureRoboImage
import dev.kyralabs.pingogram.MainActivityUiState
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The signed-in shell on the Chats tab: the floating navigation bar on a compact phone, also narrow and at 200% font
 * scale, and a collapsed wide navigation rail on an expanded window (`docs/TESTING.md`). A plain [Application] keeps
 * app start-up work out of UI tests; the Chats tab needs no Hilt view model.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = COMPACT)
class PingogramAppScreenshotTest {
    @Test
    fun compactLight() = capture(ThemeMode.Light)

    @Test
    fun compactDark() = capture(ThemeMode.Dark)

    @Test
    @Config(qualifiers = NARROW)
    fun narrowLargeFontLight() {
        RuntimeEnvironment.setFontScale(LARGE_FONT_SCALE)
        capture(ThemeMode.Light)
    }

    @Test
    @Config(qualifiers = EXPANDED)
    fun expandedLight() = capture(ThemeMode.Light)

    @Test
    @Config(qualifiers = EXPANDED)
    fun expandedDark() = capture(ThemeMode.Dark)

    private fun capture(themeMode: ThemeMode) =
        captureRoboImage {
            PingogramTheme(themeMode = themeMode, dynamicColor = false) {
                PingogramApp(uiState = MainActivityUiState.SignedIn)
            }
        }
}

private const val COMPACT = "w411dp-h891dp-xxhdpi"
private const val NARROW = "w360dp-h780dp-xxhdpi"
private const val LARGE_FONT_SCALE = 2f
private const val EXPANDED = "w840dp-h891dp-xxhdpi"
