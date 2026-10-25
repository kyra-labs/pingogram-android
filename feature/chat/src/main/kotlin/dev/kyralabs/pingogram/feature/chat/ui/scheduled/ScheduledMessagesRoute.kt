package dev.kyralabs.pingogram.feature.chat.ui.scheduled

import android.content.Context
import android.content.res.Resources
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerFocusState
import dev.kyralabs.pingogram.feature.chat.ui.composer.rememberComposerFocusState
import dev.kyralabs.pingogram.feature.chat.ui.copiedNotice
import dev.kyralabs.pingogram.feature.chat.ui.copyPlainText
import dev.kyralabs.pingogram.feature.chat.ui.panel.EmojiPanelViewModel
import dev.kyralabs.pingogram.feature.chat.ui.scheduledTime
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * The scheduled messages of the route's chat (`REQUIREMENTS.md` FR-C5): hoists [ScheduledMessagesViewModel] and the
 * emoji panel's view model ([EmojiPanelViewModel], for the composer while it edits), shows the actions sheet
 * ([ScheduledActionsBottomSheet]), the delete confirmation ([ScheduledDeleteAlert]) and the date and time pickers
 * ([RescheduleDialogHost]) while the view model has them, and turns its [ScheduledEffect]s into snackbars, copies and
 * the composer's focus ([ScheduledEffects]). [onBack] leaves it.
 */
@Composable
internal fun ScheduledMessagesRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScheduledMessagesViewModel = hiltViewModel(),
    panelViewModel: EmojiPanelViewModel = hiltViewModel(key = "emoji-panel-scheduled"),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val composerFocus = rememberComposerFocusState()
    ScheduledEffects(viewModel.effects, snackbarHostState, composerFocus)
    ScheduledMessagesScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        composerText = viewModel.composerText,
        composerFocus = composerFocus,
        emojiPanel = panelViewModel.uiState.collectAsStateWithLifecycle().value,
        onEmojiPanelEvent = panelViewModel::onEvent,
    )
    state.actions?.let { ScheduledActionsBottomSheet(it, viewModel::onEvent) }
    state.deleteDialog?.let {
        ScheduledDeleteAlert(
            onConfirm = { viewModel.onEvent(ScheduledEvent.ConfirmDelete) },
            onDismiss = { viewModel.onEvent(ScheduledEvent.DismissDelete) },
        )
    }
    state.reschedule?.let { dialog ->
        RescheduleDialogHost(
            dialog = dialog,
            onReschedule = { messageId, sendDate -> viewModel.onEvent(ScheduledEvent.Reschedule(messageId, sendDate)) },
            onDismiss = { viewModel.onEvent(ScheduledEvent.DismissReschedule) },
        )
    }
}

/**
 * Handles [effects]: a snackbar for what was done (sent, deleted, rescheduled for its time in the device's format), for
 * a message gone meanwhile, and for errors (a flood wait's time, TDLib's own text in debug builds only), the newest
 * replacing the one shown; a copied text goes to the clipboard through [copyText], with a snackbar before Android 13;
 * an edit's start gives the composer the focus.
 */
@Composable
internal fun ScheduledEffects(
    effects: Flow<ScheduledEffect>,
    snackbarHostState: SnackbarHostState,
    composerFocus: ComposerFocusState,
    copyText: Context.(text: String) -> Unit = Context::copyPlainText,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val showDetails = isDebuggable()
    LaunchedEffect(effects) {
        var snackbar: Job? = null
        effects.collect { effect ->
            val message = context.noticeOf(effect, resources, showDetails, composerFocus, copyText)
            if (message.isNotEmpty()) {
                // Cancelling showSnackbar dismisses its snackbar.
                snackbar?.cancel()
                snackbar = launch { snackbarHostState.showSnackbar(message) }
            }
        }
    }
}

/** The snackbar of [effect]; empty where none shows, after doing what it asks. */
private fun Context.noticeOf(
    effect: ScheduledEffect,
    resources: Resources,
    showDetails: Boolean,
    composerFocus: ComposerFocusState,
    copyText: Context.(text: String) -> Unit,
): String =
    when (effect) {
        is ScheduledEffect.ShowError -> {
            errorMessage(resources, effect.error, showDetails)
        }

        is ScheduledEffect.CopyText -> {
            copyText(effect.text)
            copiedNotice(resources)
        }

        ScheduledEffect.Sent -> {
            resources.getString(R.string.scheduled_sent)
        }

        ScheduledEffect.Deleted -> {
            resources.getString(R.string.scheduled_deleted)
        }

        is ScheduledEffect.Rescheduled -> {
            resources.getString(R.string.scheduled_rescheduled, scheduledTime(effect.sendDate))
        }

        ScheduledEffect.MessageGone -> {
            resources.getString(R.string.scheduled_message_gone)
        }

        ScheduledEffect.FocusComposer -> {
            composerFocus.request()
            ""
        }
    }
