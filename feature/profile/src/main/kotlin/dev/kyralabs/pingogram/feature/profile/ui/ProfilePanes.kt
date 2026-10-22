package dev.kyralabs.pingogram.feature.profile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.SupportingPaneScaffold
import androidx.compose.material3.adaptive.layout.SupportingPaneScaffoldDefaults
import androidx.compose.material3.adaptive.layout.SupportingPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldDestinationItem
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.calculateThreePaneScaffoldValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.component.EmptyState
import dev.kyralabs.pingogram.core.ui.component.paneContainer
import dev.kyralabs.pingogram.core.ui.component.plusBottom
import dev.kyralabs.pingogram.core.ui.component.topOnly
import dev.kyralabs.pingogram.core.ui.component.windowMargin
import dev.kyralabs.pingogram.core.ui.component.withTop
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.preview.PingogramVariantPreviews
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.profile.R
import dev.kyralabs.pingogram.feature.profile.ui.preview.ProfilePreviewData
import dev.kyralabs.pingogram.core.ui.R as UiR

/** The profile and its shared media side by side, as Material's supporting pane layout. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun ProfilePanes(
    page: ProfilePageParts,
    onBack: (() -> Unit)?,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier,
    gridState: LazyGridState,
) {
    val directive = calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2())
    val value =
        calculateThreePaneScaffoldValue(
            maxHorizontalPartitions = directive.maxHorizontalPartitions,
            adaptStrategies = SupportingPaneScaffoldDefaults.adaptStrategies(),
            currentDestination =
                remember {
                    ThreePaneScaffoldDestinationItem<Nothing>(
                        SupportingPaneScaffoldRole.Main,
                    )
                },
        )
    SupportingPaneScaffold(
        directive = directive,
        value = value,
        mainPane = {
            AnimatedPane(modifier = Modifier.paneContainer(twoPanes = true, last = false)) {
                ProfilePage(page, onBack, snackbarHostState, Modifier, rememberLazyGridState(), withMedia = false)
            }
        },
        supportingPane = {
            AnimatedPane(modifier = Modifier.paneContainer(twoPanes = true, last = true)) {
                SharedMediaPane(page, gridState)
            }
        },
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainer),
    )
}

/**
 * The supporting pane: "Shared media" in a small top bar on the pane's color, the tabs below it, and the shown tab's
 * items in a grid of their own.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SharedMediaPane(
    page: ProfilePageParts,
    gridState: LazyGridState,
) {
    Scaffold(
        containerColor = profilePageColor(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.profile_section_media),
                        modifier = Modifier.semantics { heading() },
                        style = MaterialTheme.typography.titleLargeEmphasized,
                    )
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                    ),
            )
        },
    ) { innerPadding ->
        SharedMediaPaneBody(page, innerPadding, gridState)
    }
}

@Composable
private fun SharedMediaPaneBody(
    page: ProfilePageParts,
    innerPadding: PaddingValues,
    gridState: LazyGridState,
) {
    val margin = windowMargin()
    val sizes = MaterialTheme.sizes
    val extraLarge = MaterialTheme.spacing.extraLarge
    SharedMediaLoadMoreEffect(gridState, page.media, page.onMediaEvent)
    BoxWithConstraints(Modifier.fillMaxSize().consumeWindowInsets(innerPadding)) {
        val columns = (maxWidth / sizes.sharedMediaCell).toInt().coerceAtLeast(1)
        val cellSizePx = with(LocalDensity.current) { (maxWidth / columns).roundToPx() }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize().padding(remember(innerPadding) { innerPadding.topOnly() }),
            state = gridState,
            contentPadding =
                remember(innerPadding, extraLarge) { innerPadding.withTop(0.dp).plusBottom(extraLarge) },
        ) {
            sharedMediaItems(page.media, page.onMediaEvent, page.mediaActions, cellSizePx, margin)
        }
    }
}
