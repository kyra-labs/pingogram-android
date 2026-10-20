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
 * expressive [HorizontalFloatingToolbar] in the standard colors, at the bottom center below the caption, as Google
 * Photos' viewer has it. Round icon buttons whose shape morphs while pressed, each named by its label for screen
 * readers and in a [PlainTooltip] on a long press or hover:
 *
 * - **Share** and **Save** where Telegram lets the content leave it ([MediaActions.canSave]);
 * - **Delete** where TDLib allows it ([MediaActions.canDelete]); it asks first;
 * - **More**, a menu ([DropdownMenuPopup]) with "Show in chat" and, for a photo that may leave Telegram ([canCopy]),
 *   "Copy".
 *
 * There is no Forward yet: Pingogram has no forward flow (M3). The button whose action waits for the item's file (More
 * for Copy) shows the download's progress ([fileState], a determinate wavy indicator) instead of its icon, or a loading
 * indicator while the file is copied into the gallery; meanwhile Share, Save and Copy are disabled. Screen readers hear
 * what each button does and that it is under way.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ViewerToolbar(
    actions: MediaActions,
    canCopy: Boolean,
    fileState: FileState?,
    onEvent: (MediaViewerEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val idle = actions.pending == null
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier,
        colors = FloatingToolbarDefaults.standardFloatingToolbarColors(),
    ) {
        if (actions.canSave) {
            ToolbarButton(
                icon = UiR.drawable.ic_share,
                label = stringResource(R.string.media_share),
                onClick = { onEvent(MediaViewerEvent.Share) },
                enabled = idle,
                pending = Pending(actions.pending == MediaAction.Share, stringResource(R.string.media_share_pending)),
                fileState = fileState,
            )
            ToolbarButton(
                icon = UiR.drawable.ic_download,
                label = stringResource(R.string.media_save),
                onClick = { onEvent(MediaViewerEvent.Save) },
                enabled = idle,
                pending = Pending(actions.pending == MediaAction.Save, stringResource(R.string.media_save_pending)),
                fileState = fileState,
            )
        }
        if (actions.canDelete) {
            ToolbarButton(
                icon = UiR.drawable.ic_delete,
                label = stringResource(R.string.media_delete),
                onClick = { onEvent(MediaViewerEvent.Delete) },
            )
        }
        MoreMenu(
            canCopy = canCopy && actions.canSave,
            copyEnabled = idle,
            copyPending = Pending(actions.pending == MediaAction.Copy, stringResource(R.string.media_copy_pending)),
            fileState = fileState,
            onEvent = onEvent,
        )
    }
}

/**
 * More, and the menu it opens: "Show in chat", then "Copy" where [canCopy] (disabled unless [copyEnabled]). A menu of
 * one group ([DropdownMenuGroup]) whose items take the expressive menu's shapes; choosing one closes it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MoreMenu(
    canCopy: Boolean,
    copyEnabled: Boolean,
    copyPending: Pending,
    fileState: FileState?,
    onEvent: (MediaViewerEvent) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        ToolbarButton(
            icon = UiR.drawable.ic_more_vert,
            label = stringResource(R.string.media_more),
            onClick = { expanded = true },
            pending = copyPending,
            fileState = fileState,
        )
        DropdownMenuPopup(expanded = expanded, onDismissRequest = { expanded = false }) {
            val count = if (canCopy) 2 else 1
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 0, count = 1)) {
                MenuItem(
                    icon = UiR.drawable.ic_chat,
                    label = stringResource(R.string.media_show_in_chat),
                    index = 0,
                    count = count,
                ) {
                    expanded = false
                    onEvent(MediaViewerEvent.ShowInChat)
                }
                if (canCopy) {
                    MenuItem(
                        icon = UiR.drawable.ic_content_copy,
                        label = stringResource(R.string.media_copy),
                        index = 1,
                        count = count,
                        enabled = copyEnabled,
                    ) {
                        expanded = false
                        onEvent(MediaViewerEvent.Copy)
                    }
                }
            }
        }
    }
}

/** An item of the More menu, [index] of [count]: its [icon] and [label]. */
@Suppress("LongParameterList") // What the item shows, where it is in the group, and what it does.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MenuItem(
    @DrawableRes icon: Int,
    label: String,
    index: Int,
    count: Int,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        onClick = onClick,
        text = { Text(label) },
        shape = MenuDefaults.itemShape(index, count).shape,
        leadingIcon = { Icon(painter = painterResource(icon), contentDescription = null) },
        enabled = enabled,
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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ToolbarButton(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    pending: Pending? = null,
    fileState: FileState? = null,
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
                PendingIndicator(fileState)
            } else {
                Icon(painter = painterResource(icon), contentDescription = null)
            }
        }
    }
}

/**
 * The download of the file an action waits for, [fileState]: a determinate wavy indicator while it downloads, or a
 * loading indicator once it is on the device (the copy into the gallery runs).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PendingIndicator(fileState: FileState?) {
    val size = Modifier.size(MaterialTheme.sizes.inlineProgressIndicator)
    when (fileState) {
        is FileState.Downloading -> CircularWavyProgressIndicator(progress = { fileState.progress }, modifier = size)
        is FileState.NotDownloaded, null -> CircularWavyProgressIndicator(progress = { 0f }, modifier = size)
        is FileState.Downloaded -> LoadingIndicator(size)
    }
}

@PingogramPreviews
@Composable
private fun ViewerToolbarPreview() {
    PingogramPreview {
        ViewerToolbar(
            actions = MediaActions(canSave = true, canDelete = true),
            canCopy = true,
            fileState = null,
            onEvent = {},
        )
    }
}
