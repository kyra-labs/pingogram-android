package dev.kyralabs.pingogram.feature.media.storypost

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.text.contextmenu.builder.TextContextMenuBuilderScope
import androidx.compose.foundation.text.contextmenu.builder.item
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import dev.kyralabs.pingogram.core.ui.text.FormattedTextStyles
import dev.kyralabs.pingogram.core.ui.text.rememberFormattedTextStyles
import dev.kyralabs.pingogram.core.ui.text.toAnnotatedString
import dev.kyralabs.pingogram.feature.media.R
import kotlinx.collections.immutable.toImmutableList
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * Draws a story caption's formatting (copied from feature:chat's composer) as messages show it
 * (`toAnnotatedString`, in the theme's [FormattedTextStyles]):
 * bold, italic, underline and strikethrough, monospace on a tonal background, links in `primary` and underlined. A
 * spoiler stays readable for its author, on `tertiaryContainer`. The text itself is untouched, so the cursor and the
 * keyboard see it as typed; the entities are read as snapshot state, so a toggled format shows at once.
 */
@Composable
internal fun rememberFormattingTransformation(text: CaptionText): OutputTransformation {
    val styles = rememberFormattedTextStyles()
    val colors = MaterialTheme.colorScheme
    val spoiler =
        remember(colors) { SpanStyle(color = colors.onTertiaryContainer, background = colors.tertiaryContainer) }
    return remember(text, styles, spoiler) { FormattingTransformation(text, styles, spoiler) }
}

private class FormattingTransformation(
    private val text: CaptionText,
    private val styles: FormattedTextStyles,
    private val spoiler: SpanStyle,
) : OutputTransformation {
    override fun TextFieldBuffer.transformOutput() {
        val current = asCharSequence().toString()
        val entities = text.entitiesFor(current)
        if (entities.isEmpty()) return
        val formatted =
            FormattedText(
                current,
                entities.toImmutableList(),
            ).toAnnotatedString(styles, spoilersRevealed = true)
        for (span in formatted.spanStyles) addStyle(span.item, span.start, span.end)
        for (link in formatted.getLinkAnnotations(0, formatted.length)) {
            link.item.styles
                ?.style
                ?.let { addStyle(it, link.start, link.end) }
        }
        for (entity in entities) {
            if (entity.type == TextEntityType.Spoiler) addStyle(spoiler, entity.offset, entity.offset + entity.length)
        }
    }
}
