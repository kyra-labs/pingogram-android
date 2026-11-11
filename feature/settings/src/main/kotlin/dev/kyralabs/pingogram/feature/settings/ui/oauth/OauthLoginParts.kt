package dev.kyralabs.pingogram.feature.settings.ui.oauth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.LinearWavyProgressIndicator
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.ui.component.CheckSwitch
import dev.kyralabs.pingogram.core.ui.component.performToggle
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.feature.settings.R

// The parts of the "Log in with Telegram" sheet's question (OauthLoginSheet.kt).

/** Where the request comes from: the browser and its system, and the place its address points to. */
@Composable
internal fun Details(state: OauthLoginUiState) {
    val request = state.request ?: return
    val colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    val browser = listOf(request.browser, request.platform).filter { it.isNotBlank() }.joinToString(" · ")
    Column(Modifier.fillMaxWidth()) {
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

/** A wavy line while Telegram answers, which screen readers hear. */
@Composable
internal fun Progress(visible: Boolean) {
    if (!visible) return
    val description = stringResource(R.string.settings_oauth_working)
    LinearWavyProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = description })
}
