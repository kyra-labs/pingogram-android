package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.contactsync.ContactSyncHost

/**
 * Reads the page again ([viewModel]) and contacts sync ([contactSyncViewModel]) whenever the page shows again, and
 * hosts contacts sync's dialogs, permission request and snackbars ([ContactSyncHost]) in [snackbarHostState].
 */
@Composable
internal fun PrivacySecurityRefreshAndSync(
    viewModel: PrivacySecurityViewModel,
    contactSyncViewModel: ContactSyncSettingViewModel,
    snackbarHostState: SnackbarHostState,
) {
    val controls by contactSyncViewModel.contactSync.controls.collectAsStateWithLifecycle()
    LifecycleStartEffect(viewModel, contactSyncViewModel) {
        viewModel.onEvent(PrivacySecurityEvent.Refresh)
        contactSyncViewModel.onStarted()
        onStopOrDispose {}
    }
    ContactSyncHost(
        controls = controls,
        effects = contactSyncViewModel.contactSync.effects,
        onEvent = contactSyncViewModel.contactSync::onEvent,
        snackbarHostState = snackbarHostState,
    )
}
