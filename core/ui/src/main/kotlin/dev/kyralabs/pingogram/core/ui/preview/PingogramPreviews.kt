package dev.kyralabs.pingogram.core.ui.preview

import android.content.res.Configuration
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.tooling.preview.PreviewWrapperProvider
import dev.kyralabs.pingogram.core.domain.model.Contrast
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme

/**
 * Light, dark and 200% font scale previews (`docs/DESIGN_SYSTEM.md` §12, `REQUIREMENTS.md` §6.3).
 * Wrap the previewed content in [PingogramPreview].
 */
@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Preview(name = "Font scale 200%", fontScale = 2f)
annotation class PingogramPreviews

/**
 * The other variants of the per-screen audit (`docs/DESIGN_SYSTEM.md` §12): AMOLED, high contrast, right to left and
 * a 700 dp (medium) window. Add it beside [PingogramPreviews] on a screen's preview; the content must be wrapped in
 * [PingogramPreview], which applies the AMOLED and contrast variants.
 */
@PingogramAmoledPreview
@PingogramHighContrastPreview
@Preview(name = "RTL", locale = "ar")
@Preview(name = "Width 700 dp", widthDp = MEDIUM_WIDTH_DP)
annotation class PingogramVariantPreviews

/**
 * The dark theme with AMOLED black. A preview's configuration has no setting for it, so [AmoledPreviewWrapper] tells
 * [PingogramPreview]; a tool that ignores the wrapper shows the plain dark theme.
 */
@Preview(name = "AMOLED", uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@PreviewWrapper(AmoledPreviewWrapper::class)
annotation class PingogramAmoledPreview

/**
 * The high contrast scheme, through [HighContrastPreviewWrapper] as for [PingogramAmoledPreview]. On API 33, as from
 * Android 14 the theme follows the system's contrast setting, which a preview cannot set.
 */
@Preview(name = "High contrast", apiLevel = Build.VERSION_CODES.TIRAMISU)
@PreviewWrapper(HighContrastPreviewWrapper::class)
annotation class PingogramHighContrastPreview

/** A medium window between the compact phone and the expanded list-detail layout (`docs/DESIGN_SYSTEM.md` §7). */
private const val MEDIUM_WIDTH_DP = 700

/** What [PingogramPreview] shows besides light and dark: set by the preview wrappers of the variants. */
private enum class PreviewThemeVariant { Default, Amoled, HighContrast }

private val LocalPreviewThemeVariant = staticCompositionLocalOf { PreviewThemeVariant.Default }

/** Shows [PingogramAmoledPreview] with AMOLED black. */
class AmoledPreviewWrapper : PreviewWrapperProvider {
    @Composable
    override fun Wrap(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalPreviewThemeVariant provides PreviewThemeVariant.Amoled, content = content)
    }
}

/** Shows [PingogramHighContrastPreview] with the high contrast scheme. */
class HighContrastPreviewWrapper : PreviewWrapperProvider {
    @Composable
    override fun Wrap(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalPreviewThemeVariant provides PreviewThemeVariant.HighContrast, content = content)
    }
}

/**
 * [PingogramTheme] on the theme's background for previews. Brand colors by default so previews do
 * not depend on the preview wallpaper; the light or dark theme follows the preview's `uiMode`, AMOLED black and the
 * high contrast scheme the variants of [PingogramVariantPreviews].
 */
@Composable
fun PingogramPreview(
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val variant = LocalPreviewThemeVariant.current
    PingogramTheme(
        dynamicColor = dynamicColor,
        contrast = if (variant == PreviewThemeVariant.HighContrast) Contrast.High else Contrast.Standard,
        amoled = variant == PreviewThemeVariant.Amoled,
    ) {
        Surface(color = MaterialTheme.colorScheme.background, content = content)
    }
}
