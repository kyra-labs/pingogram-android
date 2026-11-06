package dev.kyralabs.pingogram.feature.chat.ui.rich

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.MultiContentMeasurePolicy
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import dev.kyralabs.pingogram.core.domain.model.RichMessage
import dev.kyralabs.pingogram.core.ui.theme.BubbleColors
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.BubbleResources
import dev.kyralabs.pingogram.feature.chat.ui.BubbleStyle
import dev.kyralabs.pingogram.feature.chat.ui.ChatEvent
import dev.kyralabs.pingogram.feature.chat.ui.ChatItem
import dev.kyralabs.pingogram.feature.chat.ui.RichMessageEvent

/**
 * A rich message in its bubble (`docs/DESIGN_SYSTEM.md` §6 "Rich message"): its blocks, as wide as the bubble gets,
 * `spacing.small` apart ([RichBlocks]), laid out in the post's own direction ([RichMessage.isRtl]). A message taller
 * than [MAX_SCREENS] windows is cut there, with "Show more" below it ([RichMessageEvent.ShowAll]), in one layout
 * that learns whether it is cut as it measures ([HeightCap]), so nothing composes again for it. Where Telegram sent
 * only its start ([RichMessage.isFull] false), "Show full message" follows ([RichMessageEvent.LoadFull]), a
 * `LoadingIndicator` beside it while it loads.
 *
 * Screen readers hear the bubble as one, with all of its text ([richDescription]) and its sections and the full
 * message as custom actions ([richActions]).
 */
@Composable
internal fun RichMessageBody(
    bubble: ChatItem.Bubble,
    message: RichMessage,
    style: BubbleStyle,
    resources: BubbleResources,
    onEvent: (ChatEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val messageId = bubble.id
    val ui = bubble.rich
    val scope = rememberRichScope(bubble, message, style, resources, onEvent)
    val showsAll = ui?.showsAll == true
    val cap = remember { HeightCap() }
    val maxHeight = (LocalWindowInfo.current.containerSize.height * MAX_SCREENS).toInt()
    val gap = MaterialTheme.spacing.small
    Layout(
        contents =
            listOf(
                {
                    // The post's own direction for its lists, sections and tables; each text keeps its own.
                    val direction = if (message.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                    CompositionLocalProvider(LocalLayoutDirection provides direction) {
                        Column(
                            modifier = Modifier.fillMaxWidth().capHeight(if (showsAll) null else maxHeight, cap),
                            verticalArrangement = Arrangement.spacedBy(gap),
                        ) { RichBlocks(message.blocks, scope) }
                    }
                },
                {
                    if (!showsAll) {
                        RichFooterButton(stringResource(R.string.rich_show_more), style.colors) {
                            onEvent(RichMessageEvent.ShowAll(messageId))
                        }
                    }
                },
                {
                    if (!message.isFull) {
                        RichFooterButton(
                            stringResource(R.string.rich_show_full),
                            style.colors,
                            isLoading = ui?.isLoadingFull == true,
                        ) { onEvent(RichMessageEvent.LoadFull(messageId)) }
                    }
                },
            ),
        modifier = modifier,
        measurePolicy = remember(cap, gap) { RichBodyMeasurePolicy(cap, gap) },
    )
}

/**
 * The body's layout: the blocks, then "Show more" where [cap] says the blocks were cut and "Show full message" where
 * there is one, [gap] apart, as wide as the blocks.
 */
private class RichBodyMeasurePolicy(
    private val cap: HeightCap,
    private val gap: Dp,
) : MultiContentMeasurePolicy {
    override fun MeasureScope.measure(
        measurables: List<List<Measurable>>,
        constraints: Constraints,
    ): MeasureResult {
        val (body, more, full) = measurables
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val bodyPlaceable = body.first().measure(loose.copy(maxHeight = Constraints.Infinity))
        val morePlaceable = more.firstOrNull()?.takeIf { cap.isCut }?.measure(loose)
        val fullPlaceable = full.firstOrNull()?.measure(loose)
        val width = constraints.constrainWidth(bodyPlaceable.width)
        val gapPx = gap.roundToPx()
        val footers = listOfNotNull(morePlaceable, fullPlaceable)
        val height = bodyPlaceable.height + footers.sumOf { it.height + gapPx }
        return layout(width, constraints.constrainHeight(height)) {
            bodyPlaceable.placeRelative(0, 0)
            var y = bodyPlaceable.height
            footers.forEach {
                y += gapPx
                it.placeRelative(0, y)
                y += it.height
            }
        }
    }
}

/** Whether the content [capHeight] measured last was taller than its cap, for the layout that holds it. */
private class HeightCap {
    var isCut = false
}

/**
 * At most [max] pixels tall (no cap while null), measured as tall as it wants and cut at [max], clipped; whether it
 * was cut goes into [cap], read by the parent's measure policy in the same pass.
 */
private fun Modifier.capHeight(
    max: Int?,
    cap: HeightCap,
): Modifier =
    clipToBounds().layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(maxHeight = Constraints.Infinity))
        cap.isCut = max != null && placeable.height > max
        val height = if (cap.isCut && max != null) max else placeable.height
        layout(placeable.width, height) { placeable.placeRelative(0, 0) }
    }

/** "Show more" or "Show full message" under the blocks, in the bubble's accent; with [isLoading], disabled. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class) // LoadingIndicator.
@Composable
private fun RichFooterButton(
    label: String,
    colors: BubbleColors,
    isLoading: Boolean = false,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        enabled = !isLoading,
        shapes = ButtonDefaults.shapes(),
        colors = ButtonDefaults.textButtonColors(contentColor = colors.accent),
    ) {
        if (isLoading) {
            LoadingIndicator(Modifier.size(MaterialTheme.sizes.inlineIcon), color = colors.accent)
            Spacer(Modifier.width(MaterialTheme.spacing.small))
        }
        Text(label)
    }
}

/** How tall a rich message gets before "Show more": one and a half windows. */
private const val MAX_SCREENS = 1.5f
