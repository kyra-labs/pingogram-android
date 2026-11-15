package dev.kyralabs.pingogram.feature.media.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.feature.media.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The viewer's actions on the current item (`docs/DESIGN_SYSTEM.md` §6, media viewer; owner decision D20): an
 * expressive [HorizontalFloatingToolbar] in the standard colors, at the bottom center below the caption, within thumb's
 * reach and clear of the media. Round icon buttons whose shape morphs while pressed, each named by its label for screen
 * readers and in a [PlainTooltip] on a long press or hover:
 *
 * - **Forward** where TDLib allows it ([MediaActions.canForward]; never protected content): the forward sheet;
 * - **Share** and **Save** where Telegram lets the content leave it ([MediaActions.canSave]);
 * - **More**, a menu ([DropdownMenuPopup]) with "Show in chat" and, for a photo ([photoFileId]) that may leave
 *   Telegram, "Copy" and "Make a sticker" (boards 14a, 14b); then, in a group of its own in `error`, "Delete" where
 *   TDLib allows it ([MediaActions.canDelete]), which asks first (owner decision 2026-11-15, board 25: deleting is
 *   destructive and rare, so it leaves the toolbar).
 *
 * The button whose action waits for the item's file (More for Copy) shows the download's progress ([file], a
 * determinate wavy indicator) instead of its icon, or a loading indicator while the file is copied into the gallery;
 * meanwhile Share, Save and Copy are disabled. Screen readers hear what each button does and that it is under way.
 */
@Composable
internal fun ViewerToolbar(
    actions: MediaActions,
    photoFileId: Int?,
    file: ItemFile?,
    onEvent: (MediaViewerEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val idle = actions.pending == null
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier,
        colors = FloatingToolbarDefaults.standardFloatingToolbarColors(),
    ) {
        if (actions.canForward) {
            ToolbarButton(
                icon = UiR.drawable.ic_forward,
                label = stringResource(R.string.media_forward),
                onClick = { onEvent(MediaViewerEvent.Forward) },
            )
        }
        if (actions.canSave) {
            ToolbarButton(
                icon = UiR.drawable.ic_share,
                label = stringResource(R.string.media_share),
                onClick = { onEvent(MediaViewerEvent.Share) },
                enabled = idle,
                pending = Pending(actions.pending == MediaAction.Share, stringResource(R.string.media_share_pending)),
                file = file,
            )
            ToolbarButton(
                icon = UiR.drawable.ic_download,
                label = stringResource(R.string.media_save),
                onClick = { onEvent(MediaViewerEvent.Save) },
                enabled = idle,
                pending = Pending(actions.pending == MediaAction.Save, stringResource(R.string.media_save_pending)),
                file = file,
            )
        }
        MoreMenu(
            photoFileId = photoFileId?.takeIf { actions.canSave },
            canDelete = actions.canDelete,
            copyEnabled = idle,
            copyPending = Pending(actions.pending == MediaAction.Copy, stringResource(R.string.media_copy_pending)),
            file = file,
            onEvent = onEvent,
        )
    }
}

/**
 * More, and the menu it opens: "Show in chat", then for a photo that may leave Telegram ([photoFileId]) "Copy"
 * (disabled unless [copyEnabled]) and "Make a sticker"; and where [canDelete], "Delete" in `error` in a second group.
 * The groups ([DropdownMenuGroup]) and their items take the expressive menu's shapes; choosing an item closes it.
 */
@Suppress("LongParameterList") // What the menu offers, the state of Copy, and where its choices go.
@Composable
private fun MoreMenu(
    photoFileId: Int?,
    canDelete: Boolean,
    copyEnabled: Boolean,
    copyPending: Pending,
    file: ItemFile?,
    onEvent: (MediaViewerEvent) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        ToolbarButton(
            icon = UiR.drawable.ic_more_vert,
            label = stringResource(R.string.media_more),
            onClick = { expanded = true },
            pending = copyPending,
            file = file,
        )
        DropdownMenuPopup(expanded = expanded, onDismissRequest = { expanded = false }) {
            val groups = if (canDelete) 2 else 1
            val choose = { event: MediaViewerEvent ->
                expanded = false
                onEvent(event)
            }
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 0, count = groups)) {
                EverydayItems(photoFileId, copyEnabled, choose)
            }
            if (canDelete) {
                DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 1, count = groups)) {
                    MenuItem(
                        icon = UiR.drawable.ic_delete,
                        label = stringResource(R.string.media_delete),
                        index = 0,
                        count = 1,
                        destructive = true,
                    ) { choose(MediaViewerEvent.Delete) }
                }
            }
        }
    }
}

/** The More menu's first group: "Show in chat", and for a photo that may leave Telegram "Copy" and "Make a sticker". */
@Composable
private fun EverydayItems(
    photoFileId: Int?,
    copyEnabled: Boolean,
    choose: (MediaViewerEvent) -> Unit,
) {
    val count = if (photoFileId != null) PHOTO_MENU_SIZE else 1
    MenuItem(
        icon = UiR.drawable.ic_chat,
        label = stringResource(R.string.media_show_in_chat),
        index = 0,
        count = count,
    ) { choose(MediaViewerEvent.ShowInChat) }
    if (photoFileId != null) {
        MenuItem(
            icon = UiR.drawable.ic_content_copy,
            label = stringResource(R.string.media_copy),
            index = 1,
            count = count,
            enabled = copyEnabled,
        ) { choose(MediaViewerEvent.Copy) }
        MenuItem(
            icon = UiR.drawable.ic_mood,
            label = stringResource(R.string.media_make_sticker),
            index = 2,
            count = count,
        ) { choose(MediaViewerEvent.MakeSticker(photoFileId)) }
    }
}

/** An item of the More menu, [index] of [count]: its [icon] and [label], in `error` where [destructive]. */
@Suppress("LongParameterList") // What the item shows, where it is in the group, and what it does.
@Composable
private fun MenuItem(
    @DrawableRes icon: Int,
    label: String,
    index: Int,
    count: Int,
    enabled: Boolean = true,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val error = MaterialTheme.colorScheme.error
    DropdownMenuItem(
        onClick = onClick,
        text = { Text(label) },
        shape = MenuDefaults.itemShape(index, count).shape,
        leadingIcon = { Icon(painter = painterResource(icon), contentDescription = null) },
        enabled = enabled,
        colors =
            if (destructive) {
                MenuDefaults.itemColors(textColor = error, leadingIconColor = error)
            } else {
                MenuDefaults.itemColors()
            },
    )
}

/** Whether an action waits for the item's file ([isPending]), and what screen readers hear meanwhile ([label]). */
private class Pending(
    val isPending: Boolean,
    val label: String,
)

/**
 * A button of the toolbar: its [icon], named [label] for screen readers and in a tooltip; or while [pending], how far
 * its file is ([PendingIndicator]).
 */
@Suppress("LongParameterList") // What the button shows and says, whether it can be used, and its pending state.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolbarButton(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    pending: Pending? = null,
    file: ItemFile? = null,
) {
    val isPending = pending?.isPending == true
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        IconButton(
            onClick = onClick,
            modifier =
                Modifier.semantics {
                    contentDescription = label
                    if (isPending) stateDescription = pending.label
                },
            enabled = enabled,
            shapes = IconButtonDefaults.shapes(),
        ) {
            if (isPending) {
                PendingIndicator(file)
            } else {
                Icon(painter = painterResource(icon), contentDescription = null)
            }
        }
    }
}

/**
 * The download of the file an action waits for, [file]: a determinate wavy indicator while it downloads, or a
 * loading indicator once it is on the device (the copy into the gallery runs).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PendingIndicator(file: ItemFile?) {
    val size = Modifier.size(MaterialTheme.sizes.inlineProgressIndicator)
    val lookup = LocalFileProgress.current
    when (val state = file?.state) {
        // The progress is read where it is drawn (LocalFileProgress): a download's updates compose nothing.
        is FileState.Downloading -> {
            CircularWavyProgressIndicator(progress = { progressOf(file.id, state, lookup) }, modifier = size)
        }

        is FileState.NotDownloaded, null -> {
            CircularWavyProgressIndicator(progress = { 0f }, modifier = size)
        }

        is FileState.Downloaded -> {
            LoadingIndicator(size)
        }
    }
}

@PingogramPreviews
@Composable
private fun ViewerToolbarPreview() {
    PingogramPreview {
        ViewerToolbar(
            actions = MediaActions(canForward = true, canSave = true, canDelete = true),
            photoFileId = 1,
            file = null,
            onEvent = {},
        )
    }
}

/** "Show in chat", "Copy" and "Make a sticker". */
private const val PHOTO_MENU_SIZE = 3
