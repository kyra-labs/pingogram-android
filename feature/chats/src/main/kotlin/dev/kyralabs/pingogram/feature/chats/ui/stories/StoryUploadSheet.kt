package dev.kyralabs.pingogram.feature.chats.ui.stories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chats.R

/**
 * What the user's stories being posted are doing (board 10c, `docs/DESIGN_SYSTEM.md` §6 "Story rings"), from a tap on
 * their own ring: a [ModalBottomSheet] saying how many are posting and how much is sent, or why TDLib gave up, then
 * "View your story" where they have stories already, and "Stop posting" (or "Remove" once all failed) in `error`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StoryUploadSheet(
    upload: StoryRingUpload,
    cancel: UploadCancel,
    canView: Boolean,
    onEvent: (StoryUploadSheetEvent) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = { onEvent(StoryUploadSheetEvent.Dismiss) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        StoryUploadContent(upload, cancel, canView, onEvent)
    }
}

/** What the sheet's user does. */
internal enum class StoryUploadSheetEvent { Stop, View, Dismiss }

/** The sheet's content, also drawn alone by goldens and previews. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun StoryUploadContent(
    upload: StoryRingUpload,
    cancel: UploadCancel,
    canView: Boolean,
    onEvent: (StoryUploadSheetEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    val allFailed = upload.failed == upload.count
    Column(
        modifier.fillMaxWidth().padding(horizontal = spacing.large).padding(bottom = spacing.large),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        Text(
            text =
                if (allFailed) {
                    pluralStringResource(R.plurals.stories_upload_failed_title, upload.count, upload.count)
                } else {
                    pluralStringResource(R.plurals.stories_upload_title, upload.count, upload.count)
                },
            style = MaterialTheme.typography.titleLargeEmphasized,
            modifier = Modifier.semantics { heading() },
        )
        if (!allFailed) UploadProgress(upload.progress)
        UploadStatus(upload, cancel)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
            TextButton(
                onClick = { onEvent(StoryUploadSheetEvent.Stop) },
                enabled = !cancel.isCancelling,
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(if (allFailed) R.string.stories_upload_remove else R.string.stories_upload_stop))
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                if (canView) {
                    FilledTonalButton(
                        onClick = { onEvent(StoryUploadSheetEvent.View) },
                        shapes = ButtonDefaults.shapes(),
                    ) { Text(stringResource(R.string.stories_upload_view)) }
                }
            }
        }
    }
}

/** How much is sent, as a determinate wavy bar (indeterminate while TDLib does not know the sizes). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UploadProgress(progress: Float?) {
    if (progress != null) {
        LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
    } else {
        LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}

/**
 * "62% sent" or "Waiting to send", what stopping does, then why TDLib gave up on some or why stopping failed, in
 * `error`, a polite live region.
 */
@Composable
private fun UploadStatus(
    upload: StoryRingUpload,
    cancel: UploadCancel,
) {
    val progress = upload.progress
    val status =
        when {
            upload.failed == upload.count -> null
            progress == null -> stringResource(R.string.stories_upload_waiting)
            else -> stringResource(R.string.stories_upload_sent, (progress * PERCENT).toInt())
        }
    val error = cancel.error ?: upload.error
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall)) {
        status?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(R.string.stories_upload_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (error != null) {
            Text(
                text = errorText(error),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/** A share of 1 as a percentage. */
private const val PERCENT = 100
