package dev.kyralabs.pingogram.feature.settings.ui.oauth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import dev.kyralabs.pingogram.core.ui.component.CheckSwitch
import dev.kyralabs.pingogram.core.ui.component.performToggle
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.numericContent
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.account.StepProgress

// The parts of the "Log in with Telegram" sheet's question (OauthLoginSheet.kt).

/**
 * Who asks and what it gets: the website's or app's domain (left to right), the account's name, username and photo,
 * then where the request comes from: the browser and its system, and the place its address points to.
 */
@Composable
internal fun Details(
    state: OauthLoginUiState,
    name: String,
) {
    val request = state.request ?: return
    val colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    val browser = listOf(request.browser, request.platform).filter { it.isNotBlank() }.joinToString(" · ")
    Column(Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text(request.domain, style = MaterialTheme.typography.bodyLarge.numericContent) },
            supportingContent = {
                Text(
                    stringResource(if (request.isApp) R.string.settings_oauth_app else R.string.settings_oauth_website),
                )
            },
            colors = colors,
        )
        ListItem(
            overlineContent = {
                Text(
                    stringResource(R.string.settings_oauth_gets, name.bidiIsolated()).bidiIsolated(),
                )
            },
            headlineContent = { Text(stringResource(R.string.settings_oauth_gets_what)) },
            supportingContent =
                state.accountName?.let { account ->
                    { Text(stringResource(R.string.settings_oauth_as, account.bidiIsolated()).bidiIsolated()) }
                },
            colors = colors,
        )
        if (browser.isNotEmpty()) {
            ListItem(
                headlineContent = { Text(browser, style = MaterialTheme.typography.bodyLarge.userContent) },
                supportingContent = { Text(stringResource(R.string.settings_oauth_browser)) },
                colors = colors,
            )
        }
        if (request.location.isNotBlank()) {
            ListItem(
                headlineContent = { Text(request.location, style = MaterialTheme.typography.bodyLarge.userContent) },
                supportingContent = { Text(stringResource(R.string.settings_oauth_location)) },
                colors = colors,
            )
        }
    }
}

/** The switches for what the website or app asks for: letting its bot write, sharing the phone number; off at first. */
@Composable
internal fun Toggles(
    state: OauthLoginUiState,
    onEvent: (OauthLoginEvent) -> Unit,
) {
    val request = state.request ?: return
    Column(Modifier.fillMaxWidth()) {
        if (request.asksToMessage) {
            ToggleRow(
                title =
                    state.botName
                        ?.let { stringResource(R.string.settings_oauth_messages_bot, it.bidiIsolated()) }
                        ?: stringResource(R.string.settings_oauth_messages),
                checked = state.allowMessages,
                enabled = !state.isBusy,
                onChange = { onEvent(OauthLoginEvent.SetAllowMessages(it)) },
            )
        }
        if (request.asksForPhoneNumber) {
            ToggleRow(
                title = stringResource(R.string.settings_oauth_phone),
                checked = state.sharePhoneNumber,
                enabled = !state.isBusy,
                onChange = { onEvent(OauthLoginEvent.SetSharePhoneNumber(it)) },
            )
        }
    }
}

/** A switch row: the row toggles it, and screen readers hear the row as the switch. */
@Composable
private fun ToggleRow(
    title: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    ListItem(
        headlineContent = { Text(title) },
        modifier =
            Modifier
                .fillMaxWidth()
                .toggleable(value = checked, enabled = enabled, role = Role.Switch) {
                    haptics.performToggle(it)
                    onChange(it)
                },
        trailingContent = { CheckSwitch(checked = checked, enabled = enabled) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

/** A wavy line while Telegram answers, which screen readers hear, in a place kept above the buttons. */
@Composable
internal fun Progress(visible: Boolean) {
    StepProgress(visible, stringResource(R.string.settings_oauth_working))
}
