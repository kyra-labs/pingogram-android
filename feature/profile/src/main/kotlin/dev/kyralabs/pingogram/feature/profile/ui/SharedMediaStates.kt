package dev.kyralabs.pingogram.feature.profile.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.graphics.shapes.RoundedPolygon
import dev.kyralabs.pingogram.core.domain.model.SharedMediaKind
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.component.fadingEdges
import dev.kyralabs.pingogram.core.ui.component.rememberTabScroll
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.profile.R
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import dev.kyralabs.pingogram.core.ui.R as UiR

/** The tab's content on its way: the delayed loading indicator (`docs/DESIGN_SYSTEM.md` §5). */
@Composable
internal fun MediaLoading() {
    val description = stringResource(R.string.profile_media_loading)
    Box(Modifier.fillMaxWidth().padding(MaterialTheme.spacing.extraLarge), contentAlignment = Alignment.Center) {
        DelayedLoadingIndicator(Modifier.semantics { contentDescription = description })
    }
}

/**
 * The chat has nothing of the tab's kind: the tab's icon on a soft `Cookie9Sided` badge in `primaryContainer`, as the
 * app's empty states have it (`EmptyState`, which fills a screen and so cannot sit in a lazy list), a
 * `titleMediumEmphasized` title and a `bodyMedium` line.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MediaEmpty(kind: SharedMediaKind) {
    val colors = MaterialTheme.colorScheme
    MediaNotice(
        shape = MaterialShapes.Cookie9Sided,
        icon = kind.icon,
        container = colors.primaryContainer,
        content = colors.onPrimaryContainer,
        title = stringResource(kind.emptyTitle),
        body = stringResource(R.string.profile_media_empty_body),
    )
}

/** The tab's first page failed: the error icon on `Cookie4Sided` in `errorContainer`, why, and "Try again". */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MediaError(
    error: SharedMediaContent.Error,
    onRetry: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    MediaNotice(
        shape = MaterialShapes.Cookie4Sided,
        icon = UiR.drawable.ic_error,
        container = colors.errorContainer,
        content = colors.onErrorContainer,
        title = stringResource(R.string.profile_media_error),
        body = errorText(error.error),
    ) {
        Button(onClick = onRetry, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.profile_retry), style = MaterialTheme.typography.labelLargeEmphasized)
        }
    }
}

/** A notice in place of a tab's items: a `MaterialShapes` badge with [icon], [title], [body] and an [action]. */
@Suppress("LongParameterList") // The badge's shape, icon and colors, and the texts and action.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MediaNotice(
    shape: RoundedPolygon,
    @DrawableRes icon: Int,
    container: Color,
    content: Color,
    title: String,
    body: String,
    action: (@Composable () -> Unit)? = null,
) {
    val spacing = MaterialTheme.spacing
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.extraLarge, vertical = spacing.extraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        val badgeShape = shape.toShape()
        Box(
            modifier = Modifier.size(MaterialTheme.sizes.headlineBadge).background(container, badgeShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painter = painterResource(icon), contentDescription = null, tint = content)
        }
        Text(
            text = title,
            modifier = Modifier.semantics { heading() },
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMediumEmphasized,
        )
        Text(
            text = body,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        action?.invoke()
    }
}

/** The next page failed: why, and "Try again", at the end of the list. */
@Composable
internal fun MoreError(
    more: MoreMedia.Failed,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = errorText(more.error),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = onRetry, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.profile_retry)) }
    }
}

private val SharedMediaKind.icon: Int
    get() =
        when (this) {
            SharedMediaKind.PhotosAndVideos -> UiR.drawable.ic_photo_library_filled
            SharedMediaKind.Files -> UiR.drawable.ic_description_filled
            SharedMediaKind.Links -> UiR.drawable.ic_link
            SharedMediaKind.Voice -> UiR.drawable.ic_mic_filled
            SharedMediaKind.Music -> UiR.drawable.ic_audio_file_filled
            SharedMediaKind.Gifs -> UiR.drawable.ic_image_filled
        }

private val SharedMediaKind.emptyTitle: Int
    get() =
        when (this) {
            SharedMediaKind.PhotosAndVideos -> R.string.profile_media_empty_photos
            SharedMediaKind.Files -> R.string.profile_media_empty_files
            SharedMediaKind.Links -> R.string.profile_media_empty_links
            SharedMediaKind.Voice -> R.string.profile_media_empty_voice
            SharedMediaKind.Music -> R.string.profile_media_empty_music
            SharedMediaKind.Gifs -> R.string.profile_media_empty_gifs
        }
