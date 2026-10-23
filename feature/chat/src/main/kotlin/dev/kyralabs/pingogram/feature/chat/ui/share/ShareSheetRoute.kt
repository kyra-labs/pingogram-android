package dev.kyralabs.pingogram.feature.chat.ui.share

import android.content.res.Resources
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalResources
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.domain.model.ShareResult
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.feature.chat.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The share destination (`ShareRoute` of `:core:navigation`): the share sheet ([ShareBottomSheet]) for its
 * [ShareViewModel], over the screen it was opened from. Once the sheet closed, [onClose] leaves the destination, or
 * [onOpenChat] opens the one chat picked, where the share waits in the composer.
 */
@Composable
internal fun ShareSheetRoute(
    onClose: () -> Unit,
    onOpenChat: (chatId: Long) -> Unit,
    viewModel: ShareViewModel = hiltViewModel(),
) {
    val sheet by viewModel.sheet.collectAsStateWithLifecycle()
    val closed by viewModel.closed.collectAsStateWithLifecycle()
    sheet?.takeIf { closed == null }?.let { ShareBottomSheet(it, viewModel.comment, viewModel::onEvent) }
    val currentOnClose by rememberUpdatedState(onClose)
    val currentOnOpenChat by rememberUpdatedState(onOpenChat)
    LaunchedEffect(closed) {
        when (val close = closed) {
            null -> Unit
            ShareClose.Done -> currentOnClose()
            is ShareClose.OpenChat -> currentOnOpenChat(close.chatId)
        }
    }
}

/**
 * The snackbars of shares sent to several chats ([ShareResults]), in [snackbarHostState], the app shell's: "Sent to
 * 3 chats", or for how many it failed and why. The newest replaces the one shown.
 */
@Composable
internal fun ShareResultNotices(
    snackbarHostState: SnackbarHostState,
    viewModel: ShareResultsViewModel = hiltViewModel(),
) {
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    LaunchedEffect(viewModel, snackbarHostState) {
        var shown: Job? = null
        viewModel.results.collect { result ->
            val message = shareNotice(resources, result, showErrorDetails)
            if (message.isNotEmpty()) {
                // Cancelling showSnackbar dismisses its snackbar.
                shown?.cancel()
                shown = launch { snackbarHostState.showSnackbar(message) }
            }
        }
    }
}

/** The snackbar's text for [result]; empty for an error that shows nothing (`DomainError.Silent`). */
internal fun shareNotice(
    resources: Resources,
    result: ShareResult,
    showErrorDetails: Boolean,
): String {
    val error = result.error
    val total = result.sent + result.failed
    return when {
        error == null -> {
            resources.getQuantityString(R.plurals.share_done, result.sent, result.sent)
        }

        result.sent == 0 -> {
            errorMessage(resources, error, showErrorDetails)
        }

        else -> {
            val reason = errorMessage(resources, error, showErrorDetails)
            resources.getQuantityString(R.plurals.share_failed_some, result.failed, result.failed, total, reason)
        }
    }
}

/** [ShareResults] for [ShareResultNotices], kept by the shell that shows them. */
@HiltViewModel
class ShareResultsViewModel
    @Inject
    constructor(
        results: ShareResults,
    ) : ViewModel() {
        /** What came of sharing, as it comes. */
        internal val results: Flow<ShareResult> = results.results
    }
