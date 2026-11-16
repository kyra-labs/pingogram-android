package dev.kyralabs.pingogram.feature.auth.ui.components

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.domain.model.ConnectionStatus
import dev.kyralabs.pingogram.core.ui.component.CountdownIndicator
import dev.kyralabs.pingogram.core.ui.component.StateCrossfade
import dev.kyralabs.pingogram.core.ui.text.clockText
import dev.kyralabs.pingogram.core.ui.text.durationText
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.core.ui.time.announcedEveryMinute
import dev.kyralabs.pingogram.core.ui.time.secondsLeft
import dev.kyralabs.pingogram.feature.auth.R
import dev.kyralabs.pingogram.feature.auth.ui.withTabularClock
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * Back navigation ([onBack], null when back leaves the flow) and, while not connected, the connection status as the
 * title, announced to screen readers (`docs/DESIGN_SYSTEM.md` §9); [actions] at the end, e.g. Welcome's language
 * button.
 */
@Composable
internal fun AuthTopBar(
    connection: ConnectionStatus,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        modifier = modifier,
        actions = actions,
        // The status and the back arrow cross-fade as they come and go (docs/DESIGN_SYSTEM.md §5).
        title = {
            StateCrossfade(connection.statusText(), label = "AuthConnectionStatus") { status ->
                if (status != null) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        // Offline, the ConnectionBanner announces it instead.
                        modifier =
                            Modifier.semantics {
                                if (connection != ConnectionStatus.WaitingForNetwork) {
                                    liveRegion = LiveRegionMode.Polite
                                }
                            },
                    )
                }
            }
        },
        navigationIcon = {
            StateCrossfade(onBack, contentKey = { it != null }, label = "AuthBack") { back ->
                if (back != null) {
                    IconButton(onClick = back, shapes = IconButtonDefaults.shapes()) {
                        Icon(
                            painter = painterResource(UiR.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.auth_back),
                        )
                    }
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

/**
 * Welcome's language button at the top bar's end (owner decision 2026-11-15, board 23): a default tonal button with
 * the language symbol and the language the app shows, named "App language: English" for screen readers. It opens
 * Android's list of the app's languages ([onClick]), which exists from Android 13; earlier versions leave it out
 * ([isAppLanguageListAvailable]).
 */
@Composable
internal fun AppLanguageButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val language = locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
    val description = stringResource(R.string.auth_app_language, language)
    FilledTonalButton(
        onClick = onClick,
        shapes = ButtonDefaults.shapes(),
        modifier = modifier.padding(end = MaterialTheme.spacing.small).semantics { contentDescription = description },
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_language),
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.iconSizeFor(ButtonDefaults.MinHeight)),
        )
        Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(ButtonDefaults.MinHeight)))
        Text(text = language, style = MaterialTheme.typography.labelLarge, modifier = Modifier.clearAndSetSemantics {})
    }
}

/** Whether Android lists the app's languages for it to open (`Settings.ACTION_APP_LOCALE_SETTINGS`, Android 13+). */
internal val isAppLanguageListAvailable: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/**
 * While offline: requests wait for the connection, so say so (`REQUIREMENTS.md` §6.5), with the "wifi off" symbol;
 * announced when it appears.
 */
@Composable
internal fun ConnectionBanner(
    isOffline: Boolean,
    modifier: Modifier = Modifier,
) {
    AuthBanner(
        visible = isOffline,
        modifier = modifier,
        icon = { Icon(painterResource(R.drawable.ic_wifi_off), contentDescription = null) },
    ) {
        Text(
            text = stringResource(R.string.auth_offline_banner),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/**
 * While Telegram asks to wait (`FLOOD_WAIT`): a warning on `tertiaryContainer` (owner decision 2026-11-15, board 23),
 * the time left as a clock ("4:59", [clockText], tabular figures), which the ViewModel counts down, and beside it a
 * clock in a [CountdownIndicator] that runs down from [totalSeconds]. Screen readers hear the time in words as a live
 * region, once a minute ([announcedEveryMinute], board 23e) rather than every second.
 */
@Composable
internal fun FloodWaitBanner(
    seconds: Long?,
    totalSeconds: Long,
    modifier: Modifier = Modifier,
) {
    val left = seconds ?: 0
    AuthBanner(
        visible = seconds != null,
        modifier = modifier,
        isWarning = true,
        icon = {
            CountdownIndicator(
                secondsLeft = left,
                totalSeconds = totalSeconds,
                color = LocalContentColor.current,
                trackColor = MaterialTheme.colorScheme.outlineVariant,
            ) {
                Icon(painterResource(UiR.drawable.ic_schedule), contentDescription = null)
            }
        },
    ) {
        val announcement = stringResource(R.string.auth_flood_wait, durationText(announcedEveryMinute(left)))
        val clock = clockText(left)
        Text(
            text = withTabularClock(stringResource(R.string.auth_flood_wait, clock), clock),
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
 * A banner below the top bar, as wide as the step's content on wide windows: a leading [icon], then [content]; on
 * `surfaceContainerHighest`, or `tertiaryContainer` for a warning ([isWarning]).
 */
@Composable
private fun AuthBanner(
    visible: Boolean,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    isWarning: Boolean = false,
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
                color = if (isWarning) colors.tertiaryContainer else colors.surfaceContainerHighest,
                contentColor = if (isWarning) colors.onTertiaryContainer else colors.onSurfaceVariant,
            ) {
                Row(
                    modifier = Modifier.padding(MaterialTheme.spacing.medium),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    icon()
                    Box(modifier = Modifier.weight(1f)) { content() }
                }
            }
        }
    }
}
