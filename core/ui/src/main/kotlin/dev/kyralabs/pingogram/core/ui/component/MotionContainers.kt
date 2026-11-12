package dev.kyralabs.pingogram.core.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.ui.theme.growInVertically
import dev.kyralabs.pingogram.core.ui.theme.shrinkOutVertically
import dev.kyralabs.pingogram.core.ui.theme.stateCrossfade

/**
 * One state replacing another in place (`docs/DESIGN_SYSTEM.md` §5): an `AnimatedContent` whose new content fades in
 * on the theme's default effects spring as the old fades out on its fast one, the size changing on the default
 * spatial spring ([stateCrossfade]), clipped only if [clip]. [contentKey] says which states are the same content, so a
 * change within one (a count, an error) does not crossfade.
 */
@Composable
fun <T> StateCrossfade(
    targetState: T,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    clip: Boolean = false,
    contentKey: (T) -> Any? = { it },
    label: String = "StateCrossfade",
    content: @Composable AnimatedContentScope.(T) -> Unit,
) {
    val motionScheme = MaterialTheme.motionScheme
    val transitionSpec = remember(motionScheme, clip) { stateCrossfade<T>(motionScheme, clip) }
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        transitionSpec = transitionSpec,
        contentAlignment = contentAlignment,
        label = label,
        contentKey = contentKey,
        content = content,
    )
}

/**
 * Content that takes room as it comes and gives it back as it goes (a bar, a banner, a row): its height grows from
 * [expandFrom] on the theme's default spatial spring with a fade in, and shrinks back towards the same edge on the
 * default spatial spring, or the fast one where `docs/DESIGN_SYSTEM.md` §6 names a fast exit ([fastExit]), with a
 * fast fade out ([growInVertically], [shrinkOutVertically]).
 */
@Composable
fun GrowingVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    expandFrom: Alignment.Vertical = Alignment.Top,
    fastExit: Boolean = false,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    val motionScheme = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = remember(motionScheme, expandFrom) { growInVertically(motionScheme, expandFrom) },
        exit = remember(motionScheme, expandFrom, fastExit) { shrinkOutVertically(motionScheme, expandFrom, fastExit) },
        content = content,
    )
}

/**
 * A place that is always laid out, so nothing around it moves when [content] comes and goes (`docs/DESIGN_SYSTEM.md`
 * §5.1: progress while Telegram answers sits in a slot whose height is kept). [content] fades in on the theme's
 * default effects spring and out on its fast one, its opacity read in the draw phase, so the fade recomposes nothing
 * (`docs/PERFORMANCE.md` §4); while hidden it is out of the semantics tree.
 *
 * [content] stays composed while hidden, as its size is the slot's: a loop inside it (an indeterminate indicator)
 * keeps running unless the caller pauses it.
 */
@Composable
fun ReservedSlot(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val motionScheme = MaterialTheme.motionScheme
    val alpha =
        animateFloatAsState(
            targetValue = if (visible) 1f else 0f,
            animationSpec = if (visible) motionScheme.defaultEffectsSpec() else motionScheme.fastEffectsSpec(),
            label = "ReservedSlot",
        )
    Box(
        modifier
            .graphicsLayer { this.alpha = alpha.value }
            .then(if (visible) Modifier else Modifier.clearAndSetSemantics {}),
    ) {
        content()
    }
}

/**
 * The wavy line of a request on its way (`docs/DESIGN_SYSTEM.md` §5.1: progress while Telegram answers sits in a slot
 * whose height is always kept, above the buttons, so no button moves): a [LinearWavyProgressIndicator] as wide as its
 * parent, fading in on the theme's default effects spring and out on its fast one, its opacity read in the draw phase.
 * Screen readers hear [contentDescription] while it is [visible], and nothing while it is not.
 *
 * Unlike [ReservedSlot], it keeps only the line's height while hidden, not the indicator: its loop runs while it shows
 * or fades out, never on a page at rest.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReservedProgressLine(
    visible: Boolean,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val motionScheme = MaterialTheme.motionScheme
    val alpha =
        animateFloatAsState(
            targetValue = if (visible) 1f else 0f,
            animationSpec = if (visible) motionScheme.defaultEffectsSpec() else motionScheme.fastEffectsSpec(),
            label = "ReservedProgressLine",
        )
    // True while any of the line shows; changes twice per request, not per frame.
    val fading by remember { derivedStateOf { alpha.value > 0f } }
    Box(
        modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha.value }
            .then(if (visible) Modifier else Modifier.clearAndSetSemantics {}),
    ) {
        if (visible || fading) {
            LinearWavyProgressIndicator(
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (contentDescription != null) {
                            Modifier.semantics { this.contentDescription = contentDescription }
                        } else {
                            Modifier
                        },
                    ),
            )
        } else {
            Spacer(Modifier.fillMaxWidth().height(WavyProgressIndicatorDefaults.LinearContainerHeight))
        }
    }
}
