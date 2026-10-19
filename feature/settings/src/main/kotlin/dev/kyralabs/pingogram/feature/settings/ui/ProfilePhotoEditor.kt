package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R

/**
 * The profile photo, large and round ([Avatar]), with "Set new photo" (a tonal button) and, when there is a photo,
 * "Remove photo" (a text button in the error role) below it; both shape-morph when pressed. While the photo uploads,
 * a [CircularWavyProgressIndicator] around the avatar shows its progress, animated on the motion scheme's effects
 * spring and read in the draw phase; before that, and while preparing or removing, a [ContainedLoadingIndicator] on
 * it. The buttons wait meanwhile, and wrap below each other where they do not fit side by side.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ProfilePhotoEditor(
    account: ProfileAccount?,
    task: PhotoTask,
    onPickPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        Box(
            modifier = Modifier.size(MaterialTheme.sizes.avatarLarge + spacing.small * 2),
            contentAlignment = Alignment.Center,
        ) {
            if (account != null) {
                Avatar(
                    photo = account.photo,
                    name = account.name,
                    id = account.userId,
                    size = MaterialTheme.sizes.avatarLarge,
                    contentDescription = stringResource(R.string.settings_profile_photo),
                )
            }
            PhotoTaskProgress(task, Modifier.matchParentSize())
        }
        val idle = task == PhotoTask.None
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.small, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(spacing.small),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(onClick = onPickPhoto, shapes = ButtonDefaults.shapes(), enabled = idle) {
                Icon(
                    painter = painterResource(R.drawable.ic_add_a_photo),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(
                    text = stringResource(R.string.settings_profile_photo_set),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }
            if (account?.photo != null) {
                TextButton(
                    onClick = onRemovePhoto,
                    shapes = ButtonDefaults.shapes(),
                    enabled = idle,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(
                        text = stringResource(R.string.settings_profile_photo_remove),
                        style = MaterialTheme.typography.labelLargeEmphasized,
                    )
                }
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
            PhotoTask.Preparing -> stringResource(R.string.settings_profile_photo_preparing)
            is PhotoTask.Uploading -> stringResource(R.string.settings_profile_photo_uploading)
            PhotoTask.Removing -> stringResource(R.string.settings_profile_photo_removing)
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
