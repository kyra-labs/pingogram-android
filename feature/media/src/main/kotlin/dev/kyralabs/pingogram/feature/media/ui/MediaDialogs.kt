package dev.kyralabs.pingogram.feature.media.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.media.R
import dev.kyralabs.pingogram.feature.media.ui.preview.MediaPreviewData
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * Why saving needs the storage permission on Android 8–9, shown when the system says to explain it before asking
 * again (Android's permission guidance): "Not now" leaves it ([onAnswer] false), "Continue" asks ([onAnswer] true). A
 * Material [AlertDialog]: the answer decides whether the system's own dialog follows.
 */
@Composable
internal fun StorageRationaleDialog(onAnswer: (proceed: Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAnswer(false) },
        confirmButton = {
            TextButton(onClick = { onAnswer(true) }, shapes = ButtonDefaults.shapes()) {
                Text(
                    text = stringResource(R.string.media_storage_rationale_continue),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { onAnswer(false) }, shapes = ButtonDefaults.shapes()) {
                Text(
                    text = stringResource(R.string.media_storage_rationale_not_now),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }
        },
        icon = { Icon(painter = painterResource(UiR.drawable.ic_download), contentDescription = null) },
        title = { Text(stringResource(R.string.media_storage_rationale_title)) },
        text = { Text(stringResource(R.string.media_storage_rationale_body)) },
    )
}

/**
 * The confirmation before the current item is deleted (`REQUIREMENTS.md` FR-C6; `docs/DESIGN_SYSTEM.md` §6: an
 * [AlertDialog], for destructive confirmations only): "Delete photo?" (or video, or GIF, by [media]), for whom it
 * deletes it, and the confirm button in the `error` color. Where the user may also delete it for the others
 * ([DeleteScope.Choice]), a check box offers "Delete for everyone", unchecked at first and kept across rotation.
 *
 * @param onConfirm called with whether the check box is checked.
 */
@Composable
internal fun DeleteMediaDialog(
    confirmation: DeleteConfirmation,
    media: ViewerMedia,
    onConfirm: (forEveryone: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var forEveryone by rememberSaveable(confirmation) { mutableStateOf(false) }
    val title =
        when (media) {
            is ViewerMedia.Image -> R.string.media_delete_photo_title
            is ViewerMedia.Clip -> R.string.media_delete_video_title
            is ViewerMedia.Gif -> R.string.media_delete_gif_title
        }
    val body =
        when (confirmation.scope) {
            DeleteScope.ForMe -> R.string.media_delete_for_me_body
            DeleteScope.ForEveryone -> R.string.media_delete_for_everyone_body
            DeleteScope.Choice -> R.string.media_delete_choice_body
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onConfirm(forEveryone) },
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(
                    stringResource(R.string.media_delete_confirm),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(
                    stringResource(R.string.media_delete_cancel),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }
        },
        icon = { Icon(painter = painterResource(UiR.drawable.ic_delete), contentDescription = null) },
        title = { Text(stringResource(title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                Text(stringResource(body))
                if (confirmation.scope == DeleteScope.Choice) {
                    ForEveryoneCheckbox(checked = forEveryone, onCheckedChange = { forEveryone = it })
                }
            }
        },
    )
}

/** "Delete for everyone" with its check box, toggled as one, which screen readers read as one check box. */
@Composable
private fun ForEveryoneCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(text = stringResource(R.string.media_delete_for_everyone), style = MaterialTheme.typography.bodyMedium)
    }
}

@PingogramPreviews
@Composable
private fun DeleteMediaDialogPreview() {
    PingogramPreview {
        DeleteMediaDialog(
            confirmation = DeleteConfirmation(messageId = 1, scope = DeleteScope.Choice),
            media = MediaPreviewData.photo.current.media,
            onConfirm = {},
            onDismiss = {},
        )
    }
}
