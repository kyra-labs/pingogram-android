package dev.kyralabs.pingogram.feature.media.storyalbum

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import dev.kyralabs.pingogram.feature.media.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * "More options" in the top bar of the user's own album: an [IconButton] opening an expressive menu, one group of the
 * album's [AlbumAction]s, Delete last in `error`; picking one closes it and calls [onAction].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AlbumMenuButton(onAction: (AlbumAction) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, shapes = IconButtonDefaults.shapes()) {
            Icon(painterResource(UiR.drawable.ic_more_vert), stringResource(R.string.story_album_more))
        }
        DropdownMenuPopup(expanded = open, onDismissRequest = { open = false }) {
            val actions = AlbumAction.entries
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 0, count = 1)) {
                actions.forEachIndexed { index, action ->
                    DropdownMenuItem(
                        onClick = {
                            open = false
                            onAction(action)
                        },
                        text = {
                            Text(
                                text = stringResource(action.label),
                                color =
                                    if (action == AlbumAction.Delete) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                            )
                        },
                        shape = MenuDefaults.itemShape(index, actions.size).shape,
                    )
                }
            }
        }
    }
}

/**
 * The dialog of the menu's [action] for the album named [name]: renaming it ([AlbumNameDialog]), or "Delete this
 * album?" saying its stories stay, with "Delete" in `error`. Either ends with [onDismiss]; what is chosen goes to
 * [onEvent].
 */
@Composable
internal fun AlbumActionDialog(
    action: AlbumAction,
    name: String,
    onEvent: (StoryAlbumEvent) -> Unit,
    onDismiss: () -> Unit,
) {
    when (action) {
        AlbumAction.Rename -> {
            AlbumNameDialog(
                title = R.string.story_album_rename_title,
                confirm = R.string.story_album_save,
                onConfirm = { newName ->
                    onDismiss()
                    onEvent(StoryAlbumEvent.Rename(newName))
                },
                onDismiss = onDismiss,
                initial = name,
            )
        }

        AlbumAction.Delete -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.story_album_delete_title)) },
                text = { Text(stringResource(R.string.story_album_delete_body)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDismiss()
                            onEvent(StoryAlbumEvent.Delete)
                        },
                        shapes = ButtonDefaults.shapes(),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text(stringResource(R.string.story_album_delete_confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(R.string.story_album_cancel))
                    }
                },
            )
        }

        AlbumAction.AddStories, AlbumAction.RemoveStories -> {}
    }
}
