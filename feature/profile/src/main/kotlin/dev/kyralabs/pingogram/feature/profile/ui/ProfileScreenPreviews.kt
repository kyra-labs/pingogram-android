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

@Composable
private fun ProfileScreenPreviewContent(
    state: ProfileUiState,
    media: SharedMediaUiState = ProfilePreviewData.media,
    twoPanes: Boolean = false,
) {
    PingogramPreview {
        ProfileScreen(
            state = state,
            media = media,
            onEvent = {},
            onMediaEvent = {},
            onBack = {},
            mediaActions = SharedMediaActions({ _, _ -> }, { _, _ -> }, {}),
            onEditContact = {},
            twoPanes = twoPanes,
        )
    }
}

@PingogramPreviews
@PingogramVariantPreviews
@Composable
private fun ProfileScreenPreview() {
    ProfileScreenPreviewContent(ProfilePreviewData.content)
}

@PingogramPreviews
@Composable
private fun ProfileScreenLoadingPreview() {
    ProfileScreenPreviewContent(ProfileUiState.Loading)
}

@PingogramPreviews
@Composable
private fun ProfileScreenErrorPreview() {
    ProfileScreenPreviewContent(ProfileUiState.Error(DomainError.NotFound))
}

@PingogramPreviews
@Composable
private fun ProfileScreenBotPreview() {
    ProfileScreenPreviewContent(ProfilePreviewData.bot, ProfilePreviewData.emptyMedia)
}

@PingogramPreviews
@Composable
private fun ProfileScreenDeletedPreview() {
    ProfileScreenPreviewContent(ProfilePreviewData.deleted, ProfilePreviewData.loadingMedia)
}

@PingogramPreviews
@Composable
private fun ProfileScreenFilesPreview() {
    ProfileScreenPreviewContent(ProfilePreviewData.scam, ProfilePreviewData.files)
}

@Preview(name = "Expanded", widthDp = EXPANDED_PREVIEW_WIDTH, heightDp = EXPANDED_PREVIEW_HEIGHT)
@Composable
private fun ProfileScreenExpandedPreview() {
    ProfileScreenPreviewContent(ProfilePreviewData.content, twoPanes = true)
}

private const val EXPANDED_PREVIEW_WIDTH = 1000
private const val EXPANDED_PREVIEW_HEIGHT = 800
