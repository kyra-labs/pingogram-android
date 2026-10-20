package dev.kyralabs.pingogram.core.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.InfiniteAnimationPolicy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import dev.kyralabs.pingogram.core.ui.R
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.core.ui.transition.isReducedMotion
import kotlinx.coroutines.delay

/**
 * The Material 3 Expressive empty state (`docs/DESIGN_SYSTEM.md` §6): a [MaterialShapes] illustration
 * with a Material Symbol on it, a `headlineSmallEmphasized` title, a `bodyMedium` body and an optional
 * [action], centered in the available space. It scrolls when it does not fit, e.g. in landscape at 200%
 * font scale.
 *
 * The illustration's shape turns now and then, slowly, while the icon on it stays upright ([rememberTurn]); with
 * reduced motion it stays still.
 *
 * @param illustration the polygon behind [icon], one of [MaterialShapes]: a soft one, and for an error
 *   [MaterialShapes.Cookie4Sided], never the spiky `Burst` (§4).
 * @param icon a Material Symbols Rounded drawable; decorative, so screen readers skip it.
 * @param containerColor the illustration's color, a `*Container` role.
 * @param iconColor the matching `on*Container` role for [icon].
 * @param action the screen's next step, if it has one: a [Button] with [ButtonDefaults.shapes].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EmptyState(
    illustration: RoundedPolygon,
    @DrawableRes icon: Int,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    iconColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    action: (@Composable () -> Unit)? = null,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        // At least as tall as the viewport, so short content is centered and long content scrolls.
        val minHeight = if (constraints.hasBoundedHeight) maxHeight else Dp.Unspecified
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = minHeight)
                    .padding(
                        horizontal = MaterialTheme.spacing.windowMarginCompact,
                        vertical = MaterialTheme.spacing.extraLarge,
                    ),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val turn = rememberTurn()
            Box(modifier = Modifier.size(IllustrationSize), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .matchParentSize()
                        .graphicsLayer { rotationZ = turn() }
                        .background(containerColor, illustration.toShape()),
                )
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    modifier = Modifier.size(IllustrationIconSize),
                    tint = iconColor,
                )
            }
            Spacer(Modifier.height(MaterialTheme.spacing.extraLarge))
            Text(
                text = title,
                modifier = Modifier.widthIn(max = TextMaxWidth).semantics { heading() },
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineSmallEmphasized,
            )
            Spacer(Modifier.height(MaterialTheme.spacing.small))
            Text(
                text = body,
                modifier = Modifier.widthIn(max = TextMaxWidth),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (action != null) {
                Spacer(Modifier.height(MaterialTheme.spacing.extraLarge))
                action()
            }
        }
    }
}

/**
 * The illustration's angle, which turns by [TURN_DEGREES] every [TURN_INTERVAL_MILLIS] on the motion scheme's slow
 * spatial spring; read in the draw phase, so it moves without recomposing, and rests between turns, when nothing draws
 * anew. It stays at 0 with reduced motion, and where infinite animations are off (UI tests).
 */
@Composable
private fun rememberTurn(): () -> Float {
    val rotation = remember { Animatable(0f) }
    if (isReducedMotion()) return { 0f }
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(rotation, spec) {
        val turn: suspend () -> Unit = {
            while (true) {
                delay(TURN_INTERVAL_MILLIS)
                // Kept within a turn, so the angle never grows without bound.
                rotation.snapTo(rotation.value % FULL_TURN)
                rotation.animateTo(rotation.value + TURN_DEGREES, spec)
            }
        }
        val policy = coroutineContext[InfiniteAnimationPolicy]
        if (policy != null) policy.onInfiniteOperation(turn) else turn()
    }
    return remember(rotation) { { rotation.value } }
}

/** How far and how often the illustration turns: an eighth of a turn every few seconds, slow enough to calm. */
private const val TURN_DEGREES = 45f
private const val TURN_INTERVAL_MILLIS = 6_000L
private const val FULL_TURN = 360f

private val IllustrationSize = 128.dp
private val IllustrationIconSize = 56.dp

// Keeps centered lines short enough to read on wide windows.
private val TextMaxWidth = 360.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@PingogramPreviews
@Composable
private fun EmptyStatePreview() {
    PingogramPreview {
        EmptyState(
            illustration = MaterialShapes.Cookie9Sided,
            icon = R.drawable.ic_chat_filled,
            title = "Nothing here yet",
            body = "Content shows up here as soon as there is some.",
            action = {
                Button(onClick = {}, shapes = ButtonDefaults.shapes()) {
                    Text("Refresh", style = MaterialTheme.typography.labelLargeEmphasized)
                }
            },
        )
    }
}
