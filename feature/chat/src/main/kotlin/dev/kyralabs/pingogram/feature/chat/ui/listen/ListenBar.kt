package dev.kyralabs.pingogram.feature.chat.ui.listen

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.domain.repository.PlaybackSpeed
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ListenEvent
import dev.kyralabs.pingogram.feature.chat.ui.content.SpeedChip
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * Listen to posts' player (`REQUIREMENTS.md` FR-X26), at the top of the conversation's sheet like its other bars: a
 * tonal strip on `surfaceContainerHigh` with `large` corners: the `graphic_eq` symbol in `tertiary`, "Listening · 2
 * of 5" in `labelLarge` (a polite live region, "Post 2 of 5"), the voice messages' speed chip, then Pause or Play, Next
 * (`skip_next`, disabled on the last post) and Stop (`stop`) as icon buttons. Screen readers hear a pane, "Listening",
 * with its labelled buttons.
 */
@Composable
internal fun ListenBar(
    player: ListenPlayer,
    onEvent: (ListenEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val spacing = MaterialTheme.spacing
    val pane = stringResource(R.string.listen_pane)
    val spoken = stringResource(R.string.listen_position_spoken, player.position, player.count)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(colors.surfaceContainerHigh)
                .semantics { paneTitle = pane }
                .padding(start = spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(UiR.drawable.ic_graphic_eq), contentDescription = null, tint = colors.tertiary)
        Text(
            text = stringResource(R.string.listen_position, player.position, player.count),
            modifier =
                Modifier
                    .weight(1f)
                    .padding(horizontal = spacing.medium, vertical = spacing.small)
                    .semantics {
                        contentDescription = spoken
                        liveRegion = LiveRegionMode.Polite
                    },
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge,
        )
        SpeedChip(
            visible = true,
            speed = player.speed,
            onChange = { onEvent(ListenEvent.ChangeSpeed) },
            containerColor = colors.secondaryContainer,
            contentColor = colors.onSecondaryContainer,
        )
        if (player.isPaused) {
            BarButton(UiR.drawable.ic_play_arrow_filled, R.string.listen_resume) { onEvent(ListenEvent.Resume) }
        } else {
            BarButton(UiR.drawable.ic_pause_filled, R.string.listen_pause) { onEvent(ListenEvent.Pause) }
        }
        BarButton(UiR.drawable.ic_skip_next_filled, R.string.listen_next, enabled = player.hasNext) {
            onEvent(ListenEvent.Next)
        }
        BarButton(UiR.drawable.ic_stop_filled, R.string.listen_stop) { onEvent(ListenEvent.Stop) }
    }
}

@Composable
private fun BarButton(
    @DrawableRes icon: Int,
    @StringRes label: Int,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled, shapes = IconButtonDefaults.shapes()) {
        Icon(painterResource(icon), contentDescription = stringResource(label))
    }
}

/**
 * The player's slot for the conversation ([LocalListenBar]), reading [viewModel]'s player there alone, so a step
 * recomposes the bar and not the conversation.
 */
@Composable
internal fun rememberListenSlot(viewModel: ListenViewModel): @Composable (PaddingValues) -> Unit {
    val player = viewModel.player.collectAsStateWithLifecycle()
    return remember(viewModel) { { padding -> ListenSlot(player, viewModel::onEvent, padding) } }
}

/** The bar, growing in and shrinking away on the default spatial spring like the conversation's other bars. */
@Composable
private fun ListenSlot(
    player: State<ListenPlayer?>,
    onEvent: (ListenEvent) -> Unit,
    padding: PaddingValues,
) {
    val motion = MaterialTheme.motionScheme
    val current = player.value
    val last = remember { mutableStateOf(current) }
    SideEffect { if (current != null) last.value = current }
    val shown = current ?: last.value
    val direction = LocalLayoutDirection.current
    val small = MaterialTheme.spacing.small
    AnimatedVisibility(
        visible = current != null,
        enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
        exit = shrinkVertically(motion.defaultSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
    ) {
        shown?.let {
            ListenBar(
                player = it,
                onEvent = onEvent,
                modifier =
                    Modifier.padding(
                        start = padding.calculateStartPadding(direction) + small,
                        top = padding.calculateTopPadding() + small,
                        end = padding.calculateEndPadding(direction) + small,
                    ),
            )
        }
    }
}

/** Listen to posts' player bar, provided by the conversation's route; null shows none. */
internal val LocalListenBar = compositionLocalOf<(@Composable (PaddingValues) -> Unit)?> { null }

@PingogramPreviews
@Composable
private fun ListenBarPreview() {
    PingogramPreview {
        ListenBar(ListenPlayer(position = 2, count = 5, isPaused = false, PlaybackSpeed.Faster), onEvent = {})
    }
}

@PingogramPreviews
@Composable
private fun ListenBarPausedLastPreview() {
    PingogramPreview {
        ListenBar(ListenPlayer(position = 5, count = 5, isPaused = true, PlaybackSpeed.Normal), onEvent = {})
    }
}
