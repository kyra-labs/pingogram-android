package dev.kyralabs.pingogram.feature.feedback

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

/**
 * The feedback page: hoists [FeedbackViewModel] and carries out what the user asked for, the share sheet, the browser
 * (the issue page, through the app's `LocalUriHandler`) or the clipboard, with a snackbar where Android shows no
 * confirmation of its own. [onBack] leaves the page.
 */
@Composable
internal fun FeedbackRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FeedbackViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val uriHandler = LocalUriHandler.current
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            val message =
                when (effect) {
                    is FeedbackEffect.Share -> {
                        context.shareText(effect.text)
                        null
                    }

                    is FeedbackEffect.OpenUrl -> {
                        try {
                            uriHandler.openUri(effect.url)
                            null
                        } catch (_: IllegalArgumentException) {
                            resources.getString(R.string.feedback_no_browser)
                        }
                    }

                    is FeedbackEffect.Copy -> {
                        context.copyText(effect.text)
                        // Android 13 and later confirm a copy themselves.
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            null
                        } else {
                            resources.getString(R.string.feedback_copied)
                        }
                    }

                    FeedbackEffect.CrashDeleted -> {
                        resources.getString(R.string.feedback_crash_deleted)
                    }
                }
            if (message != null) launch { snackbarHostState.showSnackbar(message) }
        }
    }
    FeedbackScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}

/** Shares [text] through the system's share sheet, to an app the user picks. */
private fun Context.shareText(text: String) {
    val send =
        Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.feedback_copy_label))
            .putExtra(Intent.EXTRA_TEXT, text)
    try {
        startActivity(Intent.createChooser(send, getString(R.string.feedback_share_title)))
    } catch (_: ActivityNotFoundException) {
        // No app takes text; nothing else to do.
    }
}

/** Puts [text] on the clipboard. */
private fun Context.copyText(text: String) {
    val clipboard = getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.feedback_copy_label), text))
}
