package dev.kyralabs.pingogram.feature.auth.ui

import android.app.UiModeManager
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import org.junit.rules.ExternalResource
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** A compact phone in portrait (`docs/TESTING.md`). */
const val COMPACT = "w411dp-h891dp-xxhdpi"

/** An expanded window, e.g. a tablet or an unfolded foldable. */
const val EXPANDED = "w840dp-h891dp-xxhdpi"

/** A compact phone in landscape: an expanded width with a compact height. */
const val LANDSCAPE = "w891dp-h411dp-land-xxhdpi"

/**
 * Declares [ComponentActivity] to Robolectric so the compose rule can launch it, as in `:app`'s UI tests; the usual
 * `ui-test-manifest` would be one more dependency. Its theme has no action bar, so whole-screen captures show the
 * login screen only, as the app's edge-to-edge activity does.
 */
class ComponentActivityRegistration : ExternalResource() {
    override fun before() {
        val application = RuntimeEnvironment.getApplication()
        val packageManager = shadowOf(application.packageManager)
        val activity = packageManager.addActivityIfNotPresent(ComponentName(application, ComponentActivity::class.java))
        activity.theme = android.R.style.Theme_Material_Light_NoActionBar
        packageManager.addOrUpdateActivity(activity)
    }
}

/** Where a golden of [testClass] is recorded and verified: `src/test/snapshots`, as Roborazzi names them. */
fun goldenPath(
    testClass: Class<*>,
    name: String,
): String = "src/test/snapshots/${testClass.name}.$name.png"

/** A medium window (600–839 dp), e.g. a small tablet or a foldable in portrait, with one pane and the rail. */
const val MEDIUM = "w700dp-h891dp-xxhdpi"

/**
 * How a golden shows its screen: light and dark, and the other looks the Material 3 Expressive audit asks for
 * (`docs/DESIGN_SYSTEM.md` §12): true-black surfaces, high contrast and right to left. [Medium] is the light look for a
 * test that runs in a [MEDIUM] window. Brand colors, so goldens do not depend on a wallpaper, except [Dynamic]:
 * dynamic color from Robolectric's system palette.
 */
enum class ScreenshotVariant(
    val themeMode: ThemeMode,
    val dynamicColor: Boolean = false,
    val amoled: Boolean = false,
    val highContrast: Boolean = false,
    val layoutDirection: LayoutDirection = LayoutDirection.Ltr,
) {
    Light(ThemeMode.Light),
    Dark(ThemeMode.Dark),
    Dynamic(ThemeMode.Light, dynamicColor = true),
    Amoled(ThemeMode.Dark, amoled = true),
    HighContrast(ThemeMode.Light, highContrast = true),
    Rtl(ThemeMode.Light, layoutDirection = LayoutDirection.Rtl),
    Medium(ThemeMode.Light),
    ;

    /** The variant's part of a golden's name, e.g. `highContrast`. */
    val goldenName: String get() = name.replaceFirstChar { it.lowercase() }
}

/** Shows [content] in [PingogramTheme] as [variant] asks. */
@Composable
fun ScreenshotTheme(
    variant: ScreenshotVariant,
    content: @Composable () -> Unit,
) {
    if (variant.highContrast) {
        // From Android 14 the theme follows the system's contrast setting rather than its parameter, so set that,
        // before the theme reads it.
        shadowOf(LocalContext.current.getSystemService(UiModeManager::class.java)).setContrast(1f)
    }
    PingogramTheme(themeMode = variant.themeMode, dynamicColor = variant.dynamicColor, amoled = variant.amoled) {
        CompositionLocalProvider(LocalLayoutDirection provides variant.layoutDirection, content = content)
    }
}
