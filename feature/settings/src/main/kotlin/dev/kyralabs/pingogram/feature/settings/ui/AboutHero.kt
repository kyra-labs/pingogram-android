package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.ui.text.numericContent
import dev.kyralabs.pingogram.core.ui.theme.experimentalTypography
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * About's header (`docs/DESIGN_SYSTEM.md` §13.10): Pingogram's mark on a `Sunny` badge in
 * `primary`, the name in `displaySmallWide` `primary` over the version on a tonal pill (one heading for screen
 * readers), then "What's new" and "Check for updates" ([onEvent]), which open GitHub's release pages in the
 * browser, the latter only where [showsCheckForUpdates] (not when an app store installed Pingogram); on the rows'
 * container with `extraLarge` corners.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AboutHero(
    versionName: String,
    onEvent: (AboutEvent) -> Unit,
    showsCheckForUpdates: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val spacing = MaterialTheme.spacing
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = settingsRowContainerColor(),
    ) {
        Column(
            modifier = Modifier.padding(spacing.extraLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            AboutBadge()
            Column(
                modifier = Modifier.semantics(mergeDescendants = true) { heading() },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing.small),
            ) {
                Text(
                    stringResource(R.string.settings_about_app_name),
                    color = colors.primary,
                    style = MaterialTheme.experimentalTypography.displaySmallWide,
                )
                Text(
                    text = stringResource(R.string.settings_about_version, versionName),
                    modifier =
                        Modifier
                            .background(colors.surfaceContainerHighest, CircleShape)
                            .padding(horizontal = spacing.medium, vertical = spacing.extraSmall),
                    style = MaterialTheme.typography.labelMediumEmphasized.numericContent,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                Button(onClick = { onEvent(AboutEvent.OpenReleaseNotes) }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.settings_about_whats_new))
                }
                if (showsCheckForUpdates) {
                    FilledTonalButton(
                        onClick = { onEvent(AboutEvent.OpenLatestRelease) },
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(stringResource(R.string.settings_about_check_updates))
                    }
                }
            }
        }
    }
}

/** Pingogram's mark in `onPrimary` on a `Sunny` badge in `primary`, decorative (the name below says it). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AboutBadge() {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier =
            Modifier
                .size(MaterialTheme.sizes.avatarLarge)
                .background(colors.primary, MaterialShapes.Sunny.toShape()),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(UiR.drawable.ic_pingogram),
            contentDescription = null,
            modifier = Modifier.size(MaterialTheme.sizes.playBadge),
            tint = colors.onPrimary,
        )
    }
}
