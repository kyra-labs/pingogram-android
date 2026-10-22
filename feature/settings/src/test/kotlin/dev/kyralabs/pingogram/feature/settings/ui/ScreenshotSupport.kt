package dev.kyralabs.pingogram.feature.settings.ui

import android.app.UiModeManager
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.unit.LayoutDirection
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.domain.model.ThemePalette
import dev.kyralabs.pingogram.core.ui.component.LoadingIndicatorDelay
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import org.junit.rules.ExternalResource
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** A compact phone in portrait (`docs/TESTING.md`). */
const val COMPACT = "w411dp-h891dp-xxhdpi"

/** An expanded window, e.g. a tablet or an unfolded foldable. */
const val EXPANDED = "w840dp-h891dp-xxhdpi"

/** A medium window (600–839 dp), e.g. a small tablet or a foldable in portrait, with one pane and the rail. */
const val MEDIUM = "w700dp-h891dp-xxhdpi"

/** The largest font scale screens must handle without clipping (`REQUIREMENTS.md` §6.3). */
const val FONT_SCALE_200 = 2f

/**
 * Declares [ComponentActivity] to Robolectric so a compose rule can launch it (ui-test-manifest is not a dependency),
 * without the platform action bar, which whole-screen captures would show.
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

/**
 * Shows [content] in [PingogramTheme] with the colors of [palette] (the brand's by default) in [themeMode], with
 * true-black surfaces if [amoled], and
 * captures the whole screen, dialogs included, as the golden `src/test/snapshots/<test class>.<name>.png`: once loading
 * has taken [LoadingIndicatorDelay], so a page that is still loading shows its delayed indicator.
 */
@OptIn(ExperimentalRoborazziApi::class)
fun AndroidComposeTestRule<*, *>.captureScreen(
    testClass: Class<*>,
    name: String,
    themeMode: ThemeMode,
    amoled: Boolean = false,
    palette: ThemePalette = ThemePalette.Pingogram,
    content: @Composable () -> Unit,
) {
    setContent {
        PingogramTheme(
            themeMode = themeMode,
            dynamicColor = false,
            palette = palette,
            amoled = amoled,
            content = content,
        )
    }
    waitForIdle()
    mainClock.advanceTimeBy(LoadingIndicatorDelay.inWholeMilliseconds)
    waitForIdle()
    captureScreenRoboImage("src/test/snapshots/${testClass.name}.$name.png")
}

/**
 * How a golden shows its screen besides light and dark: the other looks the Material 3 Expressive audit asks for
 * (`docs/DESIGN_SYSTEM.md` §12): true-black surfaces, high contrast and right to left. [Medium] is the light look for a
 * test that runs in a [MEDIUM] window. Brand colors, so goldens do not depend on a wallpaper.
 */
enum class ScreenshotVariant(
    val themeMode: ThemeMode,
    val amoled: Boolean = false,
    val highContrast: Boolean = false,
    val layoutDirection: LayoutDirection = LayoutDirection.Ltr,
) {
    Amoled(ThemeMode.Dark, amoled = true),
    HighContrast(ThemeMode.Light, highContrast = true),
    Rtl(ThemeMode.Light, layoutDirection = LayoutDirection.Rtl),
    Medium(ThemeMode.Light),
    ;

    /** The variant's part of a golden's name, e.g. `highContrast`. */
    val goldenName: String get() = name.replaceFirstChar { it.lowercase() }
}

/** [captureScreen] as [variant] asks: its theme, its system contrast and its layout direction. */
@OptIn(ExperimentalRoborazziApi::class)
fun AndroidComposeTestRule<*, *>.captureScreen(
    testClass: Class<*>,
    name: String,
    variant: ScreenshotVariant,
    palette: ThemePalette = ThemePalette.Pingogram,
    content: @Composable () -> Unit,
) {
    setContent {
        if (variant.highContrast) {
            // From Android 14 the theme follows the system's contrast setting rather than its parameter, so set that,
            // before the theme reads it.
            shadowOf(LocalContext.current.getSystemService(UiModeManager::class.java)).setContrast(1f)
        }
        PingogramTheme(
            themeMode = variant.themeMode,
            dynamicColor = false,
            palette = palette,
            amoled = variant.amoled,
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides variant.layoutDirection, content = content)
        }
    }
    waitForIdle()
    mainClock.advanceTimeBy(LoadingIndicatorDelay.inWholeMilliseconds)
    waitForIdle()
    captureScreenRoboImage("src/test/snapshots/${testClass.name}.$name.png")
}
