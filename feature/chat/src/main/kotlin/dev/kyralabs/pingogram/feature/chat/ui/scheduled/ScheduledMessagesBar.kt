package dev.kyralabs.pingogram.feature.chat.ui.scheduled

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.preview.ScheduledPreviewData

/**
 * "3 scheduled messages" at the top of the conversation's sheet (`REQUIREMENTS.md` FR-C5, `docs/DESIGN_SYSTEM.md` §6
 * "Scheduled messages bar"), below the pinned messages and translate bars, as they are: a tonal strip
 * (`surfaceContainerHigh`, `large` corners) with the schedule-send symbol in `primary`, the count in `labelLarge`
 * `primary` over when the first goes out ("Next: Today, 9:41 AM") in `bodyMedium` `onSurfaceVariant`, one line each,
 * ellipsized. A tap opens the list of them ([onOpen]); the count and the time crossfade on the effects springs as they
 * change, the bar's height following on the default spatial spring. Screen readers hear one button, "3 scheduled
 * messages, Next: Today, 9:41 AM".
 */
@Composable
internal fun ScheduledMessagesBar(
    bar: ScheduledBar,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.colorScheme
    val openLabel = stringResource(R.string.scheduled_bar_open_label)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(colors.surfaceContainerHigh)
                .clickable(onClickLabel = openLabel, role = Role.Button, onClick = onOpen)
                .padding(horizontal = spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_schedule_send),
            contentDescription = null,
            tint = colors.primary,
        )
        ScheduledBarText(
            bar = bar,
            modifier = Modifier.weight(1f).padding(start = spacing.medium, top = spacing.small, bottom = spacing.small),
        )
    }
}

/** The count over when the first message goes out, crossfading as they change. */
@Composable
private fun ScheduledBarText(
    bar: ScheduledBar,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = bar,
        modifier = modifier,
        transitionSpec = {
            fadeIn(motion.defaultEffectsSpec())
                .togetherWith(fadeOut(motion.fastEffectsSpec()))
                .using(SizeTransform(clip = false) { _, _ -> motion.defaultSpatialSpec() })
        },
        label = "scheduledBar",
    ) { shown ->
        Column {
            Text(
                text = pluralStringResource(R.plurals.scheduled_bar_count, shown.count, shown.count),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = shown.next.resolve(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.userContent,
            )
        }
    }
}

@PingogramPreviews
@Composable
private fun ScheduledMessagesBarPreview() {
    PingogramPreview { ScheduledMessagesBar(ScheduledPreviewData.bar, onOpen = {}) }
}

@PingogramPreviews
@Composable
private fun ScheduledMessagesBarOnePreview() {
    PingogramPreview { ScheduledMessagesBar(ScheduledPreviewData.barOne, onOpen = {}) }
}

/**
 * The place of the [ScheduledMessagesBar] at the top of the conversation's sheet, below the pinned messages and
 * translate bars, inset from its sides and top as its rows are ([padding]: the sheet's content padding). The bar grows
 * in and shrinks away on the default spatial spring, fading on the effects springs, keeping the last bar while it
 * leaves. Without [onOpen] (a screen without the list) there is no bar.
 */
@Composable
internal fun ScheduledBarSlot(
    bar: ScheduledBar?,
    onOpen: (() -> Unit)?,
    padding: PaddingValues,
) {
    if (onOpen == null) return
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
            ScheduledMessagesBar(
                bar = it,
                onOpen = onOpen,
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
