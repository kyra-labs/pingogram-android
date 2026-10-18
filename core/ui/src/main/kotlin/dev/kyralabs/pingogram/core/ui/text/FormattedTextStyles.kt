package dev.kyralabs.pingogram.core.ui.text

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import dev.kyralabs.pingogram.core.domain.model.FormattedText

/**
 * The theme's look of the entities of a [FormattedText] (`REQUIREMENTS.md` FR-C3, `docs/DESIGN_SYSTEM.md` §2.2), for
 * [toAnnotatedString]. Colors are roles of the surface the text is on, so a bubble passes its own: code and spoilers
 * need a tone that stands out from the bubble's container. Equal styles are equal, so they can key a cache.
 *
 * @property linkColor links, underlined as well, so they do not rely on color alone (`docs/DESIGN_SYSTEM.md` §9).
 * @property mentionColor mentions, hashtags, cashtags, bot commands and media timestamps.
 * @property codeBackground behind inline code and code blocks, which are monospace.
 * @property spoilerColor a hidden spoiler is a block of this color: its text in the same color on it.
 * @property quoteColor block quotes, which are italic too.
 * @property boldWeight bold text: the weight the text style's font family holds for emphasis
 *   (`docs/DESIGN_SYSTEM.md` §3.2), so it is never synthesized.
 */
@Immutable
data class FormattedTextStyles(
    val linkColor: Color,
    val mentionColor: Color,
    val codeBackground: Color,
    val spoilerColor: Color,
    val quoteColor: Color,
    val boldWeight: FontWeight,
)

/**
 * [FormattedTextStyles] from the theme: links and mentions in `primary`, quotes in `onSurfaceVariant`, bold at the
 * emphasized weight of `bodyLarge`, and the given [codeBackground] and [spoilerColor] for the surface the text is on.
 */
@Composable
fun rememberFormattedTextStyles(
    codeBackground: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    spoilerColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
): FormattedTextStyles {
    val colors = MaterialTheme.colorScheme
    val boldWeight = MaterialTheme.typography.bodyLargeEmphasized.fontWeight ?: FontWeight.Bold
    return remember(colors, boldWeight, codeBackground, spoilerColor) {
        FormattedTextStyles(
            linkColor = colors.primary,
            mentionColor = colors.primary,
            codeBackground = codeBackground,
            spoilerColor = spoilerColor,
            quoteColor = colors.onSurfaceVariant,
            boldWeight = boldWeight,
        )
    }
}
