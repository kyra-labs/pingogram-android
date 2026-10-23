package dev.kyralabs.pingogram.feature.contacts.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.contactsync.ContactSyncHost
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.feature.contacts.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The Contacts tab: hoists its [ContactsViewModel]. A contact calls [onOpenChat] with the private chat; "Add contact"
 * calls [onAddContact] and a contact's Edit [onEditContact]. "Invite friends" opens Android's share sheet with the
 * invitation ([shareInvite]), and a phone's contact not on Telegram the messaging app ([inviteByMessage]); neither
 * needs a permission. Snackbars say what failed and what was deleted, the newest replacing the one shown. Contacts
 * sync's dialogs, permission request and snackbars come from [ContactSyncHost]; each time the tab starts (back from
 * Android's settings too) it tells the view model ([ContactsEvent.Started]). The top bar opens the user's own QR code
 * ([onOpenMyQrCode]) and the QR code scanner ([onScanQrCode]).
 */
@Composable
internal fun ContactsRoute(
    onOpenChat: (chatId: Long) -> Unit,
    onAddContact: () -> Unit,
    onEditContact: (userId: Long) -> Unit,
    modifier: Modifier = Modifier,
    onOpenMyQrCode: () -> Unit = {},
    onScanQrCode: () -> Unit = {},
    viewModel: ContactsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    val currentOnOpenChat by rememberUpdatedState(onOpenChat)
    val syncControls by viewModel.contactSync.controls.collectAsStateWithLifecycle()
    LifecycleStartEffect(viewModel) {
        viewModel.onEvent(ContactsEvent.Started)
        onStopOrDispose {}
    }
    ContactSyncHost(syncControls, viewModel.contactSync.effects, viewModel.contactSync::onEvent, snackbarHostState)
    LaunchedEffect(viewModel) {
        var snackbar: Job? = null
        val show = { message: String ->
            // Cancelling showSnackbar dismisses its snackbar.
            snackbar?.cancel()
            snackbar = launch { snackbarHostState.showSnackbar(message) }
        }
        viewModel.effects.collect { effect ->
            when (effect) {
                is ContactsEffect.OpenChat -> {
                    currentOnOpenChat(effect.chatId)
                }

                is ContactsEffect.ShareInvite -> {
                    shareInvite(context, resources.getString(R.string.contacts_invite_text, effect.link))
                }

                is ContactsEffect.InviteByMessage -> {
                    inviteByMessage(
                        context,
                        effect.phoneNumber,
                        resources.getString(R.string.contacts_invite_text, effect.link),
                    )
                }

                is ContactsEffect.Deleted -> {
                    show(resources.getString(R.string.contacts_deleted, effect.name.bidiIsolated()).bidiIsolated())
                }

                is ContactsEffect.ShowError -> {
                    errorMessage(resources, effect.error, showErrorDetails).takeIf { it.isNotEmpty() }?.let(show)
                }
            }
        }
    }
    ContactsScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onAddContact = onAddContact,
        onEditContact = onEditContact,
        modifier = modifier,
        syncControls = syncControls,
        onSyncEvent = viewModel.contactSync::onEvent,
        snackbarHostState = snackbarHostState,
        onOpenMyQrCode = onOpenMyQrCode,
        onScanQrCode = onScanQrCode,
    )
}

/**
 * Opens Android's share sheet with [text], an invitation to Telegram, for the user to send it with any app. Nothing
 * is sent by Pingogram itself (Telegram's API terms: no automated invitations).
 */
internal fun shareInvite(
    context: Context,
    text: String,
) {
    val send =
        Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, text)
    try {
        context.startActivity(Intent.createChooser(send, context.getString(R.string.contacts_invite_title)))
    } catch (_: ActivityNotFoundException) {
        // No app can share text (a stripped-down device): nothing to open.
    }
}

/**
 * Opens the messaging app with [text], an invitation to Telegram, addressed to [phoneNumber] (`smsto:`), for the user
 * to send: Pingogram sends nothing itself and needs no SMS permission. Without a messaging app, the share sheet.
 */
internal fun inviteByMessage(
    context: Context,
    phoneNumber: String,
    text: String,
) {
    val message = Intent(Intent.ACTION_SENDTO, "smsto:${Uri.encode(phoneNumber)}".toUri()).putExtra("sms_body", text)
    try {
        context.startActivity(message)
    } catch (_: ActivityNotFoundException) {
        shareInvite(context, text)
    }
}
