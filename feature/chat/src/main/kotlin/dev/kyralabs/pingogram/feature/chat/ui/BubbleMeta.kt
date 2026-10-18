package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.constrain
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.theme.BubbleColors
import dev.kyralabs.pingogram.core.ui.theme.messageBubble
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.senderNameColor
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.content.edgeToEdgeWidth
import dev.kyralabs.pingogram.feature.chat.ui.content.isBare
import dev.kyralabs.pingogram.feature.chat.ui.content.isEdgeToEdge
import kotlin.math.ceil
import kotlin.math.max
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * [content] with [meta] at its bottom end: after the last line of [content] when there is room for it there
 * ([lastLineWidth] of the content, or its whole width when that returns null), otherwise on a line of its own. One
 * layout rather than nested rows (`docs/PERFORMANCE.md` §4).
 */
@Composable
internal fun TrailingMeta(
    meta: @Composable () -> Unit,
    lastLineWidth: (LayoutDirection) -> Int?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val gap = MaterialTheme.spacing.small
    Layout(
        contents = listOf(content, meta),
        modifier = modifier,
    ) { (contentMeasurables, metaMeasurables), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val contentPlaceable = contentMeasurables.firstOrNull()?.measure(loose)
        val metaPlaceable = metaMeasurables.first().measure(loose)
        val contentWidth = contentPlaceable?.width ?: 0
        val contentHeight = contentPlaceable?.height ?: 0
        val lineWidth = if (contentPlaceable == null) 0 else lastLineWidth(layoutDirection) ?: contentWidth
        val inlineWidth = if (lineWidth > 0) lineWidth + gap.roundToPx() + metaPlaceable.width else metaPlaceable.width
        val inline = inlineWidth <= constraints.maxWidth
        val width = max(contentWidth, if (inline) inlineWidth else metaPlaceable.width)
        val height = if (inline) max(contentHeight, metaPlaceable.height) else contentHeight + metaPlaceable.height
        val size = constraints.constrain(IntSize(width, height))
        layout(size.width, size.height) {
            contentPlaceable?.placeRelative(0, 0)
            metaPlaceable.placeRelative(size.width - metaPlaceable.width, size.height - metaPlaceable.height)
        }
    }
}

/** "edited", the time, and the state of an outgoing message, in `labelSmall` with tabular figures. */
@Composable
internal fun BubbleMeta(
    bubble: ChatItem.Bubble,
    colors: BubbleColors,
    modifier: Modifier = Modifier,
) {
    MetaRow(bubble, colors.secondaryContent, statusTint = { it.tint(colors) }, modifier = modifier)
}

/**
 * [BubbleMeta] on a `mediaOverlay` pill, for bare content and media without a caption: everything in
 * `onMediaOverlay`, which stays legible on any picture.
 */
@Composable
internal fun OverlayMeta(
    bubble: ChatItem.Bubble,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.pingogramColors
    MetaRow(
        bubble = bubble,
        color = colors.onMediaOverlay,
        statusTint = { colors.onMediaOverlay },
        modifier =
            modifier
                .background(colors.mediaOverlay, CircleShape)
                .padding(horizontal = MaterialTheme.spacing.small, vertical = MaterialTheme.spacing.extraSmall),
    )
}

@Composable
private fun MetaRow(
    bubble: ChatItem.Bubble,
    color: Color,
    statusTint: @Composable (OutgoingStatus) -> Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val style = MaterialTheme.typography.labelSmall
        if (bubble.isEdited) Text(stringResource(R.string.chat_edited), color = color, style = style)
        Text(bubble.time, color = color, maxLines = 1, style = style)
        bubble.status?.let { StatusIcon(it, statusTint(it)) }
    }
}

/** The tint of an outgoing state in a bubble: `error` once failed, the bubble's accent once read. */
@Composable
private fun OutgoingStatus.tint(colors: BubbleColors): Color =
    when (this) {
        OutgoingStatus.Pending, OutgoingStatus.Sent -> colors.secondaryContent
        OutgoingStatus.Failed -> MaterialTheme.colorScheme.error
        OutgoingStatus.Read -> colors.accent
    }

/** A clock while sending, an error when it failed, one check once sent, two once read. */
@Composable
private fun StatusIcon(
    status: OutgoingStatus,
    tint: Color,
) {
    val icon =
        when (status) {
            OutgoingStatus.Pending -> UiR.drawable.ic_schedule
            OutgoingStatus.Failed -> UiR.drawable.ic_error
            OutgoingStatus.Sent -> UiR.drawable.ic_done
            OutgoingStatus.Read -> UiR.drawable.ic_done_all
        }
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        modifier = Modifier.size(MaterialTheme.sizes.inlineIconSmall),
        tint = tint,
    )
}
