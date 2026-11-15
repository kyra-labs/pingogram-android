package dev.kyralabs.pingogram.feature.chat.ui.pinned

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.copiedNotice
import dev.kyralabs.pingogram.feature.chat.ui.copyPlainText
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The pinned messages page of the route's conversation (`docs/DESIGN_SYSTEM.md` §6 "Pinned messages bar"): hoists
 * [PinnedMessagesViewModel], asks before unpinning them all, and turns its effects into snackbars and copies; a jump
 * leaves through [onBack], back to the conversation, which shows the message.
 */
@Composable
internal fun PinnedMessagesRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PinnedMessagesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val currentOnBack by rememberUpdatedState(onBack)
    val context = LocalContext.current
    val resources = LocalResources.current
    val showDetails = isDebuggable()
    LaunchedEffect(viewModel) {
        var snackbar: Job? = null
        viewModel.effects.collect { effect ->
            val message =
                when (effect) {
                    PinnedPageEffect.Leave -> {
                        currentOnBack()
                        ""
                    }

                    is PinnedPageEffect.CopyText -> {
                        context.copyPlainText(effect.text)
                        copiedNotice(resources)
                    }

                    PinnedPageEffect.UnpinnedAll -> {
                        resources.getString(R.string.pinned_unpinned_all)
                    }

                    is PinnedPageEffect.ShowError -> {
                        errorMessage(resources, effect.error, showDetails)
                    }
                }
            if (message.isNotEmpty()) {
                snackbar?.cancel()
                snackbar = launch { snackbarHostState.showSnackbar(message) }
            }
        }
    }
    PinnedMessagesScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
    if (state.confirmUnpinAll) {
        UnpinAllAlert(
            onConfirm = { viewModel.onEvent(PinnedPageEvent.ConfirmUnpinAll) },
            onDismiss = { viewModel.onEvent(PinnedPageEvent.DismissUnpinAll) },
        )
    }
}

/** The destructive question before every message of the conversation is unpinned for everyone. */
@Composable
private fun UnpinAllAlert(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(UiR.drawable.ic_keep_off), contentDescription = null) },
        title = { Text(stringResource(R.string.pinned_unpin_all_title)) },
        text = { Text(stringResource(R.string.pinned_unpin_all_body)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.pinned_unpin_all_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.pinned_unpin_all_cancel))
            }
        },
    )
}
