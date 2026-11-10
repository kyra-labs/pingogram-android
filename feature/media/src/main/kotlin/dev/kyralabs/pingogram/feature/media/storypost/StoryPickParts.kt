package dev.kyralabs.pingogram.feature.media.storypost

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.media.R
import dev.kyralabs.pingogram.feature.media.ui.formatDuration
import java.io.File
import dev.kyralabs.pingogram.core.ui.R as UiR

// The parts of the picking screen that its large preview and its strip of tiles share.

/** The image file at [path], cropped to fill; a still for a video without one. Decorative. */
@Composable
internal fun ItemImage(
    path: String?,
    modifier: Modifier = Modifier,
) {
    if (path == null) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Icon(
                painterResource(UiR.drawable.ic_videocam),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val context = LocalPlatformContext.current
    val request = remember(context, path) { ImageRequest.Builder(context).data(File(path)).build() }
    AsyncImage(model = request, contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
}

/** The item's place in the story, on a `primary` circle; [large] on the shown item. */
@Composable
internal fun NumberBadge(
    number: Int,
    large: Boolean,
    modifier: Modifier = Modifier,
) {
    val size = if (large) MaterialTheme.sizes.avatarExtraSmall else MaterialTheme.sizes.avatarBadge
    val typography = MaterialTheme.typography
    Box(
        modifier
            .sizeIn(minWidth = size, minHeight = size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Text(
            number.toString(),
            style = (if (large) typography.labelLargeEmphasized else typography.labelSmallEmphasized).tabular(),
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.padding(horizontal = MaterialTheme.spacing.extraSmall),
        )
    }
}

/** A video's length, "0:14", on a `mediaOverlay` pill. Decorative: the item's description says it. */
@Composable
internal fun DurationLabel(
    seconds: Int,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.pingogramColors
    Text(
        rememberDuration(seconds),
        style = MaterialTheme.typography.labelSmall.tabular(),
        color = colors.onMediaOverlay,
        modifier =
            modifier
                .clip(CircleShape)
                .background(colors.mediaOverlay)
                .padding(horizontal = MaterialTheme.spacing.small, vertical = MaterialTheme.spacing.extraSmall)
                .clearAndSetSemantics {},
    )
}

/** A photo or video still being copied: a `LoadingIndicator` in a tile's place. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PreparingTile() {
    val description = stringResource(R.string.new_story_a11y_preparing)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = MaterialTheme.shapes.large,
        modifier =
            Modifier
                .width(MaterialTheme.sizes.panelSticker)
                .aspectRatio(TILE_ASPECT_RATIO)
                .clearAndSetSemantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) { LoadingIndicator() }
    }
}

/** "Add more": the photo picker again, for the places left. An `outline` border, `large` corners. */
@Composable
internal fun AddMoreTile(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(MaterialTheme.sizes.avatarRing, MaterialTheme.colorScheme.outline),
        modifier = Modifier.width(MaterialTheme.sizes.panelSticker).aspectRatio(TILE_ASPECT_RATIO),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall, Alignment.CenterVertically),
            modifier = Modifier.padding(MaterialTheme.spacing.extraSmall),
        ) {
            val label = stringResource(R.string.new_story_add_more)
            // From 150% font the label no longer fits the tile: the symbol alone, named for screen readers.
            if (largeFont()) {
                Icon(painterResource(UiR.drawable.ic_add), contentDescription = label)
            } else {
                Icon(painterResource(UiR.drawable.ic_add), contentDescription = null)
                Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
            }
        }
    }
}

/** A video's length in the screen's language, "0:14". */
@Composable
internal fun rememberDuration(seconds: Int): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(seconds, locale) { formatDuration(seconds, locale) }
}

/** Whether the font is scaled to 150% or more, where the tiles' labels no longer fit. */
@Composable
internal fun largeFont(): Boolean = LocalDensity.current.fontScale >= LARGE_FONT_SCALE

/** With tabular figures, so numbers keep their width. */
internal fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

private const val LARGE_FONT_SCALE = 1.5f
