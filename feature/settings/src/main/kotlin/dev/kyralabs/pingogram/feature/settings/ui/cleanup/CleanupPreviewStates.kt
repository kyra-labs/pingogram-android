package dev.kyralabs.pingogram.feature.settings.ui.cleanup

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import dev.kyralabs.pingogram.core.domain.repository.CleanupSettings

/** The page's states for previews and goldens: none to suggest, loading, and off. */
internal class CleanupPreviewStates : PreviewParameterProvider<CleanupUiState> {
    override val values: Sequence<CleanupUiState> =
        sequenceOf(none, loading, off)

    companion object {
        val loading = CleanupUiState(settings = CleanupSettings(), loading = true)

        val none = CleanupUiState(settings = CleanupSettings(months = 6), loading = false)

        val off = CleanupUiState(settings = CleanupSettings(enabled = false), loading = false)
    }
}
