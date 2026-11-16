package dev.kyralabs.pingogram.feature.profile.ui

import androidx.compose.runtime.Composable
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.feature.profile.ui.preview.ProfilePreviewData

/** A private chat with "Disable sharing" on: the Info group says so. */
@PingogramPreviews
@Composable
private fun ProfileScreenSharingDisabledPreview() {
    ProfileScreenPreviewContent(ProfilePreviewData.sharingDisabled)
}
