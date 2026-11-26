package dev.kyralabs.pingogram.feature.settings.ui.cleanup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.component.AvatarShape
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.SettingsFooter
import dev.kyralabs.pingogram.feature.settings.ui.SettingsGroup
import dev.kyralabs.pingogram.feature.settings.ui.SettingsListItem
import dev.kyralabs.pingogram.feature.settings.ui.focus.appLocale
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The chat Cleanup suggestions offer now (`REQUIREMENTS.md` FR-X24): its avatar and name over when it was last opened,
 * then its choices, Mute, Archive, Leave where offered and Not now, disabled while one is on its way; how many follow
 * it under the group. While the main list loads, a loading indicator; with no chat, a line saying so.
 */
@Composable
internal fun CleanupSuggestionGroup(
    state: CleanupUiState,
    onEvent: (CleanupEvent) -> Unit,
) {
    val settings = state.settings ?: return
    if (!settings.enabled) return
    val suggestion = state.suggestion
    when {
        suggestion != null -> {
            SettingsGroup(title = stringResource(R.string.settings_cleanup_suggestion)) {
                item { shapes ->
                    SettingsListItem(
                        shapes = shapes,
                        leadingContent = { SuggestionAvatar(suggestion) },
                        supportingContent = { Text(openedText(suggestion)) },
                    ) {
                        Text(suggestion.title)
                    }
                }
                item { shapes ->
                    SettingsListItem(shapes = shapes) { SuggestionChoices(suggestion, state.isActing, onEvent) }
                }
            }
            if (state.remaining > 0) {
                val remaining = state.remaining
                SettingsFooter(pluralStringResource(R.plurals.settings_cleanup_remaining, remaining, remaining))
            }
        }

        state.loading -> {
            Box(Modifier.fillMaxWidth().padding(MaterialTheme.spacing.large), contentAlignment = Alignment.Center) {
                DelayedLoadingIndicator()
            }
        }

        else -> {
            SettingsGroup {
                item { shapes ->
                    SettingsListItem(
                        shapes = shapes,
                        supportingContent = { Text(stringResource(R.string.settings_cleanup_none_body)) },
                    ) {
                        Text(stringResource(R.string.settings_cleanup_none))
                    }
                }
            }
        }
    }
    if (state.isConfirmingLeave && suggestion != null) LeaveDialog(suggestion, onEvent)
}

/** The chat's photo, or its initials, in its kind's shape. */
@Composable
private fun SuggestionAvatar(suggestion: CleanupSuggestion) {
    val type = suggestion.type
    Avatar(
        photo = suggestion.photo,
        name = suggestion.title,
        id = suggestion.chatId,
        size = MaterialTheme.sizes.leadingIconContainer,
        shape =
            AvatarShape.ofChat(
                isForum = false,
                isGroup = type is ChatType.BasicGroup || (type as? ChatType.Supergroup)?.isChannel == false,
                isChannel = (type as? ChatType.Supergroup)?.isChannel == true,
            ),
    )
}

/** The choices, each a button that wraps to a new line where the row is narrow. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SuggestionChoices(
    suggestion: CleanupSuggestion,
    isActing: Boolean,
    onEvent: (CleanupEvent) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        val enabled = !isActing
        FilledTonalButton(
            onClick = { onEvent(CleanupEvent.Mute) },
            enabled = enabled,
            shapes = ButtonDefaults.shapes(),
        ) {
            Text(stringResource(R.string.settings_cleanup_mute))
        }
        FilledTonalButton(
            onClick = { onEvent(CleanupEvent.Archive) },
            enabled = enabled,
            shapes = ButtonDefaults.shapes(),
        ) {
            Text(stringResource(R.string.settings_cleanup_archive))
        }
        if (suggestion.canLeave) {
            OutlinedButton(
                onClick = { onEvent(CleanupEvent.Leave) },
                enabled = enabled,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.settings_cleanup_leave))
            }
        }
        TextButton(onClick = { onEvent(CleanupEvent.NotNow) }, enabled = enabled, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.settings_cleanup_not_now))
        }
    }
}

/** Leave's confirmation: "Leave Book club?", what it does, "Leave" and "Cancel". */
@Composable
private fun LeaveDialog(
    suggestion: CleanupSuggestion,
    onEvent: (CleanupEvent) -> Unit,
) {
    val isChannel = (suggestion.type as? ChatType.Supergroup)?.isChannel == true
    AlertDialog(
        onDismissRequest = { onEvent(CleanupEvent.DismissLeave) },
        title = { Text(stringResource(R.string.settings_cleanup_leave_title, suggestion.title.bidiIsolated())) },
        text = {
            Text(
                stringResource(
                    if (isChannel) R.string.settings_cleanup_leave_channel else R.string.settings_cleanup_leave_group,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = { onEvent(CleanupEvent.ConfirmLeave) }, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.settings_cleanup_leave))
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(CleanupEvent.DismissLeave) }, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.settings_dialog_dismiss))
            }
        },
    )
}

/** "Last opened 3 Mar 2026", or "Not opened since 1 Jan 2026" for a chat not opened since the phone kept it. */
@Composable
private fun openedText(suggestion: CleanupSuggestion): String {
    val date = mediumDate(suggestion.lastOpened)
    return stringResource(
        if (suggestion.neverOpened) {
            R.string.settings_cleanup_not_opened_since
        } else {
            R.string.settings_cleanup_last_opened
        },
        date,
    )
}

@Composable
private fun mediumDate(at: Instant): String =
    at
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(appLocale()))
