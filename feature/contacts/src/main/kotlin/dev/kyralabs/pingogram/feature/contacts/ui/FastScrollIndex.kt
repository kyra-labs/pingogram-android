package dev.kyralabs.pingogram.feature.contacts.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.contacts.R
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * The fast-scroll index of a long contacts list (`docs/DESIGN_SYSTEM.md` §6), along its end edge: the sections'
 * [letters] in `labelSmall`, `onSurfaceVariant`, stacked in the middle of the list's height. Built from Material parts,
 * as Material has no fast scroller: a column of text at least 48 dp wide (`minimumInteractiveComponentSize`).
 *
 * - **Touch**: pressing or dragging along it jumps the list ([listState]) to the header of the section under the
 *   finger ([headerIndices]), that letter in `primary` meanwhile, with a `SegmentTick` haptic at each new section, as
 *   a slider's steps have. The whole height counts, above and below the letters too, so the first and last sections
 *   are easy to reach.
 * - **Little room** (a short window, a large font): only every second, third… letter shows, spread over the height;
 *   touch still reaches every section.
 * - **Screen readers** hear one adjustable control, "Alphabetical index", whose value is the section at the top of the
 *   list (read in the semantics block, so scrolling recomposes nothing); swiping up or down on it moves a section.
 *   Only touching it recomposes it.
 */
@Composable
internal fun FastScrollIndex(
    letters: ImmutableList<String>,
    headerIndices: ImmutableList<Int>,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    // The section under the finger; none while the index is not touched.
    var touched by remember { mutableIntStateOf(NONE) }
    val currentHeaders by rememberUpdatedState(headerIndices)
    val jumpTo = { section: Int ->
        if (section != touched) {
            touched = section
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            listState.requestScrollToItem(currentHeaders[section])
        }
    }
    val description = stringResource(R.string.contacts_index)
    val colors = MaterialTheme.colorScheme
    // The letters in a pill, the current one on a `primary` dot (docs/DESIGN_SYSTEM.md §13.8).
    val pill = remember { PillBounds() }
    val current by remember(listState) {
        derivedStateOf { currentSection(listState.firstVisibleItemIndex, currentHeaders) }
    }
    // Read by the letters in the draw phase: touching or scrolling redraws them and recomposes nothing.
    val highlighted = { if (touched != NONE) touched else current }
    val pillColor = colors.surfaceContainer
    val pillPadding = with(LocalDensity.current) { MaterialTheme.spacing.small.toPx() }
    Layout(
        content = {
            letters.forEachIndexed { index, letter ->
                IndexLetter(letter, index, highlighted)
            }
        },
        modifier =
            modifier
                .fillMaxHeight()
                .minimumInteractiveComponentSize()
                .pill(pill, pillColor, pillPadding)
                .indexTouch(count = letters.size, onSection = jumpTo, onRelease = { touched = NONE })
                .clearAndSetSemantics {
                    contentDescription = description
                    val current = currentSection(listState.firstVisibleItemIndex, currentHeaders)
                    stateDescription = letters[current]
                    progressBarRangeInfo =
                        ProgressBarRangeInfo(
                            current = current.toFloat(),
                            range = 0f..(letters.size - 1).toFloat(),
                            steps = (letters.size - 2).coerceAtLeast(0),
                        )
                    setProgress { target ->
                        val section = target.roundToInt().coerceIn(0, letters.size - 1)
                        listState.requestScrollToItem(currentHeaders[section])
                        true
                    }
                },
        measurePolicy = remember(pill) { indexMeasurePolicy(pill) },
    )
}

/**
 * A letter of the index in `labelSmall`: `onSurfaceVariant`, or while [highlighted] names its [index] `onPrimary` on a
 * `primary` dot. Both read [highlighted] in the draw phase.
 */
@Composable
private fun IndexLetter(
    letter: String,
    index: Int,
    highlighted: () -> Int,
) {
    val colors = MaterialTheme.colorScheme
    val dotColor = colors.primary
    BasicText(
        text = letter,
        modifier =
            Modifier.drawBehind {
                if (highlighted() == index) drawCircle(dotColor, radius = maxOf(size.width, size.height) / 2)
            },
        style = MaterialTheme.typography.labelSmall.copy(textAlign = TextAlign.Center),
        maxLines = 1,
        color = { if (highlighted() == index) colors.onPrimary else colors.onSurfaceVariant },
    )
}

/**
 * Presses and drags along the index: [onSection] with the section of [count] under the finger, the whole height
 * divided evenly among them, as the finger goes down and as it moves; [onRelease] once it lifts.
 */
private fun Modifier.indexTouch(
    count: Int,
    onSection: (Int) -> Unit,
    onRelease: () -> Unit,
): Modifier =
    pointerInput(count) {
        fun sectionAt(y: Float): Int = (y / size.height * count).toInt().coerceIn(0, count - 1)
        awaitEachGesture {
            val down = awaitFirstDown()
            down.consume()
            onSection(sectionAt(down.position.y))
            do {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                if (change.pressed) {
                    change.consume()
                    onSection(sectionAt(change.position.y))
                }
            } while (change.pressed)
            onRelease()
        }
    }

/**
 * The letters stacked in the middle of the index's height, each centered in its width; where they do not all fit,
 * every n-th of them, spread over the whole height. With [pill], where they lie is kept there for [Modifier.pill].
 */
private fun indexMeasurePolicy(pill: PillBounds? = null) =
    MeasurePolicy { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minHeight = 0, minWidth = 0)) }
        val width = maxOf(constraints.minWidth, placeables.maxOfOrNull { it.width } ?: 0)
        val height = constraints.maxHeight
        val letterHeight = placeables.maxOfOrNull { it.height } ?: 0
        val every = ceil(placeables.size * letterHeight.toFloat() / height.coerceAtLeast(1)).toInt().coerceAtLeast(1)
        val shown = placeables.indices.filter { it % every == 0 }
        val step = if (every == 1) letterHeight.toFloat() else height.toFloat() / shown.size
        val top = (height - step * shown.size) / 2
        pill?.let {
            it.top = top
            it.bottom = top + step * shown.size
            it.width = placeables.maxOfOrNull { placeable -> maxOf(placeable.width, placeable.height) } ?: 0
        }
        layout(width, height) {
            shown.forEachIndexed { position, index ->
                val placeable = placeables[index]
                val y = top + step * position + (step - placeable.height) / 2
                placeable.place((width - placeable.width) / 2, y.roundToInt())
            }
        }
    }

/** Where the letters of the index lie: written while measuring, read while drawing. */
private class PillBounds {
    var top = 0f
    var bottom = 0f
    var width = 0
}

/** The index's pill in [color] around the letters ([bounds]), [padding] beyond them, read in draw. */
private fun Modifier.pill(
    bounds: PillBounds,
    color: Color,
    padding: Float,
): Modifier =
    drawBehind {
        val width = bounds.width + padding
        val height = bounds.bottom - bounds.top + padding * 2
        if (bounds.bottom <= bounds.top) return@drawBehind
        drawRoundRect(
            color = color,
            topLeft = Offset((size.width - width) / 2, bounds.top - padding),
            size = Size(width, height),
            cornerRadius = CornerRadius(width / 2),
        )
    }

/** The section whose header is the last at or above [firstVisibleItem], the list's top item. */
private fun currentSection(
    firstVisibleItem: Int,
    headerIndices: List<Int>,
): Int = headerIndices.indexOfLast { it <= firstVisibleItem }.coerceAtLeast(0)

private const val NONE = -1

private val previewLetters = persistentListOf("A", "B", "C", "D", "E", "G", "H", "J", "M", "#")

@PingogramPreviews
@Composable
private fun FastScrollIndexPreview() {
    PingogramPreview {
        FastScrollIndex(
            letters = previewLetters,
            headerIndices = previewLetters.indices.map { it * 2 }.toImmutableList(),
            listState = rememberLazyListState(),
        )
    }
}
