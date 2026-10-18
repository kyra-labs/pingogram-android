package dev.kyralabs.pingogram.core.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import com.github.takahirom.roborazzi.captureRoboImage
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Goldens of [AvatarShowcase] with the brand schemes: placeholders, icons, photos, online dots and sizes. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AvatarScreenshotTest {
    @Test
    fun avatarsLight() = capture(ThemeMode.Light)

    @Test
    fun avatarsDark() = capture(ThemeMode.Dark)

    private fun capture(themeMode: ThemeMode) =
        captureRoboImage {
            // Inspection mode, as in previews: Coil shows the showcase's preview photo instead of loading from TDLib.
            CompositionLocalProvider(LocalInspectionMode provides true) {
                PingogramTheme(themeMode = themeMode, dynamicColor = false) {
                    Surface(color = MaterialTheme.colorScheme.background) { AvatarShowcase() }
                }
            }
        }
}
