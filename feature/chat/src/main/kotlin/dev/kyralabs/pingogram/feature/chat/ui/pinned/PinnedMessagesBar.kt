package dev.kyralabs.pingogram.feature.chat.ui.pinned

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.fastCoerceIn
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatEvent
import dev.kyralabs.pingogram.feature.chat.ui.PinnedEvent
import dev.kyralabs.pingogram.feature.chat.ui.preview.PinnedPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.senderPreview
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The pinned messages bar at the top of the conversation's sheet (`REQUIREMENTS.md` FR-C9, `docs/DESIGN_SYSTEM.md` §6):
 * a tonal strip (`surfaceContainerHigh`, `large` corners) with "Pinned message" (and "2 of 5" where there are several,
 * counted from the oldest, so the newest is "5 of 5") in `labelMedium` `primary` over one line of the message, its sender in `onSurface` before the preview in
 * `onSurfaceVariant`, `bodyMedium`.
 *
 * - **Segments** along its start, one per pinned message in the list's order, the oldest at the top and the newest at
 *   the bottom (at most [MAX_SEGMENTS] at a time, the window following the shown one), the shown one in `primary`, the
 *   others in `outlineVariant`; the highlight moves on the motion scheme's
 *   default spatial spring, read in the draw phase.
 * - **Cycling**: a tap shows the message in the list and moves on to the next older one ([PinnedEvent.ShowCurrent]);
 *   the text slides with it, an older message coming from above as it is above in the list, on the default spatial spring, fading on the effects springs.
 * - **Buttons**: the list of every pinned message where there are several ([PinnedEvent.OpenList]), and close
 *   ([PinnedEvent.Close]): unpin (`ic_keep_off`) for those who may, hide (`ic_close`) for the others.
 * - **Screen readers** hear one item, "Pinned message 2 of 5, Alice: …", whose action shows the message, with the two
 *   buttons as its custom actions; the buttons themselves are hidden from them.
 */
@Composable
internal fun PinnedMessagesBar(
    bar: PinnedBar,
    onEvent: (ChatEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.colorScheme
    val currentOnEvent by rememberUpdatedState(onEvent)
    val label = pinnedLabel(bar)
    val preview = senderPreview(bar.sender, bar.preview)
    val showLabel = stringResource(R.string.pinned_show)
    val listLabel = stringResource(R.string.pinned_list)
    val closeLabel = stringResource(if (bar.canUnpin) R.string.pinned_unpin else R.string.pinned_hide)
    val position = animateFloatAsState(bar.position.toFloat(), MaterialTheme.motionScheme.defaultSpatialSpec())
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(colors.surfaceContainerHigh)
                .clickable(onClickLabel = showLabel, role = Role.Button) { currentOnEvent(PinnedEvent.ShowCurrent) }
                .semantics {
                    customActions =
                        listOfNotNull(
                            CustomAccessibilityAction(listLabel) {
                                currentOnEvent(PinnedEvent.OpenList)
                                true
                            }.takeIf { bar.count > 1 },
                            CustomAccessibilityAction(closeLabel) {
                                currentOnEvent(PinnedEvent.Close)
                                true
                            },
                        )
                }.padding(start = spacing.medium, end = spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PinnedText(
            bar = bar,
            label = label,
            preview = preview,
            modifier =
                Modifier
                    .weight(1f)
                    .padding(vertical = spacing.small)
                    .pinnedSegments(position, bar.count, MaterialTheme.sizes.pinnedIndicator, spacing.extraSmall)
                    .padding(start = spacing.medium),
        )
        PinnedButtons(bar, listLabel, closeLabel, onEvent)
    }
}

/**
 * The bar's list button, where there are several pins, and its close button: hidden from screen readers, which get
 * them as the bar's actions.
 */
@Composable
private fun PinnedButtons(
    bar: PinnedBar,
    listLabel: String,
    closeLabel: String,
    onEvent: (ChatEvent) -> Unit,
) {
    if (bar.count > 1) {
        IconButton(
            onClick = { onEvent(PinnedEvent.OpenList) },
            modifier = Modifier.semantics { hideFromAccessibility() },
            shapes = IconButtonDefaults.shapes(),
        ) {
            Icon(painterResource(UiR.drawable.ic_format_list_bulleted), contentDescription = listLabel)
        }
    }
    IconButton(
        onClick = { onEvent(PinnedEvent.Close) },
        modifier = Modifier.semantics { hideFromAccessibility() },
        shapes = IconButtonDefaults.shapes(),
    ) {
        val icon = if (bar.canUnpin) UiR.drawable.ic_keep_off else UiR.drawable.ic_close
        Icon(painterResource(icon), contentDescription = closeLabel)
    }
}

/** The label and the preview, sliding to the next pinned message as the bar moves on. */
@Composable
private fun PinnedText(
    bar: PinnedBar,
    label: String,
    preview: AnnotatedString,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = Shown(bar.messageId, bar.position, label, preview),
        modifier = modifier,
        transitionSpec = {
            // To an older message the text comes from above, as the older message is above in the list.
            val direction = if (targetState.position < initialState.position) -1 else 1
            (
                slideInVertically(motion.defaultSpatialSpec()) { direction * it / 2 } +
                    fadeIn(motion.defaultEffectsSpec())
            ).togetherWith(
                slideOutVertically(motion.defaultSpatialSpec()) { -direction * it / 2 } +
                    fadeOut(motion.fastEffectsSpec()),
            ).using(SizeTransform(clip = false) { _, _ -> motion.defaultSpatialSpec() })
        },
        contentKey = { it.messageId },
        label = "pinnedMessage",
    ) { shown ->
        Column {
            Text(
                text = shown.label,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = shown.preview,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.userContent,
            )
        }
    }
}

/** What the bar's text shows of a pinned message. */
private data class Shown(
    val messageId: Long,
    val position: Int,
    val label: String,
    val preview: AnnotatedString,
)

/** "Pinned message", or "Pinned message 2 of 5" where there are several, counted from the oldest. */
@Composable
private fun pinnedLabel(bar: PinnedBar): String =
    if (bar.count > 1) {
        stringResource(R.string.pinned_message_of, bar.position + 1, bar.count)
    } else {
        stringResource(R.string.pinned_message)
    }

/**
 * Draws a segment per pinned message (at most [MAX_SEGMENTS]) along the start of this element, [width] wide and [gap]
 * apart: the one at [position] (animated, read in the draw phase) in `primary`, the others in `outlineVariant`.
 */
@Composable
private fun Modifier.pinnedSegments(
    position: State<Float>,
    count: Int,
    width: Dp,
    gap: Dp,
): Modifier {
    val active = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.outlineVariant
    return drawBehind {
        val visible = count.coerceAtMost(MAX_SEGMENTS)
        val gapPx = if (visible > 1) gap.toPx() else 0f
        val segment = (size.height - gapPx * (visible - 1)) / visible
        val shown = position.value
        // The window of segments ends at the newest at first, and follows the shown message up, keeping it inside.
        val first = shown.coerceAtMost((count - visible).toFloat()).coerceAtLeast(0f)
        val radius = CornerRadius(width.toPx() / 2)
        val x = if (layoutDirection == LayoutDirection.Rtl) size.width - width.toPx() else 0f
        for (i in 0 until visible) {
            val top = i * (segment + gapPx)
            drawRoundRect(inactive, Offset(x, top), Size(width.toPx(), segment), radius)
        }
        val highlightTop = ((shown - first) * (segment + gapPx)).fastCoerceIn(0f, size.height - segment)
        drawRoundRect(active, Offset(x, highlightTop), Size(width.toPx(), segment), radius)
    }
}

/** Segments the bar shows at once, as Telegram's apps do; more pinned messages scroll through them. */
private const val MAX_SEGMENTS = 4

@PingogramPreviews
@Composable
private fun PinnedMessagesBarPreview() {
    PingogramPreview { PinnedMessagesBar(PinnedPreviewData.one, onEvent = {}) }
}

@PingogramPreviews
@Composable
private fun PinnedMessagesBarManyPreview() {
    PingogramPreview { PinnedMessagesBar(PinnedPreviewData.many, onEvent = {}) }
}

/**
 * The place of the [PinnedMessagesBar] at the top of the conversation's sheet, inset from its sides and top as its rows
 * are ([padding]: the sheet's content padding). The bar grows in and shrinks away on the default spatial spring,
 * fading on the effects springs, keeping the last bar while it leaves.
 */
@Composable
internal fun PinnedBarSlot(
    bar: PinnedBar?,
    onEvent: (ChatEvent) -> Unit,
    padding: PaddingValues,
) {
    val motion = MaterialTheme.motionScheme
    val last = remember { mutableStateOf(bar) }
    SideEffect { if (bar != null) last.value = bar }
    val shown = bar ?: last.value
    val direction = LocalLayoutDirection.current
    val small = MaterialTheme.spacing.small
    AnimatedVisibility(
        visible = bar != null,
        enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
        exit = shrinkVertically(motion.defaultSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
    ) {
        shown?.let {
            PinnedMessagesBar(
                bar = it,
                onEvent = onEvent,
                modifier =
                    Modifier.padding(
                        start = padding.calculateStartPadding(direction) + small,
                        top = padding.calculateTopPadding(),
                        end = padding.calculateEndPadding(direction) + small,
                    ),
            )
        }
    }
}
