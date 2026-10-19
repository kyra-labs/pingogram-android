package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
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
import dev.kyralabs.pingogram.core.ui.R as UiR

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
 * About Pingogram (`REQUIREMENTS.md` FR-S1): the logo in the theme's `primary`, the app and its version, its source code on GitHub (opened in the
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
        Image(
            painter = painterResource(UiR.drawable.logo_pingogram),
            // The app's name, which the logo spells; the row below names the app again with its version.
            contentDescription = stringResource(R.string.settings_about_app_name),
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = MaterialTheme.spacing.extraLarge)
                    .wrapContentWidth(),
        )
        SettingsGroup(title = stringResource(R.string.settings_section_app)) {
            item { shapes ->
                SettingsRow(
                    title = stringResource(R.string.settings_about_app_name),
                    shapes = shapes,
                    icon = R.drawable.ic_info,
                    supportingContent = { Text(stringResource(R.string.settings_about_version, state.versionName)) },
                )
            }
            item { shapes ->
                SettingsRow(
                    title = stringResource(R.string.settings_about_source_code),
                    shapes = shapes,
                    icon = R.drawable.ic_code,
                    supportingContent = { Text(stringResource(R.string.settings_about_source_code_summary)) },
                    trailingContent = {
                        // Says that the row leaves the app; the row's text names it.
                        Icon(painter = painterResource(R.drawable.ic_open_in_new), contentDescription = null)
                    },
                    onClick = { onEvent(AboutEvent.OpenSourceCode) },
                )
            }
            item { shapes ->
                SettingsRow(
                    title = stringResource(R.string.settings_about_license),
                    shapes = shapes,
                    icon = R.drawable.ic_license,
                    supportingContent = { Text(stringResource(R.string.settings_about_license_summary)) },
                )
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
