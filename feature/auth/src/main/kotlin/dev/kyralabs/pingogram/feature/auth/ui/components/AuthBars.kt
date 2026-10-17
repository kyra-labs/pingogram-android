package dev.kyralabs.pingogram.feature.auth.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.domain.model.ConnectionStatus
import dev.kyralabs.pingogram.core.ui.text.durationText
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.auth.R

/**
 * Back navigation ([onBack], null when back leaves the flow) and, while not connected, the connection status as the
 * title, announced to screen readers (`docs/DESIGN_SYSTEM.md` §9).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AuthTopBar(
    connection: ConnectionStatus,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        modifier = modifier,
        title = {
            val status = connection.statusText()
            if (status != null) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = stringResource(R.string.auth_back),
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

@Composable
private fun ConnectionStatus.statusText(): String? =
    when (this) {
        ConnectionStatus.WaitingForNetwork -> stringResource(R.string.auth_connection_waiting_for_network)
        ConnectionStatus.ConnectingToProxy -> stringResource(R.string.auth_connection_connecting_to_proxy)
        ConnectionStatus.Connecting -> stringResource(R.string.auth_connection_connecting)
        ConnectionStatus.Updating -> stringResource(R.string.auth_connection_updating)
        ConnectionStatus.Ready -> null
    }

/** While offline: requests wait for the connection, so say so (`REQUIREMENTS.md` §6.5). */
@Composable
internal fun ConnectionBanner(
    isOffline: Boolean,
    modifier: Modifier = Modifier,
) {
    AuthBanner(visible = isOffline, modifier = modifier) {
        Text(
            text = stringResource(R.string.auth_offline_banner),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** While Telegram asks to wait (`FLOOD_WAIT`): the time left; the ViewModel counts it down. */
@Composable
internal fun FloodWaitBanner(
    seconds: Long?,
    modifier: Modifier = Modifier,
) {
    AuthBanner(visible = seconds != null, modifier = modifier, isError = true) {
        Text(
            text = stringResource(R.string.auth_flood_wait, durationText(seconds ?: 0)),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AuthBanner(
    visible: Boolean,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    content: @Composable () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
        exit = shrinkVertically(motion.defaultSpatialSpec()) + fadeOut(motion.defaultEffectsSpec()),
    ) {
        val colors = MaterialTheme.colorScheme
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = MaterialTheme.spacing.windowMarginCompact,
                        vertical = MaterialTheme.spacing.extraSmall,
                    ),
            shape = MaterialTheme.shapes.large,
            color = if (isError) colors.errorContainer else colors.surfaceContainerHighest,
            contentColor = if (isError) colors.onErrorContainer else colors.onSurfaceVariant,
        ) {
            Box(modifier = Modifier.padding(MaterialTheme.spacing.medium)) { content() }
        }
    }
}
