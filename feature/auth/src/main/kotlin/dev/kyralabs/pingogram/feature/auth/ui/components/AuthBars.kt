package dev.kyralabs.pingogram.feature.auth.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.domain.model.ConnectionStatus
import dev.kyralabs.pingogram.core.ui.text.durationText
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.auth.R
import dev.kyralabs.pingogram.core.ui.R as UiR

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
                    // Offline, the ConnectionBanner announces it instead.
                    modifier =
                        Modifier.semantics {
                            if (connection != ConnectionStatus.WaitingForNetwork) liveRegion = LiveRegionMode.Polite
                        },
                )
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(UiR.drawable.ic_arrow_back),
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

/** While offline: requests wait for the connection, so say so (`REQUIREMENTS.md` §6.5); announced when it appears. */
@Composable
internal fun ConnectionBanner(
    isOffline: Boolean,
    modifier: Modifier = Modifier,
) {
    AuthBanner(visible = isOffline, modifier = modifier) {
        Text(
            text = stringResource(R.string.auth_offline_banner),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/**
 * While Telegram asks to wait (`FLOOD_WAIT`): the time left, which the ViewModel counts down. Screen readers hear it
 * as a live region, at most every 15 seconds ([announcedCountdownSeconds]) rather than every second.
 */
@Composable
internal fun FloodWaitBanner(
    seconds: Long?,
    modifier: Modifier = Modifier,
) {
    AuthBanner(visible = seconds != null, modifier = modifier, isError = true) {
        val left = seconds ?: 0
        val announcement = stringResource(R.string.auth_flood_wait, durationText(announcedCountdownSeconds(left)))
        Text(
            text = stringResource(R.string.auth_flood_wait, durationText(left)),
            style = MaterialTheme.typography.bodyMedium,
            modifier =
                Modifier.clearAndSetSemantics {
                    contentDescription = announcement
                    liveRegion = LiveRegionMode.Polite
                },
        )
    }
}

/**
 * Whole seconds of a countdown as screen readers hear them: under a minute rounded up to 15-second steps, so a live
 * region speaks at most every 15 seconds; `durationText` already rounds longer times up to minutes and hours. Rounding
 * up never claims less time than is left.
 */
internal fun announcedCountdownSeconds(seconds: Long): Long =
    if (seconds in 1 until SECONDS_PER_MINUTE) {
        (seconds + ANNOUNCEMENT_STEP_SECONDS - 1) / ANNOUNCEMENT_STEP_SECONDS * ANNOUNCEMENT_STEP_SECONDS
    } else {
        seconds
    }

private const val SECONDS_PER_MINUTE = 60L
private const val ANNOUNCEMENT_STEP_SECONDS = 15L

/** A banner below the top bar, as wide as the step's content on wide windows. */
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
        modifier = modifier.fillMaxWidth(),
        enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
        exit = shrinkVertically(motion.defaultSpatialSpec()) + fadeOut(motion.defaultEffectsSpec()),
    ) {
        val colors = MaterialTheme.colorScheme
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = authHorizontalMargin(), vertical = MaterialTheme.spacing.extraSmall),
            contentAlignment = Alignment.TopCenter,
        ) {
            Surface(
                modifier = Modifier.widthIn(max = MaterialTheme.sizes.contentMaxWidth).fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = if (isError) colors.errorContainer else colors.surfaceContainerHighest,
                contentColor = if (isError) colors.onErrorContainer else colors.onSurfaceVariant,
            ) {
                Box(modifier = Modifier.padding(MaterialTheme.spacing.medium)) { content() }
            }
        }
    }
}
