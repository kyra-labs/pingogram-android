package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.material3.ListItemShapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.kyralabs.pingogram.core.ui.component.PhotoEditor
import dev.kyralabs.pingogram.core.ui.component.PhotoTask
import dev.kyralabs.pingogram.feature.settings.R

/**
 * The profile photo in a row of its own ([shapes], a [SettingsSegment]): [PhotoEditor] with the user's photo and name
 * ([account]; the initials' placeholder until TDLib has announced the user), what is being done with it ([task]),
 * "Set new photo" ([onPickPhoto]) and "Remove photo" ([onRemovePhoto]).
 */
@Composable
internal fun ProfilePhotoEditor(
    account: ProfileAccount?,
    task: PhotoTask,
    shapes: ListItemShapes,
    onPickPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsSegment(shapes, modifier) {
        PhotoEditor(
            photo = account?.photo,
            name = account?.name.orEmpty(),
            avatarId = account?.userId ?: 0,
            task = task,
            contentDescription = stringResource(R.string.settings_profile_photo),
            onPick = onPickPhoto,
            onRemove = onRemovePhoto,
        )
    }
}
