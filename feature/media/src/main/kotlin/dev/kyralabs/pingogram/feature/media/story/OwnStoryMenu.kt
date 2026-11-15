package dev.kyralabs.pingogram.feature.media.story

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.feature.media.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * ⋮ on the user's own story (board 26, owner decision 2026-11-15: Delete is in ⋮): an `IconButton` in
 * `onMediaOverlay` that opens an expressive [DropdownMenuPopup] with "Delete" in `error`, which asks first
 * ([DeleteStoryDialog]). The story pauses while the menu shows.
 */
@Composable
internal fun OwnStoryMenu(
    own: OwnStoryUi,
    onEvent: (StoryViewerEvent) -> Unit,
) {
    Box {
        IconButton(
            onClick = { onEvent(StoryViewerEvent.Own.MenuOpen(true)) },
            shapes = IconButtonDefaults.shapes(),
        ) {
            Icon(
                painter = painterResource(UiR.drawable.ic_more_vert),
                contentDescription = stringResource(R.string.story_more),
                tint = MaterialTheme.pingogramColors.onMediaOverlay,
            )
        }
        DropdownMenuPopup(
            expanded = own.menuOpen,
            onDismissRequest = { onEvent(StoryViewerEvent.Own.MenuOpen(false)) },
        ) {
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 0, count = 1)) {
                val error = MaterialTheme.colorScheme.error
                DropdownMenuItem(
                    onClick = { onEvent(StoryViewerEvent.Own.Delete) },
                    text = { Text(stringResource(R.string.story_delete)) },
                    shape = MenuDefaults.itemShape(0, 1).shape,
                    leadingIcon = { Icon(painterResource(UiR.drawable.ic_delete), contentDescription = null) },
                    colors = MenuDefaults.itemColors(textColor = error, leadingIconColor = error),
                )
            }
        }
    }
}

/**
 * "Delete this story?": a destructive [AlertDialog] with the delete symbol, what deleting does, "Cancel" and "Delete"
 * in `error`, both disabled while TDLib deletes it ([isDeleting]).
 */
@Composable
internal fun DeleteStoryDialog(
    isDeleting: Boolean,
    onEvent: (StoryViewerEvent) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isDeleting) onEvent(StoryViewerEvent.Own.DeleteDismissed) },
        icon = { Icon(painterResource(UiR.drawable.ic_delete), contentDescription = null) },
        title = { Text(stringResource(R.string.story_delete_title)) },
        text = { Text(stringResource(R.string.story_delete_body)) },
        confirmButton = {
            TextButton(
                onClick = { onEvent(StoryViewerEvent.Own.DeleteConfirmed) },
                enabled = !isDeleting,
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.story_delete_confirm)) }
        },
        dismissButton = {
            TextButton(
                onClick = { onEvent(StoryViewerEvent.Own.DeleteDismissed) },
                enabled = !isDeleting,
                shapes = ButtonDefaults.shapes(),
            ) { Text(stringResource(R.string.story_delete_cancel)) }
        },
    )
}

@PingogramPreviews
@Composable
private fun DeleteStoryDialogPreview() {
    PingogramPreview { DeleteStoryDialog(isDeleting = false, onEvent = {}) }
}
