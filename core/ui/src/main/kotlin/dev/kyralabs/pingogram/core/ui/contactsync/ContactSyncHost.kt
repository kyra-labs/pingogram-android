package dev.kyralabs.pingogram.core.ui.contactsync

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri
import dev.kyralabs.pingogram.core.ui.R
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * The parts of contacts sync every screen that offers it shares (`docs/DESIGN_SYSTEM.md` §6, "Contacts sync"): the
 * dialog [controls] asks for ([ContactSyncRationaleSheet], [ContactSyncTurnOffDialog]) and the [effects] of its
 * [ContactSyncController]: Android's request for `READ_CONTACTS` (the Activity Result API), whose answer goes to
 * [onEvent] with whether Android would ask again; the app's page in Android's settings; and the snackbars on
 * [snackbarHostState].
 */
@Composable
fun ContactSyncHost(
    controls: ContactSyncControls,
    effects: Flow<ContactSyncEffect>,
    onEvent: (ContactSyncEvent) -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    val currentOnEvent by rememberUpdatedState(onEvent)
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val canAskAgain =
                activity != null &&
                    ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.READ_CONTACTS)
            currentOnEvent(ContactSyncEvent.PermissionResult(granted, canAskAgain))
        }
    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                ContactSyncEffect.RequestPermission -> {
                    permission.launch(Manifest.permission.READ_CONTACTS)
                }

                ContactSyncEffect.OpenAppSettings -> {
                    context.openAppSettings()
                }

                // Snackbars queue up; later effects must not wait for them.
                ContactSyncEffect.TurnedOff -> {
                    launch {
                        val result =
                            snackbarHostState.showSnackbar(
                                message = resources.getString(R.string.contact_sync_turned_off),
                                actionLabel = resources.getString(R.string.contact_sync_settings),
                                withDismissAction = true,
                            )
                        if (result == SnackbarResult.ActionPerformed) context.openAppSettings()
                    }
                }

                is ContactSyncEffect.ShowError -> {
                    launch { snackbarHostState.showSnackbar(errorMessage(resources, effect.error, showErrorDetails)) }
                }
            }
        }
    }
    when (controls.dialog) {
        ContactSyncDialog.Rationale -> {
            ContactSyncRationaleSheet(
                onContinue = { onEvent(ContactSyncEvent.Continue) },
                onDismiss = { onEvent(ContactSyncEvent.DismissDialog) },
            )
        }

        ContactSyncDialog.TurnOff -> {
            ContactSyncTurnOffDialog(
                onConfirm = { onEvent(ContactSyncEvent.ConfirmTurnOff) },
                onDismiss = { onEvent(ContactSyncEvent.DismissDialog) },
            )
        }

        null -> {
            // Nothing asks.
        }
    }
}

/**
 * What turning contacts sync on does, before Android asks for the permission, in a [ModalBottomSheet] as the location
 * sheet explains its own (`docs/DESIGN_SYSTEM.md` §6): a `Cookie9Sided` badge with the contacts icon, the title in
 * `titleLargeEmphasized`, then what is sent, when, and how to undo it in `bodyMedium`, honestly: the phone's names and
 * numbers go to Telegram, which finds who uses it (its own contact import); nothing is read in the background; turning
 * it off deletes them from Telegram. "Not now" ([onDismiss]) at the start, "Continue" ([onContinue]) at the end.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactSyncRationaleSheet(
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState =
            rememberBottomSheetState(
                SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
            ),
    ) {
        ContactSyncRationale(onContinue = onContinue, onDismiss = onDismiss)
    }
}

/** The rationale's content, also for previews and screenshots without the sheet's window. */
@Composable
fun ContactSyncRationale(
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.extraLarge)
                .padding(bottom = spacing.large)
                .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        ContactsBadge(Modifier.align(Alignment.CenterHorizontally))
        Text(
            text = stringResource(R.string.contact_sync_rationale_title),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLargeEmphasized,
        )
        listOf(
            R.string.contact_sync_rationale_what,
            R.string.contact_sync_rationale_when,
            R.string.contact_sync_rationale_off,
        ).forEach { text ->
            Text(
                text = stringResource(text),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = spacing.small),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.contact_sync_not_now))
            }
            Button(onClick = onContinue, shapes = ButtonDefaults.shapes()) {
                Text(
                    stringResource(R.string.contact_sync_continue),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }
        }
    }
}

/**
 * Whether to turn contacts sync off (`docs/DESIGN_SYSTEM.md` §6: an `AlertDialog` for destructive confirmations):
 * Telegram deletes the numbers it imported, contacts stay; "Turn off" in the error role next to "Cancel".
 */
@Composable
fun ContactSyncTurnOffDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.contact_sync_turn_off))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.contact_sync_cancel))
            }
        },
        icon = { Icon(painter = painterResource(R.drawable.ic_contacts), contentDescription = null) },
        title = { Text(stringResource(R.string.contact_sync_turn_off_title)) },
        text = { Text(stringResource(R.string.contact_sync_turn_off_text)) },
    )
}

/** The contacts icon on a `Cookie9Sided` badge of `primaryContainer`, decorative. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ContactsBadge(modifier: Modifier = Modifier) {
    val size = MaterialTheme.sizes.headlineBadge
    Box(
        modifier =
            modifier
                .size(size)
                .background(MaterialTheme.colorScheme.primaryContainer, MaterialShapes.Cookie9Sided.toShape()),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_contacts),
            contentDescription = null,
            modifier = Modifier.size(size / 2),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/** The app's page in Android's settings, where the user grants or takes back its permissions. */
fun Context.openAppSettings() {
    try {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()))
    } catch (_: ActivityNotFoundException) {
        // A device without the page: nothing to open.
    }
}

@PingogramPreviews
@Composable
private fun ContactSyncRationalePreview() {
    PingogramPreview { ContactSyncRationale(onContinue = {}, onDismiss = {}) }
}

@PingogramPreviews
@Composable
private fun ContactSyncTurnOffDialogPreview() {
    PingogramPreview { ContactSyncTurnOffDialog(onConfirm = {}, onDismiss = {}) }
}
