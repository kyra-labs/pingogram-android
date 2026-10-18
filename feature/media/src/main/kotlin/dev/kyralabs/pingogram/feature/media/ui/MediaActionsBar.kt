package dev.kyralabs.pingogram.feature.media.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FlexibleBottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.feature.media.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The viewer's bottom app bar (`docs/DESIGN_SYSTEM.md` §6: the media viewer's `FlexibleBottomAppBar` actions), in the
 * bottom chrome below the caption: Share and Save of the current item, Material Symbols icon buttons whose shape morphs
 * while pressed, side by side in the middle at the bar's fixed spacing, on phones and tablets alike. Transparent, over
 * the chrome's gradient, whose insets it already has. Only where Telegram lets the
 * content leave it ([MediaActions.canSave]); the screen leaves it out otherwise.
 *
 * The button whose action waits for the item's file shows the download's progress ([fileState], a determinate wavy
 * indicator) instead of its icon, or a loading indicator while the file is copied into the gallery; meanwhile both
 * buttons are disabled. Screen readers hear what each button does and that it is under way.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ViewerActions(
    actions: MediaActions,
    fileState: FileState?,
    onEvent: (MediaViewerEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlexibleBottomAppBar(
        modifier = modifier,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        horizontalArrangement = BottomAppBarDefaults.FlexibleFixedHorizontalArrangement,
        windowInsets = WindowInsets(0, 0, 0, 0),
    ) {
        ActionButton(
            icon = UiR.drawable.ic_share,
            label = stringResource(R.string.media_share),
            pendingLabel = stringResource(R.string.media_share_pending),
            pending = actions.pending == MediaAction.Share,
            enabled = actions.pending == null,
            fileState = fileState,
            onClick = { onEvent(MediaViewerEvent.Share) },
        )
        ActionButton(
            icon = UiR.drawable.ic_download,
            label = stringResource(R.string.media_save),
            pendingLabel = stringResource(R.string.media_save_pending),
            pending = actions.pending == MediaAction.Save,
            enabled = actions.pending == null,
            fileState = fileState,
            onClick = { onEvent(MediaViewerEvent.Save) },
        )
    }
}

/** An action of the bar: its [icon], or while it is [pending], how far its file is ([PendingIndicator]). */
@Suppress("LongParameterList") // What the button shows and says, its two states, and what it does.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ActionButton(
    @DrawableRes icon: Int,
    label: String,
    pendingLabel: String,
    pending: Boolean,
    enabled: Boolean,
    fileState: FileState?,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier =
            Modifier.semantics {
                contentDescription = label
                if (pending) stateDescription = pendingLabel
            },
        enabled = enabled,
        shapes = IconButtonDefaults.shapes(),
    ) {
        if (pending) {
            PendingIndicator(fileState)
        } else {
            Icon(painter = painterResource(icon), contentDescription = null)
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
            TextButton(onClick = { onAnswer(true) }) {
                Text(
                    text = stringResource(R.string.media_storage_rationale_continue),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { onAnswer(false) }) {
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
