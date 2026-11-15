package dev.kyralabs.pingogram.feature.chat.ui.drop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The drop zone over the messages while files are dragged (`docs/DESIGN_SYSTEM.md` §6 "Drag and drop", board 35a):
 * the whole area tinted with `primaryContainer` and edged with a dashed `primary` border in `extraLarge` corners,
 * fading in on the effects springs; a pane for screen readers, announced politely. Keyboard and switch users paste or
 * attach instead.
 */
@Composable
internal fun ChatDropZone(
    state: ChatDropState,
    chatTitle: String,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = state.isActive,
        modifier = modifier,
        enter = fadeIn(motion.defaultEffectsSpec()),
        exit = fadeOut(motion.fastEffectsSpec()),
    ) {
        val border = MaterialTheme.colorScheme.primary
        val dash = MaterialTheme.spacing.small
        val width = MaterialTheme.sizes.avatarRing
        val shape = MaterialTheme.shapes.extraLarge
        val title = stringResource(R.string.drop_title, chatTitle)
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(MaterialTheme.spacing.small)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = ZONE_ALPHA), shape)
                    .drawWithCache {
                        val stroke =
                            Stroke(
                                width = width.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash.toPx(), dash.toPx())),
                            )
                        val outline = shape.createOutline(size, layoutDirection, this)
                        onDrawBehind { drawOutline(outline, border, style = stroke) }
                    }.semantics {
                        paneTitle = title
                        liveRegion = LiveRegionMode.Polite
                    },
            contentAlignment = Alignment.Center,
        ) {
            DropZoneContent(state, title)
        }
    }
}

/**
 * The attach symbol on a `MaterialShapes.Cookie9Sided` badge (`headlineBadge`) that grows while the drag is over the
 * messages (on the motion scheme's fast spatial spring, in its graphics layer), [title] ("Drop to send to Ada") in
 * `titleLargeEmphasized` over what is coming in `bodyMedium`.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DropZoneContent(
    state: ChatDropState,
    title: String,
) {
    val scale by animateFloatAsState(
        targetValue = if (state.isHovered) HOVERED_SCALE else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "dropBadge",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        modifier = Modifier.padding(MaterialTheme.spacing.large),
    ) {
        Box(
            modifier =
                Modifier
                    .size(MaterialTheme.sizes.headlineBadge)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }.clip(MaterialShapes.Cookie9Sided.toShape())
                    .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(UiR.drawable.ic_add),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLargeEmphasized.userContent,
        )
        Text(
            text = stringResource(if (state.isMedia) R.string.drop_media else R.string.drop_files),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private const val HOVERED_SCALE = 1.2f
private const val ZONE_ALPHA = 0.9f

@PingogramPreviews
@Composable
private fun ChatDropZonePreview() {
    PingogramPreview {
        Box(Modifier.fillMaxSize()) {
            ChatDropZone(ChatDropState(isActive = true, isHovered = true, isMedia = true), chatTitle = "Ada Lovelace")
        }
    }
}
