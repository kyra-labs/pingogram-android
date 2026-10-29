package dev.kyralabs.pingogram.feature.settings.ui.notifications

import androidx.compose.runtime.Composable
import dev.kyralabs.pingogram.core.domain.model.ReactionNotificationSettings
import dev.kyralabs.pingogram.core.domain.model.ReactionNotificationSource
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews

@PingogramPreviews
@Composable
private fun ReactionNotificationsScreenPreview() {
    PingogramPreview {
        ReactionNotificationsScreen(
            ReactionNotificationsPreviewStates.content,
            onEvent = {},
            onBack = {},
        )
    }
}

@PingogramPreviews
@Composable
private fun ReactionNotificationsScreenLoadingPreview() {
    PingogramPreview {
        ReactionNotificationsScreen(
            ReactionNotificationsPreviewStates.loading,
            onEvent = {},
            onBack = {},
        )
    }
}
