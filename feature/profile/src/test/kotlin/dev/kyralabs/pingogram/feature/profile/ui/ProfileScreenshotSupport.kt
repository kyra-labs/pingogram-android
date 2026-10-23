package dev.kyralabs.pingogram.feature.profile.ui

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

/** A medium window (600–839 dp), e.g. a small tablet or a foldable in portrait, with one pane and the rail. */
const val MEDIUM = "w700dp-h891dp-xxhdpi"

/** An expanded window (840 dp and wider), e.g. a tablet in landscape: the shared media beside the profile. */
const val EXPANDED = "w1000dp-h800dp-xhdpi"

/** Declares [ComponentActivity] to Robolectric, as ui-test-manifest would, for Compose rules that start it. */
class ComponentActivityRegistration : ExternalResource() {
    override fun before() {
        val application = RuntimeEnvironment.getApplication()
        shadowOf(application.packageManager)
            .addActivityIfNotPresent(ComponentName(application, ComponentActivity::class.java))
    }
}

/**
 * How a golden shows its screen: light and dark, and the other looks the Material 3 Expressive audit asks for
 * (`docs/DESIGN_SYSTEM.md` §12): true-black surfaces, high contrast and right to left. [Medium] is the light look for a
 * test that runs in a [MEDIUM] window. Brand colors, so goldens do not depend on a wallpaper.
 */
enum class ScreenshotVariant(
    val themeMode: ThemeMode,
    val amoled: Boolean = false,
    val highContrast: Boolean = false,
    val layoutDirection: LayoutDirection = LayoutDirection.Ltr,
) {
    Light(ThemeMode.Light),
    Dark(ThemeMode.Dark),
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
    PingogramTheme(themeMode = variant.themeMode, dynamicColor = false, amoled = variant.amoled) {
        CompositionLocalProvider(LocalLayoutDirection provides variant.layoutDirection, content = content)
    }
}
