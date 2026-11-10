package dev.kyralabs.pingogram.feature.settings.ui.passkeys

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.passkey.PasskeyResult
import dev.kyralabs.pingogram.core.ui.passkey.rememberPasskeyCredentials
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.DestructiveDialog
import dev.kyralabs.pingogram.feature.settings.ui.DestructiveDialogTexts
import dev.kyralabs.pingogram.feature.settings.ui.PageItem
import dev.kyralabs.pingogram.feature.settings.ui.SettingsError
import dev.kyralabs.pingogram.feature.settings.ui.SettingsGroup
import dev.kyralabs.pingogram.feature.settings.ui.SettingsItemIcon
import dev.kyralabs.pingogram.feature.settings.ui.SettingsLoading
import dev.kyralabs.pingogram.feature.settings.ui.SettingsPageScaffold
import dev.kyralabs.pingogram.feature.settings.ui.SettingsRow
import dev.kyralabs.pingogram.feature.settings.ui.SettingsStatusCard
import dev.kyralabs.pingogram.feature.settings.ui.settingsRowColors
import kotlinx.coroutines.launch
import java.text.NumberFormat
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The Passkeys page: hoists [PasskeysViewModel], loads the passkeys again whenever the page shows, hands Telegram's
 * request to the password managers (Android 14 and later; earlier, "Add passkey" is left out) and shows the results in
 * snackbars.
 */
@Composable
internal fun PasskeysRoute(
    onOpen: (PasskeysLink) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PasskeysViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    val credentials = rememberPasskeyCredentials()
    val currentCredentials by rememberUpdatedState(credentials)
    LifecycleStartEffect(viewModel) {
        viewModel.onEvent(PasskeysEvent.Refresh)
        onStopOrDispose {}
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            val message =
                when (effect) {
                    is PasskeysEffect.CreatePasskey -> {
                        // Android's sheet shows meanwhile; the answer goes back to the view model as an event.
                        launch {
                            val result = currentCredentials?.create(effect.requestJson) ?: PasskeyResult.Unavailable
                            viewModel.onEvent(PasskeysEvent.Created(result))
                        }
                        null
                    }

                    is PasskeysEffect.Added -> {
                        resources.getString(R.string.settings_passkey_added, effect.name.bidiIsolated())
                    }

                    is PasskeysEffect.Removed -> {
                        resources.getString(R.string.settings_passkey_removed, effect.name.bidiIsolated())
                    }

                    PasskeysEffect.Unavailable -> {
                        resources.getString(R.string.settings_passkey_unavailable)
                    }

                    PasskeysEffect.Failed -> {
                        resources.getString(R.string.settings_passkey_failed)
                    }

                    is PasskeysEffect.ShowError -> {
                        errorMessage(resources, effect.error, showErrorDetails)
                    }
                }
            if (message != null) launch { snackbarHostState.showSnackbar(message) }
        }
    }
    PasskeysScreen(
        state = state,
        canAdd = credentials != null,
        onEvent = viewModel::onEvent,
        onOpen = onOpen,
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}

/**
 * Privacy and security → Passkeys (`docs/DESIGN_SYSTEM.md` §6, "Passkeys"; board 17a): a status card saying whether
 * passkeys sign in to the account; "Your passkeys", each with the password manager that keeps it, when it was added and
 * used, and "Remove" naming it (behind an `AlertDialog`), then "Add passkey" where the device offers passkeys
 * ([canAdd]); and "Also protecting this account": 2-step verification and the active sessions.
 */
@Composable
internal fun PasskeysScreen(
    state: PasskeysUiState,
    canAdd: Boolean,
    onEvent: (PasskeysEvent) -> Unit,
    onOpen: (PasskeysLink) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    SettingsPageScaffold(
        title = stringResource(R.string.settings_passkeys),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) {
        when (val passkeys = state.passkeys) {
            PasskeysState.Loading -> {
                SettingsLoading(stringResource(R.string.settings_passkeys_loading))
            }

            is PasskeysState.Failed -> {
                SettingsError(passkeys.error, onRetry = { onEvent(PasskeysEvent.Retry) })
            }

            is PasskeysState.Loaded -> {
                PasskeysCard(passkeys.passkeys.size)
                Passkeys(state, passkeys, canAdd, onEvent)
                Protection(state.protection, onOpen)
            }
        }
    }
    state.dialog?.let { dialog ->
        DestructiveDialog(
            icon = UiR.drawable.ic_key,
            texts =
                DestructiveDialogTexts(
                    title = stringResource(R.string.settings_passkey_dialog_title),
                    text =
                        stringResource(R.string.settings_passkey_dialog_text, dialog.name.bidiIsolated())
                            .bidiIsolated(),
                    confirm = stringResource(R.string.settings_passkey_remove_button),
                ),
            onConfirm = { onEvent(PasskeysEvent.Confirm) },
            onDismiss = { onEvent(PasskeysEvent.Dismiss) },
        )
    }
}

/** The board's status card: "On · 2 passkeys" or "Off", over what passkeys do. */
@Composable
private fun PasskeysCard(count: Int) {
    val numbers = NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0])
    SettingsStatusCard(
        kicker = stringResource(R.string.settings_passkeys),
        title = stringResource(R.string.settings_passkeys_card_title),
        summary = stringResource(R.string.settings_passkeys_card_summary),
        status =
            if (count > 0) {
                pluralStringResource(R.plurals.settings_passkeys_on, count, numbers.format(count))
            } else {
                stringResource(R.string.settings_passkeys_off)
            },
        icon = UiR.drawable.ic_key,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Passkeys(
    state: PasskeysUiState,
    passkeys: PasskeysState.Loaded,
    canAdd: Boolean,
    onEvent: (PasskeysEvent) -> Unit,
) {
    SettingsGroup(
        title = stringResource(R.string.settings_section_passkeys),
        footer =
            stringResource(
                if (canAdd) R.string.settings_passkeys_footer else R.string.settings_passkeys_footer_old_android,
            ),
    ) {
        if (passkeys.passkeys.isEmpty()) {
            item(key = NONE) { shapes -> NoPasskeys(shapes) }
        }
        passkeys.passkeys.forEach { passkey ->
            item(key = passkey.id) { shapes ->
                PasskeyRow(
                    passkey = passkey,
                    isRemoving = passkey.id in state.removingIds,
                    shapes = shapes,
                    onRemove = { onEvent(PasskeysEvent.Remove(passkey)) },
                )
            }
        }
        if (canAdd) {
            item(key = ADD) { shapes ->
                val adding = stringResource(R.string.settings_passkey_adding)
                SettingsRow(
                    title = stringResource(R.string.settings_passkey_add),
                    shapes = shapes,
                    icon = UiR.drawable.ic_add,
                    trailingContent =
                        if (state.isAdding) {
                            { LoadingIndicator(Modifier.semantics { contentDescription = adding }) }
                        } else {
                            null
                        },
                    onClick = { onEvent(PasskeysEvent.Add) },
                    enabled = !state.isAdding,
                )
            }
        }
    }
}

/**
 * A passkey, a row of its group ([shapes]): the key in the group's tonal circle, the password manager that keeps it,
 * when it was added and used, and "Remove" named with it, or progress while it is removed.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PasskeyRow(
    passkey: PasskeyItem,
    isRemoving: Boolean,
    shapes: ListItemShapes,
    onRemove: () -> Unit,
) {
    SegmentedListItem(
        shapes = shapes,
        leadingContent = { SettingsItemIcon(UiR.drawable.ic_key) },
        trailingContent = {
            if (isRemoving) {
                val removing = stringResource(R.string.settings_passkey_removing, passkey.name)
                LoadingIndicator(Modifier.semantics { contentDescription = removing })
            } else {
                val remove = stringResource(R.string.settings_passkey_remove, passkey.name)
                TextButton(
                    onClick = onRemove,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.semantics { contentDescription = remove },
                ) {
                    Text(stringResource(R.string.settings_passkey_remove_button))
                }
            }
        },
        supportingContent = { Text(text = passkey.details.resolve()) },
        colors = settingsRowColors(),
    ) {
        Text(text = passkey.name, style = LocalTextStyle.current.userContent)
    }
}

/** No passkey yet, the group's first segment. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NoPasskeys(shapes: ListItemShapes) {
    SegmentedListItem(shapes = shapes, colors = settingsRowColors()) {
        Text(
            text = stringResource(R.string.settings_passkeys_none),
            modifier = Modifier.fillMaxWidth().padding(vertical = MaterialTheme.spacing.extraSmall),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "Also protecting this account": 2-step verification and the active sessions, each opening its page. */
@Composable
private fun Protection(
    protection: AccountProtection,
    onOpen: (PasskeysLink) -> Unit,
) {
    val numbers = NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0])
    SettingsGroup(title = stringResource(R.string.settings_section_passkeys_also)) {
        item(key = TWO_STEP) { shapes ->
            SettingsRow(
                title = stringResource(R.string.settings_two_step_verification),
                shapes = shapes,
                icon = R.drawable.ic_password,
                supportingContent =
                    protection.twoStepVerification?.let { on ->
                        { Text(stringResource(twoStepVerificationText(on))) }
                    },
                onClick = { onOpen(PasskeysLink.TwoStepVerification) },
                opensPage = true,
            )
        }
        item(key = SESSIONS) { shapes ->
            val count = protection.sessionCount
            if (count != null) {
                PageItem(
                    title = stringResource(R.string.settings_active_sessions),
                    summary =
                        pluralStringResource(
                            R.plurals.settings_active_sessions_count,
                            count,
                            numbers.format(count),
                        ),
                    icon = R.drawable.ic_devices,
                    shapes = shapes,
                    onClick = { onOpen(PasskeysLink.ActiveSessions) },
                )
            } else {
                SettingsRow(
                    title = stringResource(R.string.settings_active_sessions),
                    shapes = shapes,
                    icon = R.drawable.ic_devices,
                    onClick = { onOpen(PasskeysLink.ActiveSessions) },
                    opensPage = true,
                )
            }
        }
    }
}

/** Whether a 2-step verification password is set, as the row's summary says it. */
@StringRes
private fun twoStepVerificationText(on: Boolean): Int =
    if (on) R.string.settings_passkeys_two_step_on else R.string.settings_passkeys_two_step_off

private const val NONE = "none"
private const val ADD = "add"
private const val TWO_STEP = "twoStep"
private const val SESSIONS = "sessions"
