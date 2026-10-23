package dev.kyralabs.pingogram.feature.chat.ui.location

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.ui.preview.LocationPreviewData

// Previews of every step of the "Location" sheet ([ShareLocationContent]), light and dark.

@PingogramPreviews
@Composable
private fun ShareLocationPermissionPreview() {
    PingogramPreview { ShareLocationPreviewContent(ShareLocationUiState.Permission()) }
}

@PingogramPreviews
@Composable
private fun ShareLocationDeniedPreview() {
    PingogramPreview { ShareLocationPreviewContent(ShareLocationUiState.Permission(deniedForGood = true)) }
}

@PingogramPreviews
@Composable
private fun ShareLocationSearchingPreview() {
    PingogramPreview { ShareLocationPreviewContent(ShareLocationUiState.Locating()) }
}

@PingogramPreviews
@Composable
private fun ShareLocationFoundPreview() {
    PingogramPreview { ShareLocationPreviewContent(LocationPreviewData.found) }
}

@PingogramPreviews
@Composable
private fun ShareLocationRefiningPreview() {
    PingogramPreview { ShareLocationPreviewContent(LocationPreviewData.refining) }
}

@PingogramPreviews
@Composable
private fun ShareLocationApproximatePreview() {
    PingogramPreview { ShareLocationPreviewContent(LocationPreviewData.approximate) }
}

@PingogramPreviews
@Composable
private fun ShareLocationNotFoundPreview() {
    PingogramPreview { ShareLocationPreviewContent(ShareLocationUiState.NotFound) }
}

@Composable
private fun ShareLocationPreviewContent(state: ShareLocationUiState) {
    ShareLocationContent(
        state = state,
        canSchedule = true,
        canOpenMaps = true,
        onEvent = {},
        onSend = { _, _ -> },
        onSchedule = {},
        modifier = Modifier.padding(top = MaterialTheme.spacing.large),
    )
}
