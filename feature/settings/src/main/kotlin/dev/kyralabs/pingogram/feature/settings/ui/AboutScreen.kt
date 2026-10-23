package dev.kyralabs.pingogram.feature.settings.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.component.LocalFeedbackLauncher
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The About page: hoists [AboutViewModel] and opens the links it asks for. [onOpenLicenses] opens the open-source
 * licenses; [onBack] leaves the page. On Android 12 and later, "Open Telegram links" opens Android's "Open by default"
 * settings of the app ([openLinkSettings]), where the user lets Pingogram open `t.me` links
 * (`docs/adr/0013-deep-links.md`); before 12, Android asks which app opens them anyway. "Send feedback" opens through
 * the app's [LocalFeedbackLauncher].
 */
@Composable
internal fun AboutRoute(
    onOpenLicenses: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AboutViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val onOpenLinkSettings =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            { context.openLinkSettings() }
        } else {
            null
        }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is AboutEffect.OpenUrl -> uriHandler.tryOpen(effect.url)
            }
        }
    }
    AboutScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onOpenLicenses = onOpenLicenses,
        onBack = onBack,
        modifier = modifier,
        onOpenLinkSettings = onOpenLinkSettings,
        onSendFeedback = LocalFeedbackLauncher.current,
    )
}

/**
 * Android's "Open by default" settings of the app, where "Open supported links" lets it open `t.me` links: they cannot
 * be verified App Links, which only Telegram could publish. The app's details if a system has no such page.
 */
@RequiresApi(Build.VERSION_CODES.S)
private fun Context.openLinkSettings() {
    val app = Uri.fromParts("package", packageName, null)
    try {
        startActivity(Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, app))
    } catch (_: ActivityNotFoundException) {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, app))
        } catch (_: ActivityNotFoundException) {
            // No settings app to open; nothing else to do.
        }
    }
}

/**
 * About Pingogram (`REQUIREMENTS.md` FR-S1): the logo in the theme's `primary`, the app and its version, its source
 * code on GitHub (opened in the browser), its license, the open-source licenses of what it is built from
 * ([onOpenLicenses]), "Open Telegram links" in Android's settings where they have it ([onOpenLinkSettings], null
 * where they do not), "Send feedback" ([onSendFeedback], where the app has it), and the statement that it is an
 * unofficial client, not affiliated with Telegram, as Telegram's
 * API terms require of third-party apps (`CLAUDE.md` rule 3).
 */
@Composable
internal fun AboutScreen(
    state: AboutUiState,
    onEvent: (AboutEvent) -> Unit,
    onOpenLicenses: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenLinkSettings: (() -> Unit)? = null,
    onSendFeedback: (() -> Unit)? = null,
) {
    SettingsPageScaffold(title = stringResource(R.string.settings_about), onBack = onBack, modifier = modifier) {
        AboutLogo()
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
            item { shapes ->
                SettingsRow(
                    title = stringResource(R.string.settings_about_licenses),
                    shapes = shapes,
                    icon = R.drawable.ic_library_books,
                    supportingContent = { Text(stringResource(R.string.settings_about_licenses_summary)) },
                    onClick = onOpenLicenses,
                )
            }
            if (onOpenLinkSettings != null) openTelegramLinksItem(onOpenLinkSettings)
            if (onSendFeedback != null) sendFeedbackItem(onSendFeedback)
        }
        Text(
            text = stringResource(R.string.settings_about_unofficial),
            modifier = Modifier.padding(horizontal = MaterialTheme.spacing.large),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** Pingogram's logo in the theme's `primary`, centred above the page's rows. */
@Composable
private fun AboutLogo() {
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
}

/** "Open Telegram links" (Android 12+), which opens Android's "Open by default" settings of the app ([onClick]). */
private fun SettingsGroupScope.openTelegramLinksItem(onClick: () -> Unit) =
    item { shapes ->
        SettingsRow(
            title = stringResource(R.string.settings_about_open_links),
            shapes = shapes,
            icon = UiR.drawable.ic_link,
            supportingContent = { Text(stringResource(R.string.settings_about_open_links_summary)) },
            trailingContent = {
                // Says that the row leaves the app; the row's text names it.
                Icon(painter = painterResource(R.drawable.ic_open_in_new), contentDescription = null)
            },
            onClick = onClick,
        )
    }

/** "Send feedback", which prepares a report of the build, the device and the last crash to share ([onClick]). */
private fun SettingsGroupScope.sendFeedbackItem(onClick: () -> Unit) =
    item { shapes ->
        SettingsRow(
            title = stringResource(R.string.settings_about_feedback),
            shapes = shapes,
            icon = UiR.drawable.ic_bug_report,
            supportingContent = { Text(stringResource(R.string.settings_about_feedback_summary)) },
            onClick = onClick,
        )
    }

/** Opens [uri] in the app that handles it, the browser for a web address, if there is one. */
internal fun UriHandler.tryOpen(uri: String) {
    try {
        openUri(uri)
    } catch (_: IllegalArgumentException) {
        // No app can open it (no browser); nothing else to do.
    }
}

@PingogramPreviews
@Composable
private fun AboutScreenPreview() {
    PingogramPreview {
        AboutScreen(
            state = AboutUiState(versionName = "0.3.0"),
            onEvent = {},
            onOpenLicenses = {},
            onBack = {},
            onOpenLinkSettings = {},
            onSendFeedback = {},
        )
    }
}
