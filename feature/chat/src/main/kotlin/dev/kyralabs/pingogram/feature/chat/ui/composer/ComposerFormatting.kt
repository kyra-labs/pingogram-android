package dev.kyralabs.pingogram.feature.chat.ui.composer

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
import dev.kyralabs.pingogram.feature.chat.R
import kotlinx.collections.immutable.toImmutableList
import dev.kyralabs.pingogram.core.ui.R as UiR

/** A formatting the user can apply to selected text, with its name and icon in the selection menu. */
internal enum class TextFormat(
    val type: TextEntityType,
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
) {
    Bold(TextEntityType.Bold, R.string.composer_format_bold, UiR.drawable.ic_format_bold),
    Italic(TextEntityType.Italic, R.string.composer_format_italic, UiR.drawable.ic_format_italic),
    Underline(TextEntityType.Underline, R.string.composer_format_underline, UiR.drawable.ic_format_underlined),
    Strikethrough(
        TextEntityType.Strikethrough,
        R.string.composer_format_strikethrough,
        UiR.drawable.ic_format_strikethrough,
    ),
    Monospace(TextEntityType.Code, R.string.composer_format_monospace, UiR.drawable.ic_code),
    Spoiler(TextEntityType.Spoiler, R.string.composer_format_spoiler, UiR.drawable.ic_visibility_off),
}

/**
 * Draws the composer's formatting as messages show it (`toAnnotatedString`, in the theme's [FormattedTextStyles]):
 * bold, italic, underline and strikethrough, monospace on a tonal background, links in `primary` and underlined. A
 * spoiler stays readable for its author, on `tertiaryContainer`. The text itself is untouched, so the cursor and the
 * keyboard see it as typed; the entities are read as snapshot state, so a toggled format shows at once.
 */
@Composable
internal fun rememberFormattingTransformation(text: ComposerText): OutputTransformation {
    val styles = rememberFormattedTextStyles()
    val colors = MaterialTheme.colorScheme
    val spoiler =
        remember(colors) { SpanStyle(color = colors.onTertiaryContainer, background = colors.tertiaryContainer) }
    return remember(text, styles, spoiler) { FormattingTransformation(text, styles, spoiler) }
}

private class FormattingTransformation(
    private val text: ComposerText,
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

/** The formatting actions' labels, resolved for the menu and screen readers, which are built outside composition. */
internal class FormattingLabels(
    val formats: Map<TextFormat, String>,
    val link: String,
)

@Composable
internal fun rememberFormattingLabels(): FormattingLabels {
    val resources = LocalResources.current
    return remember(resources) {
        FormattingLabels(
            formats = TextFormat.entries.associateWith { resources.getString(it.label) },
            link = resources.getString(R.string.composer_format_link),
        )
    }
}

/**
 * The formatting actions after the text field's own (cut, copy, paste) in its selection menu, while text is selected:
 * each format toggles on the selection, and "Link" asks for an address ([onLink]).
 */
internal fun TextContextMenuBuilderScope.formattingItems(
    text: ComposerText,
    labels: FormattingLabels,
    onLink: (TextRange) -> Unit,
) {
    val selection = text.state.selection
    if (selection.collapsed) return
    separator()
    for (format in TextFormat.entries) {
        item(key = format, label = labels.formats.getValue(format), leadingIcon = format.icon) {
            text.toggle(format.type, selection)
            close()
        }
    }
    item(key = LINK_ITEM, label = labels.link, leadingIcon = UiR.drawable.ic_link) {
        onLink(selection)
        close()
    }
}

/**
 * The same actions for screen readers, which reach them through the field's actions menu rather than the selection
 * menu (`docs/DESIGN_SYSTEM.md` §9).
 */
internal fun formattingActions(
    text: ComposerText,
    labels: FormattingLabels,
    onLink: (TextRange) -> Unit,
): List<CustomAccessibilityAction> =
    TextFormat.entries.map { format ->
        CustomAccessibilityAction(labels.formats.getValue(format)) {
            text.toggle(format.type)
            true
        }
    } +
        CustomAccessibilityAction(labels.link) {
            onLink(text.state.selection)
            true
        }

/**
 * Keyboard shortcuts for formatting on a hardware keyboard, as in text editors: Ctrl+B, Ctrl+I, Ctrl+U, and Ctrl+K for
 * a link. True if [event] was one and text is selected, so the field does not handle it as well.
 */
internal fun formattingShortcut(
    event: KeyEvent,
    text: ComposerText,
    onLink: (TextRange) -> Unit,
): Boolean {
    val selection = text.state.selection
    val isCtrlKey = event.type == KeyEventType.KeyDown && event.isCtrlPressed && !event.isShiftPressed
    val format =
        when (event.key) {
            Key.B -> TextEntityType.Bold
            Key.I -> TextEntityType.Italic
            Key.U -> TextEntityType.Underline
            else -> null
        }
    val isLink = event.key == Key.K
    val handled = isCtrlKey && !selection.collapsed && (format != null || isLink)
    if (handled) {
        if (format != null) text.toggle(format, selection) else onLink(selection)
    }
    return handled
}

private const val LINK_ITEM = "link"
