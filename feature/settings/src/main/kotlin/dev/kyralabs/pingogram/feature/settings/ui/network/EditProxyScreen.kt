package dev.kyralabs.pingogram.feature.settings.ui.network

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SecureTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.domain.model.ProxyKind
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.text.numericContent
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.ChoiceRow
import dev.kyralabs.pingogram.feature.settings.ui.SettingsError
import dev.kyralabs.pingogram.feature.settings.ui.SettingsGroup
import dev.kyralabs.pingogram.feature.settings.ui.SettingsLoading
import dev.kyralabs.pingogram.feature.settings.ui.SettingsPageScaffold
import dev.kyralabs.pingogram.feature.settings.ui.SettingsSegment
import dev.kyralabs.pingogram.feature.settings.ui.SwitchItem
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

/**
 * Adding or editing a proxy: hoists [EditProxyViewModel], closes the page once it saved ([onBack], with a `Confirm`
 * haptic), and shows its errors in snackbars with a `Reject` haptic.
 */
@Composable
internal fun EditProxyRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditProxyViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val haptics = LocalHapticFeedback.current
    val showErrorDetails = isDebuggable()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                EditProxyEffect.Saved -> {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onBack()
                }

                is EditProxyEffect.ShowError -> {
                    haptics.performHapticFeedback(HapticFeedbackType.Reject)
                    launch { snackbarHostState.showSnackbar(errorMessage(resources, effect.error, showErrorDetails)) }
                }
            }
        }
    }
    EditProxyScreen(state, viewModel::onEvent, onBack, modifier, snackbarHostState)
}

/**
 * The form of a proxy (`docs/DESIGN_SYSTEM.md` §6, "Proxies"): the protocol as connected toggle buttons, then the
 * server and port (left to right), what the protocol needs (username and password, "HTTP requests only", or the
 * secret), and a name, in rounded tonal fields, each wrong one with its error below it; then how "Check" went, the
 * progress while saving, "Check" at the start and "Save and connect" / "Save" at the end.
 */
@Composable
internal fun EditProxyScreen(
    state: EditProxyUiState,
    onEvent: (EditProxyEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val isNew = (state as? EditProxyUiState.Form)?.isNew ?: false
    SettingsPageScaffold(
        title = stringResource(if (isNew) R.string.settings_proxy_new else R.string.settings_proxy_edit_title),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.union(WindowInsets.ime),
    ) {
        when (state) {
            EditProxyUiState.Loading -> SettingsLoading(stringResource(R.string.settings_proxy_edit_loading))
            is EditProxyUiState.Error -> SettingsError(state.error, onRetry = { onEvent(EditProxyEvent.Retry) })
            is EditProxyUiState.Form -> ProxyFormContent(state, onEvent)
        }
    }
}

@Composable
private fun ColumnScope.ProxyFormContent(
    state: EditProxyUiState.Form,
    onEvent: (EditProxyEvent) -> Unit,
) {
    val form = state.form
    val change = { changed: ProxyForm -> onEvent(EditProxyEvent.Change(changed)) }
    SettingsGroup(title = stringResource(R.string.settings_proxy_protocol)) {
        item { shapes ->
            SettingsSegment(shapes) {
                ChoiceRow(
                    options = ProxyKind.entries.toImmutableList(),
                    selected = form.kind,
                    label = { proxyKindName(it) },
                    onSelect = { change(form.copy(kind = it)) },
                    contentPadding = PaddingValues(MaterialTheme.spacing.small),
                    enabled = !state.saving,
                )
            }
        }
    }
    ProxyFields(state, change)
    if (form.kind == ProxyKind.Http) {
        SettingsGroup {
            item { shapes ->
                SwitchItem(
                    title = stringResource(R.string.settings_proxy_http_only),
                    summary = stringResource(R.string.settings_proxy_http_only_summary),
                    checked = form.httpOnly,
                    shapes = shapes,
                    onCheckedChange = { change(form.copy(httpOnly = it)) },
                )
            }
        }
    }
    FormEnd(state, onEvent)
}

/** The server and port, what the protocol needs to log in, and the name, each wrong one with its error. */
@Composable
private fun ProxyFields(
    state: EditProxyUiState.Form,
    change: (ProxyForm) -> Unit,
) {
    val form = state.form
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
        ProxyField(
            value = form.server,
            onValueChange = { change(form.copy(server = it)) },
            label = stringResource(R.string.settings_proxy_server),
            error = stringResource(R.string.settings_proxy_server_error).takeIf { ProxyField.Server in state.errors },
            enabled = !state.saving,
            keyboardType = KeyboardType.Uri,
            numeric = true,
        )
        ProxyField(
            value = form.port,
            onValueChange = { text -> change(form.copy(port = text.filter { it.isDigit() })) },
            label = stringResource(R.string.settings_proxy_port),
            error = stringResource(R.string.settings_proxy_port_error).takeIf { ProxyField.Port in state.errors },
            enabled = !state.saving,
            keyboardType = KeyboardType.Number,
            numeric = true,
        )
        CredentialFields(state, change)
        ProxyField(
            value = form.name,
            onValueChange = { change(form.copy(name = it)) },
            label = stringResource(R.string.settings_proxy_name),
            enabled = !state.saving,
            keyboardType = KeyboardType.Text,
            numeric = false,
            imeAction = ImeAction.Done,
        )
    }
}

/** What the protocol needs to log in: a username and password, or the secret. */
@Composable
private fun CredentialFields(
    state: EditProxyUiState.Form,
    change: (ProxyForm) -> Unit,
) {
    val form = state.form
    when (form.kind) {
        ProxyKind.Socks5, ProxyKind.Http -> {
            ProxyField(
                value = form.username,
                onValueChange = { change(form.copy(username = it)) },
                label = stringResource(R.string.settings_proxy_username),
                enabled = !state.saving,
                keyboardType = KeyboardType.Ascii,
                numeric = true,
            )
            SecretField(
                value = form.password,
                onValueChange = { change(form.copy(password = it)) },
                label = stringResource(R.string.settings_proxy_password),
                enabled = !state.saving,
            )
        }

        ProxyKind.Mtproto -> {
            SecretField(
                value = form.secret,
                onValueChange = { change(form.copy(secret = it)) },
                label = stringResource(R.string.settings_proxy_secret),
                enabled = !state.saving,
                error =
                    stringResource(R.string.settings_proxy_secret_error).takeIf {
                        ProxyField.Secret in
                            state.errors
                    },
            )
        }
    }
}

/** How "Check" went (a polite live region), the progress while saving, then "Check" and "Save". */
@Composable
private fun FormEnd(
    state: EditProxyUiState.Form,
    onEvent: (EditProxyEvent) -> Unit,
) {
    state.check?.let { check ->
        Text(
            text = proxyCheckText(check),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Polite },
            color =
                if (check == ProxyCheck.Unavailable) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    if (state.saving) {
        val saving = stringResource(R.string.settings_proxy_saving)
        LinearWavyProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = saving })
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = { onEvent(EditProxyEvent.Check) },
            enabled = !state.saving && state.check != ProxyCheck.Checking,
            shapes = ButtonDefaults.shapes(),
        ) {
            Text(stringResource(R.string.settings_proxy_check), style = MaterialTheme.typography.labelLarge)
        }
        Box {
            Button(
                onClick = { onEvent(EditProxyEvent.Save) },
                enabled = !state.saving,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(
                    text =
                        stringResource(
                            if (state.isNew) R.string.settings_proxy_save_connect else R.string.settings_proxy_save,
                        ),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }
        }
    }
}

/** A rounded tonal field of the form; [numeric] for what reads left to right (addresses, ports, usernames). */
@Suppress("LongParameterList") // A field's value, label, error and keyboard.
@Composable
private fun ProxyField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    keyboardType: KeyboardType,
    numeric: Boolean,
    error: String? = null,
    imeAction: ImeAction = ImeAction.Next,
) {
    val style = MaterialTheme.typography.bodyLarge
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().semantics { if (error != null) error(error) },
        enabled = enabled,
        textStyle = if (numeric) style.numericContent else style.userContent,
        label = { Text(label) },
        isError = error != null,
        supportingText = error?.let { { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }) } },
        singleLine = true,
        keyboardOptions =
            KeyboardOptions(
                keyboardType = keyboardType,
                autoCorrectEnabled = false,
                imeAction = imeAction,
            ),
        shape = TextFieldDefaults.roundedShape,
        colors = TextFieldDefaults.tonalColors(),
    )
}

/**
 * A password or secret in a [SecureTextField], which keeps its own text: what is typed goes up through
 * [onValueChange], and a [value] the page sets (an edited proxy's, once it is read) comes down.
 */
@Composable
private fun SecretField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    error: String? = null,
) {
    val state: TextFieldState = rememberTextFieldState(value)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    LaunchedEffect(value) {
        if (state.text.toString() != value) state.edit { replace(0, length, value) }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }.collect { currentOnValueChange(it) }
    }
    SecureTextField(
        state = state,
        modifier = Modifier.fillMaxWidth().semantics { if (error != null) error(error) },
        enabled = enabled,
        label = { Text(label) },
        supportingText = error?.let { { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }) } },
        isError = error != null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
        shape = TextFieldDefaults.roundedShape,
        colors = TextFieldDefaults.tonalColors(),
    )
}
