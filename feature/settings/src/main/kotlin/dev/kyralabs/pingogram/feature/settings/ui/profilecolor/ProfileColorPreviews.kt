package dev.kyralabs.pingogram.feature.settings.ui.profilecolor

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import dev.kyralabs.pingogram.core.domain.model.AccentColorOption
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/** Sample "Your color" pages for previews and goldens. */
internal object ProfileColorPreviewData {
    val content =
        ProfileColorUiState.Content(
            userId = 7,
            name = "YamTee",
            photo = null,
            current = 5,
            chosen = 0,
            colors = (0..6).map { AccentColorOption(it, it) }.toImmutableList(),
            premiumColors =
                persistentListOf(AccentColorOption(8, 0), AccentColorOption(9, 4), AccentColorOption(10, 2)),
        )
}

internal class ProfileColorStates : PreviewParameterProvider<ProfileColorUiState> {
    override val values = sequenceOf(ProfileColorPreviewData.content, ProfileColorUiState.Loading)
}

@PingogramPreviews
@Composable
private fun ProfileColorScreenPreview(
    @PreviewParameter(ProfileColorStates::class) state: ProfileColorUiState,
) {
    PingogramPreview { ProfileColorScreen(state, onEvent = {}, onBack = {}) }
}
