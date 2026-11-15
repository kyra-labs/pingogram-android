package dev.kyralabs.pingogram.feature.chat.ui.reminders

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ReminderEvent
import kotlinx.coroutines.launch

/**
 * The reminder sheet of [viewModel] while it shows, and what it says in the conversation's snackbars: "Reminder set for
 * Sun, 9:00 AM" with "Undo" (long enough for screen readers, `SnackbarDuration.Long`), "Reminder deleted", or why it
 * failed.
 */
@Composable
internal fun ReminderSheetHost(
    viewModel: ReminderSheetViewModel,
    snackbarHostState: SnackbarHostState,
) {
    val sheet by viewModel.sheet.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val details = isDebuggable()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            // Snackbars queue up; later effects must not wait for them.
            launch {
                when (effect) {
                    is ReminderEffect.Set -> {
                        val result =
                            snackbarHostState.showSnackbar(
                                message =
                                    resources.getString(
                                        R.string.reminder_set_for,
                                        effect.time.resolve(resources),
                                    ),
                                actionLabel = resources.getString(R.string.reminder_undo),
                                duration = SnackbarDuration.Long,
                            )
                        if (result == SnackbarResult.ActionPerformed) viewModel.onEvent(ReminderEvent.Undo)
                    }

                    ReminderEffect.Deleted -> {
                        snackbarHostState.showSnackbar(resources.getString(R.string.reminder_deleted))
                    }

                    is ReminderEffect.ShowError -> {
                        snackbarHostState.showSnackbar(errorMessage(resources, effect.error, details))
                    }
                }
            }
        }
    }
    sheet?.let { ReminderSheet(it, viewModel::onEvent) }
}
