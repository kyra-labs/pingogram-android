package dev.kyralabs.pingogram.core.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.core.ui.R
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing

/**
 * A photo to change, the user's own (Edit profile) or a group's or channel's (its info page): the photo, large and
 * round ([Avatar], [name]'s initials on [avatarId]'s colors without one), with "Set new photo" (a tonal button, which
 * [onPick] answers with the system's photo picker) and, when there is a photo, "Remove photo" (a text button in the
 * error role, [onRemove]) below it; both shape-morph when pressed. While the photo uploads, a
 * [CircularWavyProgressIndicator] around the avatar shows its progress, animated on the motion scheme's effects spring
 * and read in the draw phase; before that, and while preparing or removing, a [ContainedLoadingIndicator] on it. The
 * buttons wait meanwhile ([task]), and wrap below each other where they do not fit side by side. The caller puts it in
 * its own container; [contentDescription] names the photo for screen readers.
 */
@Suppress("LongParameterList") // The photo, what happens to it, and the two actions.
@Composable
fun PhotoEditor(
    photo: ProfilePhoto?,
    name: String,
    avatarId: Long,
    task: PhotoTask,
    contentDescription: String,
    onPick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes placeholderIcon: Int? = null,
) {
    val spacing = MaterialTheme.spacing
    Column(
        modifier = modifier.fillMaxWidth().padding(spacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        Box(
            modifier = Modifier.size(MaterialTheme.sizes.avatarLarge + spacing.small * 2),
            contentAlignment = Alignment.Center,
        ) {
            Avatar(
                photo = photo,
                name = name,
                id = avatarId,
                size = MaterialTheme.sizes.avatarLarge,
                placeholderIcon = placeholderIcon,
                contentDescription = contentDescription,
            )
            PhotoTaskProgress(task, Modifier.matchParentSize())
        }
        PhotoButtons(idle = task == PhotoTask.None, hasPhoto = photo != null, onPick = onPick, onRemove = onRemove)
    }
}

/** "Set new photo" and, with a photo ([hasPhoto]), "Remove photo"; enabled while [idle]. */
@Composable
private fun PhotoButtons(
    idle: Boolean,
    hasPhoto: Boolean,
    onPick: () -> Unit,
    onRemove: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(spacing.small, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(spacing.small),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalButton(onClick = onPick, shapes = ButtonDefaults.shapes(), enabled = idle) {
            Icon(
                painter = painterResource(R.drawable.ic_add_a_photo),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(
                text = stringResource(R.string.photo_editor_set),
                style = MaterialTheme.typography.labelLargeEmphasized,
            )
        }
        if (hasPhoto) {
            TextButton(
                onClick = onRemove,
                shapes = ButtonDefaults.shapes(),
                enabled = idle,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(
                    text = stringResource(R.string.photo_editor_remove),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }
        }
    }
}

/** What [task] shows over the avatar, which [modifier] sizes the ring around. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PhotoTaskProgress(
    task: PhotoTask,
    modifier: Modifier,
) {
    val description =
        when (task) {
            PhotoTask.None -> return
            PhotoTask.Preparing -> stringResource(R.string.photo_editor_preparing)
            is PhotoTask.Uploading -> stringResource(R.string.photo_editor_uploading)
            PhotoTask.Removing -> stringResource(R.string.photo_editor_removing)
        }
    val progress = (task as? PhotoTask.Uploading)?.progress
    if (progress != null) {
        val shown by animateFloatAsState(progress, MaterialTheme.motionScheme.defaultEffectsSpec())
        CircularWavyProgressIndicator(
            progress = { shown },
            modifier = modifier.semantics { contentDescription = description },
        )
    } else {
        ContainedLoadingIndicator(Modifier.semantics { contentDescription = description })
    }
}
