package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.constrain
import dev.kyralabs.pingogram.core.ui.theme.BubbleColors
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
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

/**
 * "edited", the time, and the state of an outgoing message, in `labelSmall` with tabular figures; a failed message's
 * state is its [NotSentLabel] instead. Shown where [ChatItem.Bubble.showsTime] says.
 */
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

/**
 * [BubbleMeta] on the conversation's background, for content shown without a bubble or pill (emoji only): the time in
 * `onSurfaceVariant`, a read message's checks in `primary`.
 */
@Composable
internal fun SurfaceMeta(
    bubble: ChatItem.Bubble,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    MetaRow(
        bubble = bubble,
        color = scheme.onSurfaceVariant,
        statusTint = { status -> if (status == OutgoingStatus.Read) scheme.primary else scheme.onSurfaceVariant },
        modifier = modifier,
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
        bubble.status?.takeIf { it != OutgoingStatus.Failed }?.let { StatusIcon(it, statusTint(it)) }
    }
}

/** The tint of an outgoing state in a bubble: the bubble's accent once read. */
private fun OutgoingStatus.tint(colors: BubbleColors): Color =
    if (this == OutgoingStatus.Read) colors.accent else colors.secondaryContent

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

/**
 * "Not sent · Tap to retry" under the bubble of a message that failed to send (`docs/DESIGN_SYSTEM.md` §2.2), with the
 * error icon, in `onErrorContainer` on an `errorContainer` pill; just "Not sent" where TDLib cannot send it again
 * ([canRetry]). With [canRetry] it is a button that sends the message again ([onRetry]), its touch target 48 dp tall
 * without the label taking more room ([touchTarget]). Inside a bubble's row, screen readers hear "not sent" in the
 * bubble's description and send it again through its custom action instead.
 */
@Composable
internal fun NotSentLabel(
    canRetry: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val spacing = MaterialTheme.spacing
    val interactions = remember { MutableInteractionSource() }
    val target =
        if (canRetry) {
            Modifier.touchTarget(
                Modifier.clickable(interactions, indication = null, role = Role.Button, onClick = onRetry),
            )
        } else {
            Modifier
        }
    Row(
        modifier =
            modifier
                .then(target)
                .clip(CircleShape)
                .then(if (canRetry) Modifier.indication(interactions, ripple()) else Modifier)
                .background(scheme.errorContainer)
                .padding(horizontal = spacing.small, vertical = spacing.extraSmall),
        horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(UiR.drawable.ic_error),
            contentDescription = null,
            modifier = Modifier.size(MaterialTheme.sizes.inlineIconSmall),
            tint = scheme.onErrorContainer,
        )
        Text(
            text = stringResource(if (canRetry) R.string.chat_not_sent_retry else R.string.chat_not_sent),
            color = scheme.onErrorContainer,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
