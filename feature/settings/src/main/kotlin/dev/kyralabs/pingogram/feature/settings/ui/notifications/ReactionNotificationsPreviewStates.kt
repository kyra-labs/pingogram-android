package dev.kyralabs.pingogram.feature.settings.ui.notifications

import dev.kyralabs.pingogram.core.domain.model.ReactionNotificationSettings
import dev.kyralabs.pingogram.core.domain.model.ReactionNotificationSource

/** The states of the reactions page, shared by previews and screenshot tests. */
internal object ReactionNotificationsPreviewStates {
    val content =
        ReactionNotificationsUiState(
            ReactionNotificationSettings(
                messages = ReactionNotificationSource.All,
                stories = ReactionNotificationSource.Contacts,
                pollVotes = ReactionNotificationSource.None,
                showPreview = true,
            ),
        )

    val loading = ReactionNotificationsUiState()
}
