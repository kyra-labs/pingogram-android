package dev.kyralabs.pingogram.feature.chats.ui.archive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.domain.model.ArchiveRowStyle
import dev.kyralabs.pingogram.core.domain.model.ArchiveSettings
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.preview.PingogramVariantPreviews
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chats.R
import dev.kyralabs.pingogram.feature.chats.ui.preview.ArchiveSettingsPreviewStates

/**
 * The archive's settings (`REQUIREMENTS.md` FR-L9), pushed above the archive from its top bar: hoists its
 * [ArchiveSettingsViewModel], shows its errors as snackbars, and [onBack] leaves it.
 */
@Composable
internal fun ArchiveSettingsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ArchiveSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ArchiveSettingsEffect.ShowError -> {
                    snackbarHostState.showSnackbar(errorMessage(resources, effect.error, showErrorDetails))
                }
            }
        }
    }
    ArchiveSettingsScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}

/**
 * The archive's settings page (`docs/DESIGN_SYSTEM.md` §6 "Archive settings"), a settings-style page: the medium
 * flexible top bar, then segmented groups on the page's color.
 *
 * - **New chats**: "Archive and mute new chats" from non-contacts, disabled with the reason as its footer where
 *   Telegram does not offer it ([ArchiveSettingsContent.Content.canChangeNewChats]).
 * - **When a new message arrives**: keeping unmuted chats archived, and keeping those of folders, which the first
 *   implies (disabled while it is on, saying so).
 * - **In the chat list**: the archive row full, compact or hidden, as radio rows; the device's own setting, shown in
 *   every state of Telegram's.
 *
 * Telegram's groups show a loading indicator (after 200 ms) or the error with "Try again" in a segment in their place.
 */
@Composable
internal fun ArchiveSettingsScreen(
    state: ArchiveSettingsUiState,
    onEvent: (ArchiveSettingsEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    ArchivePageScaffold(
        title = stringResource(R.string.archive_settings_title),
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    ) {
        when (val settings = state.settings) {
            ArchiveSettingsContent.Loading -> LoadingSegment()
            is ArchiveSettingsContent.Error -> ErrorSegment(settings, onRetry = { onEvent(ArchiveSettingsEvent.Retry) })
            is ArchiveSettingsContent.Content -> TelegramGroups(settings, onEvent)
        }
        RowStyleGroup(state.rowStyle, onSelect = { onEvent(ArchiveSettingsEvent.SetRowStyle(it)) })
    }
}

/** The groups of Telegram's settings: new chats, and what a new message does. */
@Composable
private fun TelegramGroups(
    content: ArchiveSettingsContent.Content,
    onEvent: (ArchiveSettingsEvent) -> Unit,
) {
    val settings = content.settings
    val change = { changed: ArchiveSettings -> onEvent(ArchiveSettingsEvent.Change(changed)) }
    ArchivePageGroup(
        title = stringResource(R.string.archive_settings_new_chats_header),
        count = 1,
        footer =
            stringResource(
                if (content.canChangeNewChats) {
                    R.string.archive_settings_new_chats_footer
                } else {
                    R.string.archive_settings_new_chats_unavailable
                },
            ),
    ) { _, shapes ->
        ArchiveSwitchItem(
            title = stringResource(R.string.archive_settings_new_chats),
            summary = stringResource(R.string.archive_settings_new_chats_summary),
            checked = settings.archiveAndMuteNewChatsFromNonContacts,
            shapes = shapes,
            onCheckedChange = { change(settings.copy(archiveAndMuteNewChatsFromNonContacts = it)) },
            enabled = content.canChangeNewChats,
        )
    }
    ArchivePageGroup(
        title = stringResource(R.string.archive_settings_kept_header),
        count = 2,
        footer = stringResource(R.string.archive_settings_kept_footer),
    ) { index, shapes ->
        if (index == 0) {
            ArchiveSwitchItem(
                title = stringResource(R.string.archive_settings_keep_unmuted),
                summary = stringResource(R.string.archive_settings_keep_unmuted_summary),
                checked = settings.keepUnmutedChatsArchived,
                shapes = shapes,
                onCheckedChange = { change(settings.copy(keepUnmutedChatsArchived = it)) },
            )
        } else {
            // Telegram ignores it while every unmuted chat is kept.
            val implied = settings.keepUnmutedChatsArchived
            ArchiveSwitchItem(
                title = stringResource(R.string.archive_settings_keep_folders),
                summary =
                    stringResource(
                        if (implied) {
                            R.string.archive_settings_keep_folders_implied
                        } else {
                            R.string.archive_settings_keep_folders_summary
                        },
                    ),
                checked = implied || settings.keepChatsFromFoldersArchived,
                shapes = shapes,
                onCheckedChange = { change(settings.copy(keepChatsFromFoldersArchived = it)) },
                enabled = !implied,
            )
        }
    }
}

/** The archive row's style, as radio rows in one `selectableGroup`. */
@Composable
private fun RowStyleGroup(
    style: ArchiveRowStyle,
    onSelect: (ArchiveRowStyle) -> Unit,
) {
    val styles = ArchiveRowStyle.entries
    ArchivePageGroup(
        title = stringResource(R.string.archive_settings_row_header),
        count = styles.size,
        footer = stringResource(R.string.archive_settings_row_footer),
        rowsModifier = Modifier.selectableGroup(),
    ) { index, shapes ->
        val option = styles[index]
        ArchiveRadioItem(
            label = stringResource(option.labelRes()),
            summary = stringResource(option.summaryRes()),
            selected = option == style,
            shapes = shapes,
            onClick = { onSelect(option) },
        )
    }
}

/** Telegram's settings on their way: a loading indicator in a segment as tall as a row, after 200 ms. */
@Composable
private fun LoadingSegment() {
    val loading = stringResource(R.string.archive_settings_loading)
    ArchivePageSegment(shapes = ListItemDefaults.segmentedShapes(0, 1)) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(MaterialTheme.spacing.large),
            contentAlignment = Alignment.Center,
        ) { DelayedLoadingIndicator(Modifier.semantics { contentDescription = loading }) }
    }
}

/** Reading Telegram's settings failed: why, and "Try again" ([onRetry]) at the end. */
@Composable
private fun ErrorSegment(
    error: ArchiveSettingsContent.Error,
    onRetry: () -> Unit,
) {
    ArchivePageSegment(shapes = ListItemDefaults.segmentedShapes(0, 1)) {
        Text(
            text = stringResource(R.string.archive_settings_error),
            modifier =
                Modifier.padding(
                    start = MaterialTheme.spacing.large,
                    end = MaterialTheme.spacing.large,
                    top = MaterialTheme.spacing.large,
                ),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = errorText(error.error),
            modifier = Modifier.padding(horizontal = MaterialTheme.spacing.large),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(MaterialTheme.spacing.small),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onRetry, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.chat_list_retry))
            }
        }
    }
}

private fun ArchiveRowStyle.labelRes(): Int =
    when (this) {
        ArchiveRowStyle.Full -> R.string.archive_settings_row_full
        ArchiveRowStyle.Compact -> R.string.archive_settings_row_compact
        ArchiveRowStyle.Hidden -> R.string.archive_settings_row_hidden
    }

private fun ArchiveRowStyle.summaryRes(): Int =
    when (this) {
        ArchiveRowStyle.Full -> R.string.archive_settings_row_full_summary
        ArchiveRowStyle.Compact -> R.string.archive_settings_row_compact_summary
        ArchiveRowStyle.Hidden -> R.string.archive_settings_row_hidden_summary
    }

@PingogramPreviews
@PingogramVariantPreviews
@Composable
private fun ArchiveSettingsScreenPreview(
    @PreviewParameter(ArchiveSettingsPreviewStates::class) state: ArchiveSettingsUiState,
) {
    PingogramPreview { ArchiveSettingsScreen(state = state, onEvent = {}, onBack = {}) }
}
