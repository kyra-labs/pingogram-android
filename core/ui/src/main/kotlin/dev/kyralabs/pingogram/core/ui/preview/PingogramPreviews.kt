package dev.kyralabs.pingogram.core.ui.preview

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
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
 * [PingogramTheme] on the theme's background for previews. Brand colors by default so previews do
 * not depend on the preview wallpaper; the light or dark theme follows the preview's `uiMode`.
 */
@Composable
fun PingogramPreview(
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    PingogramTheme(dynamicColor = dynamicColor) {
        Surface(color = MaterialTheme.colorScheme.background, content = content)
    }
}
