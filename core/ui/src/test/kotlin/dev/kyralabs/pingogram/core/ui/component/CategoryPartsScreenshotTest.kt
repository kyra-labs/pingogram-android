package dev.kyralabs.pingogram.core.ui.component

import android.app.UiModeManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.LayoutDirection
import com.github.takahirom.roborazzi.captureRoboImage
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.domain.model.ThemePalette
import dev.kyralabs.pingogram.core.ui.R
import dev.kyralabs.pingogram.core.ui.theme.CategoryHue
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.spacing
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The settings pages' shared parts (`docs/DESIGN_SYSTEM.md` §13.10): the round back button, the six category
 * colors' icon circles, one disabled, and switches on and off, enabled and disabled, on the page's and the rows' colors, in light, dark, AMOLED black, high contrast,
 * right to left, and with the pink, teal and monochrome themes, which the colors are harmonized toward.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class CategoryPartsScreenshotTest {
    @Test
    fun light() = capture(ThemeMode.Light)

    @Test
    fun dark() = capture(ThemeMode.Dark)

    @Test
    fun amoled() = capture(ThemeMode.Dark, amoled = true)

    @Test
    fun highContrast() {
        val uiModeManager = RuntimeEnvironment.getApplication().getSystemService(UiModeManager::class.java)
        shadowOf(uiModeManager).setContrast(1f)
        capture(ThemeMode.Light)
    }

    @Test
    fun highContrastDark() {
        val uiModeManager = RuntimeEnvironment.getApplication().getSystemService(UiModeManager::class.java)
        shadowOf(uiModeManager).setContrast(1f)
        capture(ThemeMode.Dark)
    }

    @Test
    fun rtl() = capture(ThemeMode.Light, layoutDirection = LayoutDirection.Rtl)

    @Test
    fun pinkDark() = capture(ThemeMode.Dark, palette = ThemePalette.Pink)

    @Test
    fun tealLight() = capture(ThemeMode.Light, palette = ThemePalette.Teal)

    @Test
    fun monochromeLight() = capture(ThemeMode.Light, palette = ThemePalette.Monochrome)

    private fun capture(
        themeMode: ThemeMode,
        amoled: Boolean = false,
        palette: ThemePalette = ThemePalette.Pingogram,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    ) = captureRoboImage {
        CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
            PingogramTheme(themeMode = themeMode, dynamicColor = false, palette = palette, amoled = amoled) {
                Column {
                    // The page's color, then the rows' (as settingsPageColor and settingsRowColors pick them).
                    val colors = MaterialTheme.colorScheme
                    Parts(if (amoled) colors.surface else colors.surfaceContainerHigh)
                    Parts(if (amoled) colors.surfaceContainer else colors.surface)
                }
            }
        }
    }
}

@Composable
private fun Parts(background: Color) {
    val spacing = MaterialTheme.spacing
    Surface(color = background) {
        Column(Modifier.padding(spacing.large), verticalArrangement = Arrangement.spacedBy(spacing.small)) {
            CategoryRow()
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.large)) {
                CheckSwitch(checked = true)
                CheckSwitch(checked = false)
                CheckSwitch(checked = true, enabled = false)
                CheckSwitch(checked = false, enabled = false)
            }
        }
    }
}

/** The back button, then the six circles with their filled symbols, and a disabled one. */
@Composable
private fun CategoryRow() {
    val categories = MaterialTheme.pingogramColors.categories
    Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall)) {
        RoundBackButton(label = "Back", onClick = {})
        CategoryHue.entries.forEachIndexed { index, hue ->
            CategoryIconBadge(painterResource(Icons[index]), categories[hue])
        }
        CategoryIconBadge(painterResource(R.drawable.ic_person_filled), categories[CategoryHue.Pink], enabled = false)
    }
}

private val Icons =
    listOf(
        R.drawable.ic_chat_filled,
        R.drawable.ic_bookmark_filled,
        R.drawable.ic_call_filled,
        R.drawable.ic_image_filled,
        R.drawable.ic_settings_filled,
        R.drawable.ic_description_filled,
    )
