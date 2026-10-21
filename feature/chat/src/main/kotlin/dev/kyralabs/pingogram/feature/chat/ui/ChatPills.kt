package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import kotlinx.collections.immutable.ImmutableList

// The entries of the conversation that are not bubbles (docs/DESIGN_SYSTEM.md §2.2: service / date pill in
// surfaceContainerHighest / onSurfaceVariant), and the pill that floats over the list while it scrolls.

/** "Today", "Monday, March 17": a date in a pill, inline above a day's first message or floating over the list. */
@Composable
internal fun DatePill(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier =
            modifier
                .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)
                .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.extraSmall),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        style = MaterialTheme.typography.labelLarge,
    )
}

/** An inline date separator, centred; a heading for screen readers, so they can jump from day to day. */
@Composable
internal fun DateSeparatorRow(
    item: ChatItem.DateSeparator,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        DatePill(item.date.resolve(), Modifier.semantics { heading() })
    }
}

/**
 * A service message ("Alice joined the group"), centred in a pill. Long texts wrap within the width of a bubble, the
 * shape's corners rounding the lines.
 */
@Composable
internal fun ServiceMessageRow(
    item: ChatItem.Service,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text = item.text.resolve(),
            modifier =
                Modifier
                    .widthIn(max = MaterialTheme.sizes.bubbleMaxWidth)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.extraLarge)
                    .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.extraSmall),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            // Names in it are isolated (`bidiIsolated`): it takes its language's direction, not the layout's.
            style = MaterialTheme.typography.labelLarge.userContent,
        )
    }
}

/** "Unread messages" between two dividers, above the first message that was unread when the chat was opened. */
@Composable
internal fun UnreadDividerRow(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            text = stringResource(R.string.chat_unread_divider),
            modifier = Modifier.semantics { heading() },
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
        )
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/**
 * The date of the topmost entry in a pill at the top, shown while the list scrolls. The pill composes again only when
 * that date changes, not with every new message or scrolled frame; its alpha is read in the draw phase.
 */
@Composable
internal fun FloatingDatePill(
    items: ImmutableList<ChatItem>,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val currentItems by rememberUpdatedState(items)
    val date by remember(listState) {
        derivedStateOf {
            val entries = currentItems
            listState.layoutInfo.visibleItemsInfo
                .lastOrNull { it.index < entries.size }
                ?.let { entries[it.index].date }
        }
    }
    val alpha =
        animateFloatAsState(
            targetValue = if (listState.isScrollInProgress) 1f else 0f,
            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
            label = "datePill",
        )
    val pillModifier = remember(modifier, alpha) { modifier.graphicsLayer { this.alpha = alpha.value } }
    date?.let { DatePill(it.resolve(), pillModifier) }
}
