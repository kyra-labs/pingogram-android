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

/**
 * The tabs of the shared media: a connected [ButtonGroup] of [ToggleButton]s, as the chat list's folder tabs
 * (`docs/DESIGN_SYSTEM.md` §6 "Folder tabs"), in a horizontal scroll that brings the selected tab into view and fades
 * at its edges, so the group never overflows into its menu. The tabs switch what the page shows below them, as the
 * folder tabs switch the chat list; six of them fit a connected group (more would be a `PrimaryScrollableTabRow`), and
 * one style for tabs keeps the app consistent. The selected label is `labelLargeEmphasized`; unchecked buttons on
 * `surfaceContainerHighest`, a step above the page. Screen readers hear tabs.
 */
@Composable
internal fun SharedMediaTabRow(
    selected: SharedMediaKind,
    onSelect: (SharedMediaKind) -> Unit,
    modifier: Modifier = Modifier,
    margin: Dp = MaterialTheme.spacing.windowMarginCompact,
) {
    val colors = ToggleButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
    val tabs = SharedMediaTabs
    val labels = tabs.map { it.label() }
    val scroll = rememberTabScroll(tabs.size, selectedIndex = tabs.indexOf(selected), margin)
    ButtonGroup(
        // Never measured: the group always fits in its scroll.
        overflowIndicator = {},
        modifier =
            modifier
                .fillMaxWidth()
                .fadingEdges(scroll.state)
                .horizontalScroll(scroll.state)
                .selectableGroup()
                .padding(horizontal = margin, vertical = MaterialTheme.spacing.extraSmall),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEachIndexed { index, kind ->
            val isSelected = kind == selected
            customItem(
                buttonGroupContent = {
                    val interactionSource = remember { MutableInteractionSource() }
                    ToggleButton(
                        checked = isSelected,
                        onCheckedChange = { onSelect(kind) },
                        modifier =
                            Modifier
                                .animateWidth(interactionSource)
                                .onPlaced { scroll.buttonBounds[index] = it.boundsInParent() }
                                .semantics { role = Role.Tab },
                        shapes = connectedShapes(index, tabs.lastIndex),
                        colors = colors,
                        interactionSource = interactionSource,
                    ) {
                        val typography = MaterialTheme.typography
                        Text(
                            text = labels[index],
                            maxLines = 1,
                            style = if (isSelected) typography.labelLargeEmphasized else typography.labelLarge,
                        )
                    }
                },
                menuContent = { menu ->
                    DropdownMenuItem(
                        text = { Text(labels[index]) },
                        onClick = {
                            onSelect(kind)
                            menu.dismiss()
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun connectedShapes(
    index: Int,
    lastIndex: Int,
): ToggleButtonShapes =
    when (index) {
        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
        lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
    }

@Composable
@ReadOnlyComposable
private fun SharedMediaKind.label(): String =
    stringResource(
        when (this) {
            SharedMediaKind.PhotosAndVideos -> R.string.profile_media_photos
            SharedMediaKind.Files -> R.string.profile_media_files
            SharedMediaKind.Links -> R.string.profile_media_links
            SharedMediaKind.Voice -> R.string.profile_media_voice
            SharedMediaKind.Music -> R.string.profile_media_music
            SharedMediaKind.Gifs -> R.string.profile_media_gifs
        },
    )
