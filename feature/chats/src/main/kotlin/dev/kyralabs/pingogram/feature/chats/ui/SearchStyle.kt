package dev.kyralabs.pingogram.feature.chats.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ChipShapes
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarColors
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import dev.kyralabs.pingogram.core.ui.text.MatchHighlight
import dev.kyralabs.pingogram.core.ui.text.TextBlocksLayout
import dev.kyralabs.pingogram.core.ui.text.textBlocks
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.core.ui.R as UiR

// Global search's parts (`docs/DESIGN_SYSTEM.md` §13.4): the header's block and colors, the
// chips, the chat rows' chevron and the found messages' highlighted text.

/**
 * The search bar's colors (`docs/DESIGN_SYSTEM.md` §13.4): the field's pill on `surfaceContainerHighest`, the block
 * around it on `surfaceContainerLow`.
 */
@Composable
internal fun searchBarColors(): SearchBarColors {
    val scheme = MaterialTheme.colorScheme
    return SearchBarDefaults.colors(
        containerColor = scheme.surfaceContainerLow,
        inputFieldColors =
            SearchBarDefaults.inputFieldColors(
                focusedContainerColor = scheme.surfaceContainerHighest,
                unfocusedContainerColor = scheme.surfaceContainerHighest,
            ),
    )
}

/** The filter chips: at rest `medium` corners, pressed `small`, selected a full pill (§13.4). */
@Composable
internal fun searchChipShapes() =
    ChipShapes(
        shape = MaterialTheme.shapes.medium,
        selectedShape = CircleShape,
        pressedShape = MaterialTheme.shapes.small,
    )

/** The filter chips: selected on `primary` / `onPrimary`, the others outlined on nothing (§13.4). */
@Composable
internal fun searchChipColors(): SelectableChipColors {
    val scheme = MaterialTheme.colorScheme
    return FilterChipDefaults.filterChipColors(
        labelColor = scheme.onSurfaceVariant,
        selectedContainerColor = scheme.primary,
        selectedLabelColor = scheme.onPrimary,
        selectedLeadingIconColor = scheme.onPrimary,
    )
}

/** The header's background: [color] in [shape] (§13.4), with [bottomPadding] inside it. */
internal fun Modifier.headerBackground(
    color: Color,
    shape: Shape,
    bottomPadding: Dp,
): Modifier = background(color, shape).padding(bottom = bottomPadding)

/**
 * A chat row's end: the button that removes a recent chat ([onRemove]), else a chevron (`docs/DESIGN_SYSTEM.md`
 * §13.4), decorative, as the whole row opens the chat.
 */
internal fun chatTrailing(
    title: String,
    onRemove: (() -> Unit)?,
): @Composable () -> Unit = if (onRemove != null) ({ RemoveRecentButton(title, onRemove) }) else ({ ChatChevron() })

/** The chevron at a chat row's end, mirrored right to left. */
@Composable
private fun ChatChevron() {
    Icon(
        painter = painterResource(UiR.drawable.ic_chevron_right),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Two lines of a found message, its matches on `primaryContainer` blocks with `small` corners, drawn from the text's
 * layout ([textBlocks]), the rest in `onSurfaceVariant` (§13.4).
 */
@Composable
internal fun MessagePreview(preview: AnnotatedString) {
    val blocks = remember { TextBlocksLayout() }
    Text(
        text = preview,
        modifier =
            Modifier
                .textBlocks(
                    layout = blocks,
                    codeColor = Color.Unspecified,
                    spoilerColor = Color.Unspecified,
                    shape = MaterialTheme.shapes.small,
                    horizontalPadding = MaterialTheme.sizes.textBlockPadding,
                    matchColor = MaterialTheme.colorScheme.primaryContainer,
                ).clearAndSetSemantics {},
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        overflow = TextOverflow.Ellipsis,
        maxLines = 2,
        onTextLayout = blocks.onTextLayout,
        style = MaterialTheme.typography.bodyMedium.userContent,
    )
}

/** A found message's date, which screen readers hear with the row. */
@Composable
internal fun MessageDate(date: String) {
    Text(
        text = date,
        modifier = Modifier.clearAndSetSemantics {},
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        style = MaterialTheme.typography.labelSmall.userContent,
    )
}

/**
 * A found message's text after its sender, its matches as `primaryContainer` blocks ([blockHighlighted], §13.4), in the
 * emphasized weight.
 */
@Composable
internal fun foundMessageText(
    sender: String?,
    text: String,
    highlight: MatchHighlight,
): AnnotatedString {
    val colors = MaterialTheme.colorScheme
    val emphasis = MaterialTheme.typography.bodyMediumEmphasized.fontWeight
    return remember(sender, text, highlight, colors.onSurface, colors.onPrimaryContainer, emphasis) {
        blockHighlighted(
            label = sender,
            text = text,
            highlight = highlight,
            labelStyle = SpanStyle(color = colors.onSurface, fontWeight = emphasis),
            matchStyle = SpanStyle(color = colors.onPrimaryContainer, fontWeight = emphasis),
        )
    }
}

/** The first line of a found message: its chat's [title], then its [date] at the end (§13.4). */
@Composable
internal fun TitleWithDate(
    date: String,
    title: @Composable () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { title() }
        Spacer(Modifier.width(MaterialTheme.spacing.small))
        MessageDate(date)
    }
}
