package dev.kyralabs.pingogram.feature.settings.ui.preview

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.UsernameCheck
import dev.kyralabs.pingogram.core.domain.model.UsernameProblem
import dev.kyralabs.pingogram.core.ui.component.PhotoTask
import dev.kyralabs.pingogram.feature.settings.ui.EditProfileDialog
import dev.kyralabs.pingogram.feature.settings.ui.EditProfileUiState
import dev.kyralabs.pingogram.feature.settings.ui.UsernameStatus

/** Every state of the Edit profile page for its previews, named in the preview pane. */
internal class EditProfilePreviewStates : PreviewParameterProvider<EditProfileUiState> {
    private val available = SettingsPreviewData.editedProfile(UsernameStatus.Checked(UsernameCheck.Available))

    private val states =
        listOf(
            "Profile" to SettingsPreviewData.editProfileWithPhoto,
            "Username available" to available,
            "Username taken" to SettingsPreviewData.editedProfile(UsernameStatus.Checked(UsernameCheck.Taken)),
            "Username too short" to
                SettingsPreviewData.editedProfile(UsernameStatus.Problem(UsernameProblem.TooShort), username = "ada"),
            "Saving" to available.copy(isSaving = true),
            "Uploading photo" to
                SettingsPreviewData.editProfileWithPhoto.copy(photoTask = PhotoTask.Uploading(progress = 0.6f)),
            "Discard changes" to available.copy(dialog = EditProfileDialog.Discard),
            "Remove photo" to SettingsPreviewData.editProfileWithPhoto.copy(dialog = EditProfileDialog.RemovePhoto),
            "Loading" to EditProfileUiState.Loading,
            "Error" to EditProfileUiState.Error(DomainError.Network),
        )

    override val values: Sequence<EditProfileUiState> = states.asSequence().map { it.second }

    override fun getDisplayName(index: Int): String = states[index].first
}
