package dev.kyralabs.pingogram.feature.media.storypost

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.zIndex
import dev.kyralabs.pingogram.core.ui.component.animateItem
import dev.kyralabs.pingogram.core.ui.component.rememberItemMotion
import dev.kyralabs.pingogram.core.ui.gesture.SettlingOffset
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.media.R
import dev.kyralabs.pingogram.feature.media.ui.formatDuration
import kotlinx.coroutines.CoroutineScope
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * "In this story": the picked items in the order they will be posted, a keyed row of tiles (`large` corners, the
 * shown one ringed in `primary`), then those still being added and "Add more" while the post takes more. A tap shows an
 * item; a long press lifts it to drag it into its place, the others sliding aside on the motion scheme's spatial
 * spring, with a `SegmentTick` at each place it passes; dropped, it settles into its place on the default spatial
 * spring. Every tile comes, moves and goes on the theme's item motion. Screen readers hear "Photo 2 of 4" and move or
 * remove an item through its custom actions.
 */
@Composable
internal fun PickStrip(
    state: NewStoryUiState,
    onEvent: (NewStoryEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    val drag = remember { StripDrag() }
    val motion = rememberItemMotion()
    val step = with(LocalDensity.current) { (MaterialTheme.sizes.panelSticker + spacing.small).toPx() }
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = spacing.medium, vertical = spacing.small),
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        itemsIndexed(state.items, key = { _, pick -> pick.key }, contentType = { _, _ -> "pick" }) { index, pick ->
            val dragged = drag.key == pick.key
            PickTile(
                pick = pick,
                position = TilePosition(index, state.items.size),
                selected = pick.key == state.shown?.key,
                onEvent = onEvent,
                modifier =
                    if (dragged) {
                        // Under the finger, then settling into its place: its own offset, not the list's placement.
                        Modifier.zIndex(1f).graphicsLayer { translationX = drag.offset.value }
                    } else {
                        Modifier
                    }.animateItem(this, motion, placement = !dragged)
                        .dragToReorder(
                            drag,
                            pick.key,
                            TilePosition(index, state.items.size),
                            step,
                            onEvent,
                        ),
            )
        }
        items(state.preparing, key = { PREPARING_KEY + it }, contentType = { PREPARING_KEY }) {
            Box(Modifier.animateItem(this, motion)) { PreparingTile() }
        }
        if (state.remaining > 0 && state.items.isNotEmpty()) {
            item(key = "add", contentType = "add") {
                Box(Modifier.animateItem(this, motion)) { AddMoreTile(onClick = { onEvent(NewStoryEvent.AddMore) }) }
            }
        }
    }
}

/** An item's place among [count]. */
internal data class TilePosition(
    val index: Int,
    val count: Int,
)

/**
 * The tile being dragged or settling after a drop, by its key, and how far it is from its place, in the layout's
 * direction: a [SettlingOffset], so a drop settles it into its place and a new drag takes it from there.
 */
@Stable
private class StripDrag {
    var key by mutableStateOf<String?>(null)
    val offset = SettlingOffset()

    /** A long press lifts the tile of [tileKey]: it takes over from a tile still settling. */
    fun lift(tileKey: String) {
        if (key != tileKey) offset.snapTo(0f) else offset.dragBy(0f)
        key = tileKey
    }

    /** The finger let go: the tile settles into its place, then the list's placement takes it again. */
    fun drop(
        scope: CoroutineScope,
        spec: AnimationSpec<Float>,
    ) {
        val dropped = key ?: return
        offset.settle(scope, 0f, spec, onSettled = { if (key == dropped) key = null })
    }
}

/**
 * Lifts the tile on a long press and drags it: past half a tile toward a neighbour it takes the neighbour's place
 * ([NewStoryEvent.Move]) and its offset is kept relative to the new place, so it stays under the finger. Dropped, it
 * settles into its place on the default spatial spring.
 */
@Composable
private fun Modifier.dragToReorder(
    drag: StripDrag,
    key: String,
    position: TilePosition,
    step: Float,
    onEvent: (NewStoryEvent) -> Unit,
): Modifier {
    val current by rememberUpdatedState(position)
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val settle by rememberUpdatedState(MaterialTheme.motionScheme.defaultSpatialSpec<Float>())
    val drop = { drag.drop(scope, settle) }
    val sign = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    return pointerInput(key, step, sign) {
        detectDragGesturesAfterLongPress(
            onDragStart = {
                drag.lift(key)
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            },
            onDragEnd = { drop() },
            onDragCancel = { drop() },
            onDrag = { change, amount ->
                change.consume()
                drag.offset.dragBy(amount.x)
                drag.passed(current, step * sign)?.let { to ->
                    onEvent(NewStoryEvent.Move(key, to))
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                }
            },
        )
    }
}

/**
 * Where the dragged tile goes when it passed half of [step] (negative right to left) toward a neighbour: the
 * neighbour's index, its offset then kept relative to its new place; null while it has not.
 */
private fun StripDrag.passed(
    position: TilePosition,
    step: Float,
): Int? {
    val ratio = offset.value / step
    val places =
        if (ratio > HALF) {
            1
        } else if (ratio < -HALF) {
            -1
        } else {
            0
        }
    val to = position.index + places
    if (places == 0 || to !in 0 until position.count) return null
    offset.snapTo(offset.value - places * step)
    return to
}

@Composable
private fun PickTile(
    pick: StoryPick,
    position: TilePosition,
    selected: Boolean,
    onEvent: (NewStoryEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sizes = MaterialTheme.sizes
    val shape = MaterialTheme.shapes.large
    val description = tileDescription(pick, position)
    val actions = tileActions(pick.key, position, onEvent)
    val ring = if (selected) Modifier.border(sizes.avatarRing, MaterialTheme.colorScheme.primary, shape) else Modifier
    Box(
        modifier
            .width(sizes.panelSticker)
            .aspectRatio(TILE_ASPECT_RATIO)
            .clip(shape)
            .then(ring)
            .clickable { onEvent(NewStoryEvent.Select(pick.key)) }
            .semantics {
                contentDescription = description
                role = Role.Tab
                this.selected = selected
                customActions = actions
            },
    ) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxSize()) {
            ItemImage(pick.image, Modifier.fillMaxSize())
        }
        val inset = MaterialTheme.spacing.extraSmall
        NumberBadge(position.index + 1, large = false, Modifier.align(Alignment.TopStart).padding(inset))
        // From 150% font the length no longer fits the tile; the tile's description still says it.
        if (pick.isVideo && !largeFont()) {
            DurationLabel(pick.durationSeconds, Modifier.align(Alignment.BottomStart).padding(inset))
        }
    }
}

/** "Photo 2 of 4", or "Video 3 of 4, 0:14". */
@Composable
private fun tileDescription(
    pick: StoryPick,
    position: TilePosition,
): String {
    val number = position.index + 1
    return if (pick.isVideo) {
        stringResource(R.string.new_story_a11y_video, number, position.count, rememberDuration(pick.durationSeconds))
    } else {
        stringResource(R.string.new_story_a11y_photo, number, position.count)
    }
}

/** "Move earlier", "Move later" where there is room, and "Remove from story". */
@Composable
private fun tileActions(
    key: String,
    position: TilePosition,
    onEvent: (NewStoryEvent) -> Unit,
): List<CustomAccessibilityAction> {
    val resources = LocalResources.current
    return remember(key, position, resources, onEvent) {
        buildList {
            if (position.index > 0) {
                add(
                    action(resources.getString(R.string.new_story_move_earlier)) {
                        onEvent(
                            NewStoryEvent.Move(
                                key,
                                position.index - 1,
                            ),
                        )
                    },
                )
            }
            if (position.index < position.count - 1) {
                add(
                    action(resources.getString(R.string.new_story_move_later)) {
                        onEvent(
                            NewStoryEvent.Move(
                                key,
                                position.index + 1,
                            ),
                        )
                    },
                )
            }
            add(action(resources.getString(R.string.new_story_remove)) { onEvent(NewStoryEvent.Remove(key)) })
        }
    }
}

private fun action(
    label: String,
    run: () -> Unit,
) = CustomAccessibilityAction(label) {
    run()
    true
}

/** The tiles are 3:4, as the screen's photos mostly are. */
internal const val TILE_ASPECT_RATIO = 3f / 4f

/** A dragged tile takes a neighbour's place past half a step. */
private const val HALF = 0.5f

/** The keys of the tiles of items still being added. */
private const val PREPARING_KEY = "preparing:"
