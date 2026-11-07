package dev.kyralabs.pingogram.feature.chat.ui.rich

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.RichBlock
import dev.kyralabs.pingogram.core.ui.theme.chatStyle
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** [blocks] one after another, in the caller's column. */
@Composable
internal fun RichBlocks(
    blocks: ImmutableList<RichBlock>,
    scope: RichScope,
) {
    blocks.forEach { block -> RichBlockView(block, scope) }
}

/** One block, in the style of its kind (`docs/DESIGN_SYSTEM.md` §6 "Rich message"). */
@Composable
private fun RichBlockView(
    block: RichBlock,
    scope: RichScope,
) {
    when (block) {
        is RichBlock.TextBlock -> RichTextBlock(block, scope)
        is RichBlock.MathematicalExpression -> RichPreformatted(rememberPlain(block.expression), scope)
        RichBlock.Divider -> HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        is RichBlock.Anchor -> Unit
        is RichBlock.ListBlock -> RichList(block, scope)
        is RichBlock.BlockQuote -> RichQuote(scope, block.credit) { RichBlocks(block.blocks, scope) }
        is RichBlock.Details -> RichDetails(block, scope)
        is RichBlock.Media -> RichMedia(block, scope)
        is RichBlock.Collage -> RichMediaGroup(block.blocks, block.caption, scope)
        is RichBlock.Slideshow -> RichMediaGroup(block.blocks, block.caption, scope)
        is RichBlock.Table -> RichTable(block, scope)
        is RichBlock.ButtonRow -> RichButtonRow(block)
        RichBlock.Unsupported -> RichUnsupported(scope)
    }
}

/** A block of one text: headings in the emphasized styles, paragraphs in the message text style. */
@Composable
private fun RichTextBlock(
    block: RichBlock.TextBlock,
    scope: RichScope,
) {
    val typography = MaterialTheme.typography
    val secondary = scope.colors.secondaryContent
    when (block) {
        is RichBlock.Title -> RichText(block.text, scope, typography.titleLargeEmphasized)
        is RichBlock.Subtitle -> RichText(block.text, scope, typography.titleMedium, color = secondary)
        is RichBlock.Header -> RichText(block.text, scope, typography.titleMediumEmphasized)
        is RichBlock.Subheader -> RichText(block.text, scope, typography.titleSmallEmphasized)
        is RichBlock.SectionHeading -> RichText(block.text, scope, sectionHeadingStyle(block.size))
        is RichBlock.Kicker -> RichText(block.text, scope, typography.labelLarge, color = scope.colors.accent)
        is RichBlock.Paragraph -> RichText(block.text, scope, MaterialTheme.chatStyle.messageText)
        is RichBlock.Preformatted -> RichPreformatted(block.text, scope)
        is RichBlock.Footer -> RichText(block.text, scope, typography.bodySmall, color = secondary)
        is RichBlock.PullQuote -> RichPullQuote(block, scope)
        is RichBlock.ExpandableBlockQuote -> RichQuote(scope, block.credit) { RichParagraph(block.text, scope) }
    }
}

/** A section heading's style by its [size], 1 the largest. */
@Composable
private fun sectionHeadingStyle(size: Int): TextStyle {
    val typography = MaterialTheme.typography
    return when {
        size <= 1 -> typography.titleMediumEmphasized
        size == 2 -> typography.titleSmallEmphasized
        else -> typography.labelLargeEmphasized
    }
}

@Composable
internal fun RichParagraph(
    text: FormattedText,
    scope: RichScope,
) = RichText(text, scope, MaterialTheme.chatStyle.messageText)

/** [text] as written, in monospace on the bubble's block color with `medium` corners, as code blocks are. */
@Composable
private fun RichPreformatted(
    text: FormattedText,
    scope: RichScope,
) {
    RichText(
        text = text,
        scope = scope,
        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        modifier =
            Modifier
                .fillMaxWidth()
                .background(scope.colors.block, MaterialTheme.shapes.medium)
                .padding(MaterialTheme.spacing.small),
        color = scope.colors.blockContent,
    )
}

/** A text the message gives as a plain string, kept as one object so its annotated text is built once. */
@Composable
private fun rememberPlain(text: String): FormattedText = remember(text) { FormattedText(text, persistentListOf()) }

/**
 * A list: each item's label ("1.", "•") or, for a checklist, a checkbox in the bubble's accent (not a control: the
 * sender checked it), then its blocks.
 */
@Composable
private fun RichList(
    block: RichBlock.ListBlock,
    scope: RichScope,
) {
    val spacing = MaterialTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.extraSmall)) {
        block.items.forEach { item ->
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                if (item.hasCheckbox) {
                    Checkbox(
                        checked = item.isChecked,
                        onCheckedChange = null,
                        colors =
                            CheckboxDefaults.colors(
                                checkedColor = scope.colors.accent,
                                checkmarkColor = scope.colors.onAccent,
                                uncheckedColor = scope.colors.secondaryContent,
                            ),
                    )
                } else if (item.label.isNotBlank()) {
                    Text(
                        text = item.label,
                        color = scope.colors.secondaryContent,
                        style = MaterialTheme.chatStyle.messageText,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(spacing.extraSmall)) {
                    RichBlocks(item.blocks, scope)
                }
            }
        }
    }
}

/** In place of a block Pingogram cannot show yet: one line saying so. */
@Composable
private fun RichUnsupported(scope: RichScope) {
    Text(
        text = stringResource(R.string.rich_unsupported_block),
        color = scope.colors.secondaryContent,
        maxLines = 1,
        style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
    )
}
