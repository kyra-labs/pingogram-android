package dev.kyralabs.pingogram.feature.chat.ui.export

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.domain.model.ChatExportError
import dev.kyralabs.pingogram.core.domain.model.ChatExportResult
import dev.kyralabs.pingogram.core.ui.component.EmptyState
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import java.text.NumberFormat
import dev.kyralabs.pingogram.core.ui.R as UiR

// The export page while an export runs, once it ended, and when the chat cannot be exported or read.

/**
 * The export running: its chat, the progress (determinate where the export knows how far it is), how many messages
 * so far or Telegram's flood wait (a polite live region), a note that it runs on in the background, and "Cancel
 * export".
 */
@Composable
internal fun RunningState(
    content: ChatExportUiState.Content,
    run: ExportRun.Running,
    onEvent: (ChatExportEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = run.progress
    val waiting = progress.floodWaitSeconds
    val count = NumberFormat.getIntegerInstance().format(progress.messages)
    ExportColumn(modifier.verticalScroll(rememberScrollState())) {
        ExportGroup(
            rows =
                listOf(
                    ExportRow("running") { shapes ->
                        ExportSegment(shapes) {
                            Column(
                                Modifier.padding(MaterialTheme.spacing.large),
                                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
                            ) {
                                Text(
                                    text =
                                        stringResource(
                                            R.string.chat_export_running,
                                            content.chatTitle.resolve().bidiIsolated(),
                                        ),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                val fraction = progress.fraction
                                if (fraction != null) {
                                    LinearWavyProgressIndicator(
                                        progress = { fraction },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                } else {
                                    LinearWavyProgressIndicator(Modifier.fillMaxWidth())
                                }
                                Text(
                                    text =
                                        if (waiting != null) {
                                            stringResource(R.string.chat_export_flood_wait, waiting)
                                        } else {
                                            pluralStringResource(
                                                R.plurals.chat_export_messages_read,
                                                progress.messages,
                                                count,
                                            )
                                        },
                                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    },
                ),
            footer = stringResource(R.string.chat_export_running_note),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { onEvent(ChatExportEvent.Cancel) }, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.chat_export_cancel))
            }
        }
    }
}

/** What was exported, with "Export again" at the start and "Open folder" at the end. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DoneState(
    result: ChatExportResult,
    onEvent: (ChatExportEvent) -> Unit,
    onOpenFolder: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    val numbers = NumberFormat.getIntegerInstance()
    val body =
        buildList {
            add(
                resources.getQuantityString(
                    R.plurals.chat_export_done_messages,
                    result.messageCount,
                    numbers.format(result.messageCount),
                ),
            )
            if (result.mediaCount > 0) {
                add(
                    resources.getQuantityString(
                        R.plurals.chat_export_done_media,
                        result.mediaCount,
                        numbers.format(result.mediaCount),
                    ),
                )
            }
            if (result.skippedMediaCount > 0) {
                add(
                    resources.getQuantityString(
                        R.plurals.chat_export_done_skipped,
                        result.skippedMediaCount,
                        numbers.format(result.skippedMediaCount),
                    ),
                )
            }
        }.joinToString("\n")
    EmptyState(
        illustration = MaterialShapes.Cookie9Sided,
        icon = UiR.drawable.ic_check,
        title = stringResource(R.string.chat_export_done_title),
        body = body,
        modifier = modifier,
        action = {
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                TextButton(onClick = { onEvent(ChatExportEvent.Dismiss) }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.chat_export_again))
                }
                Button(onClick = { onOpenFolder(result.folderUri) }, shapes = ButtonDefaults.shapes()) {
                    Text(
                        stringResource(R.string.chat_export_open_folder),
                        style = MaterialTheme.typography.labelLargeEmphasized,
                    )
                }
            }
        },
    )
}

/** Why the export failed, with "Try again", which returns to the form. */
@Composable
internal fun FailedState(
    error: ChatExportError,
    onEvent: (ChatExportEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    ErrorState(
        title = stringResource(R.string.chat_export_failed_title),
        body =
            when (error) {
                ChatExportError.FolderUnavailable -> stringResource(R.string.chat_export_folder_unavailable)
                ChatExportError.StorageFull -> stringResource(R.string.chat_export_storage_full)
                ChatExportError.NotAllowed -> stringResource(R.string.chat_export_protected)
                is ChatExportError.Telegram -> errorText(error.error)
            },
        action = stringResource(R.string.chat_export_try_again),
        onAction = { onEvent(ChatExportEvent.Dismiss) },
        modifier = modifier,
    )
}

/** An error: an [EmptyState] on `Cookie4Sided` in `errorContainer`, with [action]. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ErrorState(
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EmptyState(
        illustration = MaterialShapes.Cookie4Sided,
        icon = UiR.drawable.ic_error,
        title = title,
        body = body,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.errorContainer,
        iconColor = MaterialTheme.colorScheme.onErrorContainer,
        action = {
            Button(onClick = onAction, shapes = ButtonDefaults.shapes()) {
                Text(action, style = MaterialTheme.typography.labelLargeEmphasized)
            }
        },
    )
}

/** Why the chat cannot be exported: a secret chat, or saving its content is off. No action. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun NotAllowedState(
    isSecretChat: Boolean,
    modifier: Modifier = Modifier,
) {
    EmptyState(
        illustration = MaterialShapes.Cookie9Sided,
        icon = UiR.drawable.ic_lock,
        title = stringResource(R.string.chat_export_not_allowed_title),
        body = stringResource(if (isSecretChat) R.string.chat_export_secret_chat else R.string.chat_export_protected),
        modifier = modifier,
    )
}
