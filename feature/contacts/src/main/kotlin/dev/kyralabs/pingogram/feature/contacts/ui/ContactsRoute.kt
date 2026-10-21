package dev.kyralabs.pingogram.feature.contacts.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.feature.contacts.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The Contacts tab: hoists its [ContactsViewModel]. A contact calls [onOpenChat] with the private chat; "Add contact"
 * calls [onAddContact] and a contact's Edit [onEditContact]. "Invite friends" opens Android's share sheet with the
 * invitation ([shareInvite]), which needs no permission. Snackbars say what failed and what was deleted, the newest
 * replacing the one shown.
 */
@Composable
internal fun ContactsRoute(
    onOpenChat: (chatId: Long) -> Unit,
    onAddContact: () -> Unit,
    onEditContact: (userId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ContactsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    val currentOnOpenChat by rememberUpdatedState(onOpenChat)
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
        snackbarHostState = snackbarHostState,
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
