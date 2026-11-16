package dev.kyralabs.pingogram.feature.media.ui.zoom

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.core.os.ConfigurationCompat
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.media.R
import kotlinx.coroutines.delay
import java.text.NumberFormat
import kotlin.math.roundToInt

/**
 * What shows in the toolbar's place while the photo is zoomed in (board 25): the zoom level as a chip ("2.5×",
 * `labelLargeEmphasized` with tabular figures), which fades out a moment after the zoom last changed, above a pill on
 * `mediaOverlay` with a mini-map of the photo, the part on screen outlined, and "Double-tap to fit". Shown while
 * [visible] and [zoom] is zoomed, fading in on the default and out on the fast effects spring. Hidden from screen
 * readers: the page says "Zoomed in", and its zoom actions are theirs. The mini-map is drawn in the draw phase, so
 * panning never recomposes it; the level recomposes only when it changes by a tenth.
 */
@Composable
internal fun ZoomIndicator(
    zoom: ZoomState,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    val zoomed by remember(zoom) { derivedStateOf { zoom.isZoomed } }
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = visible && zoomed,
        modifier = modifier.clearAndSetSemantics {},
        enter = fadeIn(motion.defaultEffectsSpec()),
        exit = fadeOut(motion.fastEffectsSpec()),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            ZoomLevel(zoom)
            Row(
                modifier =
                    Modifier
                        .background(MaterialTheme.pingogramColors.mediaOverlay, CircleShape)
                        .padding(MaterialTheme.spacing.small)
                        .padding(end = MaterialTheme.spacing.small),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            ) {
                MiniMap(zoom)
                Text(
                    text = stringResource(R.string.media_zoom_fit_hint),
                    color = MaterialTheme.pingogramColors.onMediaOverlay,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

/** [ZoomIndicator] where the viewer's toolbar is: at the bottom, centred, above the navigation bar. */
@Composable
internal fun BoxScope.ViewerZoomIndicator(
    zoom: ZoomState,
    visible: Boolean,
) {
    ZoomIndicator(
        zoom = zoom,
        visible = visible,
        modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .padding(bottom = MaterialTheme.spacing.large),
    )
}

/** The zoom level, to a tenth, in the chip's style; shown while it changes and fading out once it stays. */
@Composable
private fun ZoomLevel(zoom: ZoomState) {
    val tenths by remember(zoom) { derivedStateOf { (zoom.zoom.scale * TENTHS).roundToInt() } }
    var shown by remember { mutableStateOf(true) }
    LaunchedEffect(tenths) {
        shown = true
        delay(LEVEL_SHOWN_MILLIS)
        shown = false
    }
    val motion = MaterialTheme.motionScheme
    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = if (shown) motion.defaultEffectsSpec() else motion.fastEffectsSpec(),
        label = "zoomLevel",
    )
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0]
    val format =
        remember(locale) {
            (locale?.let(NumberFormat::getNumberInstance) ?: NumberFormat.getNumberInstance()).apply {
                minimumFractionDigits = 1
                maximumFractionDigits = 1
            }
        }
    val onOverlay = MaterialTheme.pingogramColors.onMediaOverlay
    Text(
        text = stringResource(R.string.media_zoom_level, format.format(tenths / TENTHS.toDouble())),
        modifier =
            Modifier
                .graphicsLayer { this.alpha = alpha }
                .background(MaterialTheme.pingogramColors.mediaOverlay, CircleShape)
                .heightIn(min = FilterChipDefaults.Height)
                .wrapContentHeight()
                .padding(horizontal = MaterialTheme.spacing.medium),
        color = onOverlay,
        maxLines = 1,
        style = MaterialTheme.typography.labelLargeEmphasized.copy(fontFeatureSettings = "tnum"),
    )
}

/**
 * The photo in small, its longer side `sizes.leadingIconContainer`, its shape as `onMediaOverlay` at a sixth of its
 * strength, and the part the page shows outlined in `onMediaOverlay`; drawn in the draw phase from [zoom].
 */
@Composable
private fun MiniMap(zoom: ZoomState) {
    val bounds = zoom.bounds
    val longer = MaterialTheme.sizes.leadingIconContainer
    val content = bounds?.content
    val ratio = if (content == null || content.height <= 0f) 1f else content.width / content.height
    val (width, height) = if (ratio >= 1f) longer to longer / ratio else longer * ratio to longer
    val onOverlay = MaterialTheme.pingogramColors.onMediaOverlay
    val stroke = MaterialTheme.sizes.avatarRing
    val corner = MaterialTheme.shapes.extraSmall
    Box(
        Modifier
            .size(width, height)
            .background(onOverlay.copy(alpha = MAP_ALPHA), corner)
            .drawBehind {
                val current = zoom.bounds ?: return@drawBehind
                val part = visiblePart(current, zoom.zoom)
                val width = stroke.toPx()
                val topLeft = Offset(part.left * this.size.width, part.top * this.size.height)
                val partSize = Size(part.width * this.size.width, part.height * this.size.height)
                drawRoundRect(
                    color = onOverlay,
                    topLeft = topLeft + Offset(width / 2, width / 2),
                    size = Size(partSize.width - width, partSize.height - width),
                    cornerRadius = CornerRadius(width, width),
                    style = Stroke(width),
                )
            },
    )
}

/** How long the zoom level stays after it last changed, before it fades out. */
private const val LEVEL_SHOWN_MILLIS = 1_500L

/** The level is shown to a tenth. */
private const val TENTHS = 10

/** How strongly the mini-map shows the whole photo. */
private const val MAP_ALPHA = 0.16f
