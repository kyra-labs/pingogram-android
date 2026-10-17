package dev.kyralabs.pingogram.core.ui.theme

import android.app.UiModeManager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureRoboImage
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Goldens of the theme tokens with the brand schemes. AMOLED and high contrast only change colors,
 * so only the color sheet is captured for them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ThemeShowcaseScreenshotTest {
    @After
    fun resetSystemContrast() = setSystemContrast(0f)

    @Test
    fun colorRolesLight() = capture(ThemeMode.Light) { ColorRolesShowcase() }

    @Test
    fun colorRolesDark() = capture(ThemeMode.Dark) { ColorRolesShowcase() }

    @Test
    fun colorRolesAmoled() = capture(ThemeMode.Dark, amoled = true) { ColorRolesShowcase() }

    // Robolectric runs Android 14+, where the theme follows the system contrast setting.
    @Test
    fun colorRolesHighContrast() {
        setSystemContrast(1f)
        capture(ThemeMode.Light) { ColorRolesShowcase() }
    }

    @Test
    fun typeScaleLight() = capture(ThemeMode.Light) { TypeScaleShowcase() }

    @Test
    fun typeScaleDark() = capture(ThemeMode.Dark) { TypeScaleShowcase() }

    @Test
    fun shapesLight() = capture(ThemeMode.Light) { ShapesShowcase() }

    @Test
    fun shapesDark() = capture(ThemeMode.Dark) { ShapesShowcase() }

    private fun setSystemContrast(level: Float) {
        val uiModeManager = RuntimeEnvironment.getApplication().getSystemService(UiModeManager::class.java)
        shadowOf(uiModeManager).setContrast(level)
    }

    private fun capture(
        themeMode: ThemeMode,
        amoled: Boolean = false,
        content: @Composable () -> Unit,
    ) = captureRoboImage {
        PingogramTheme(themeMode = themeMode, dynamicColor = false, amoled = amoled) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, content = content)
        }
    }
}
