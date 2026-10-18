package dev.kyralabs.pingogram.feature.chats.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.domain.model.ConnectionStatus
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chats.R

/**
 * The top bar, and the folder tabs below it on the same container. The bar's expanded height makes room for the
 * connection pill on the motion scheme's spatial spring: a parameter, so the bar recomposes for those few frames of a
 * connection change, while scrolling only redraws.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ChatListTopBar(
    state: ChatListUiState,
    scrollBehavior: TopAppBarScrollBehavior,
    onSelectFolder: (Int?) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val showPill = state.connection != ConnectionStatus.Ready
    val expandedHeight by animateDpAsState(
        targetValue =
            if (showPill) {
                TopAppBarDefaults.MediumFlexibleAppBarWithSubtitleExpandedHeight
            } else {
                TopAppBarDefaults.MediumFlexibleAppBarWithoutSubtitleExpandedHeight
            },
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "expandedHeight",
    )
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawRect(lerp(colors.surface, colors.surfaceContainer, scrollBehavior.state.collapsedFraction))
                },
        contentAlignment = Alignment.TopCenter,
    ) {
        // As wide as the rows below, so the title and the tabs line up with them on wide windows.
        Column(Modifier.widthIn(max = MaterialTheme.sizes.contentMaxWidth)) {
            MediumFlexibleTopAppBar(
                title = { ChatListTitle() },
                subtitle = { ConnectionPill(state.connection) },
                expandedHeight = expandedHeight,
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                    ),
                scrollBehavior = scrollBehavior,
            )
            if (state.folderTabs.isNotEmpty()) {
                FolderTabs(
                    tabs = state.folderTabs,
                    selectedFolderId = state.selectedFolderId,
                    onSelect = onSelectFolder,
                )
            }
        }
    }
}

/**
 * "Pingogram" in the emphasized variant of the style the bar sets: `headlineMedium` while expanded, `titleLarge`
 * collapsed (`docs/DESIGN_SYSTEM.md` §3.3). The bar composes the title once for each state.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChatListTitle() {
    val typography = MaterialTheme.typography
    val collapsed = LocalTextStyle.current.fontSize == typography.titleLarge.fontSize
    Text(
        text = stringResource(R.string.chat_list_title),
        modifier = Modifier.semantics { heading() },
        maxLines = 1,
        style = if (collapsed) typography.titleLargeEmphasized else typography.headlineMediumEmphasized,
    )
}

/**
 * "Connecting…" and the like while not connected (`REQUIREMENTS.md` FR-L5): a pill in `surfaceContainerHighest` with
 * an expressive loading indicator. It grows in and out on the motion scheme's springs, keeping its last text while it
 * leaves, and screen readers announce each change.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ConnectionPill(connection: ConnectionStatus) {
    val motionScheme = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = connection.takeIf { it != ConnectionStatus.Ready },
        transitionSpec = {
            fadeIn(motionScheme.defaultEffectsSpec()) togetherWith fadeOut(motionScheme.fastEffectsSpec()) using
                SizeTransform(clip = false) { _, _ -> motionScheme.defaultSpatialSpec() }
        },
        label = "connectionPill",
    ) { status ->
        if (status != null) {
            Surface(
                modifier = Modifier.padding(top = MaterialTheme.spacing.extraSmall),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Row(
                    modifier =
                        Modifier.padding(
                            horizontal = MaterialTheme.spacing.medium,
                            vertical = MaterialTheme.spacing.extraSmall,
                        ),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LoadingIndicator(Modifier.size(MaterialTheme.sizes.inlineProgressIndicator))
                    Text(
                        text = stringResource(status.labelRes()),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        maxLines = 1,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

private fun ConnectionStatus.labelRes(): Int =
    when (this) {
        ConnectionStatus.WaitingForNetwork -> R.string.chat_list_waiting_for_network
        ConnectionStatus.ConnectingToProxy -> R.string.chat_list_connecting_to_proxy
        ConnectionStatus.Connecting -> R.string.chat_list_connecting
        ConnectionStatus.Updating, ConnectionStatus.Ready -> R.string.chat_list_updating
    }
