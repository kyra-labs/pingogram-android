package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R

/** The About page: hoists [AboutViewModel] and opens the links it asks for. [onBack] leaves the page. */
@Composable
internal fun AboutRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AboutViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is AboutEffect.OpenUrl -> uriHandler.tryOpen(effect.url)
            }
        }
    }
    AboutScreen(state = state, onEvent = viewModel::onEvent, onBack = onBack, modifier = modifier)
}

/**
 * About Pingogram (`REQUIREMENTS.md` FR-S1): the app and its version, its source code on GitHub (opened in the
 * browser), its license, and the statement that it is an unofficial client, not affiliated with Telegram, as
 * Telegram's API terms require of third-party apps (`CLAUDE.md` rule 3).
 */
@Composable
internal fun AboutScreen(
    state: AboutUiState,
    onEvent: (AboutEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsPageScaffold(title = stringResource(R.string.settings_about), onBack = onBack, modifier = modifier) {
        SettingsSection(stringResource(R.string.settings_section_app)) {
            ListItem(
                leadingContent = { SettingsItemIcon(R.drawable.ic_info) },
                supportingContent = { Text(stringResource(R.string.settings_about_version, state.versionName)) },
                colors = settingsItemColors(),
            ) {
                Text(stringResource(R.string.settings_about_app_name))
            }
            ListItem(
                onClick = { onEvent(AboutEvent.OpenSourceCode) },
                leadingContent = { SettingsItemIcon(R.drawable.ic_code) },
                trailingContent = {
                    // Says that the row leaves the app; the row's text names it.
                    Icon(painter = painterResource(R.drawable.ic_open_in_new), contentDescription = null)
                },
                supportingContent = { Text(stringResource(R.string.settings_about_source_code_summary)) },
                colors = settingsItemColors(),
            ) {
                Text(stringResource(R.string.settings_about_source_code))
            }
            ListItem(
                leadingContent = { SettingsItemIcon(R.drawable.ic_license) },
                supportingContent = { Text(stringResource(R.string.settings_about_license_summary)) },
                colors = settingsItemColors(),
            ) {
                Text(stringResource(R.string.settings_about_license))
            }
        }
        Text(
            text = stringResource(R.string.settings_about_unofficial),
            modifier = Modifier.padding(horizontal = MaterialTheme.spacing.large),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun UriHandler.tryOpen(uri: String) {
    try {
        openUri(uri)
    } catch (_: IllegalArgumentException) {
        // No app can open it (no browser); nothing else to do.
    }
}

@PingogramPreviews
@Composable
private fun AboutScreenPreview() {
    PingogramPreview { AboutScreen(state = AboutUiState(versionName = "0.3.0"), onEvent = {}, onBack = {}) }
}
