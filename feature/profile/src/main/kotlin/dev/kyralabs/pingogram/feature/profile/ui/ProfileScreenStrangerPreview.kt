package dev.kyralabs.pingogram.feature.profile.ui

import androidx.compose.runtime.Composable
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.preview.PingogramVariantPreviews
import dev.kyralabs.pingogram.feature.profile.ui.preview.ProfilePreviewData

/** Someone not in the contacts who wrote first: the card above the actions, and their phone number hidden. */
@PingogramPreviews
@PingogramVariantPreviews
@Composable
private fun ProfileScreenStrangerPreview() {
    ProfileScreenPreviewContent(ProfilePreviewData.stranger, ProfilePreviewData.emptyMedia)
}
