package dev.kyralabs.pingogram.feature.settings.ui.reminders

import androidx.compose.runtime.Composable
import dev.kyralabs.pingogram.core.ui.component.AvatarShape
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.UiText
import kotlinx.collections.immutable.persistentListOf

/** The Reminders page's states for previews and screenshot tests: board 29e's reminders, and none (29f). */
internal object RemindersPreviewData {
    val content =
        RemindersUiState.Content(
            upcoming =
                persistentListOf(
                    ReminderRow(
                        chatId = 1,
                        messageId = 11,
                        chatTitle = "Trip — Coorg 2026",
                        photo = null,
                        avatarShape = AvatarShape.Group,
                        time = UiText.Plain("Sun, 9:00 AM"),
                        preview = UiText.Plain("Aisha: Train leaves 6:40 sharp. Be at Yeshwantpur by 6:20."),
                        note = "Pack ID and charger",
                        isDone = false,
                    ),
                    ReminderRow(
                        chatId = 2,
                        messageId = 12,
                        chatTitle = "Pingogram Beta",
                        photo = null,
                        avatarShape = AvatarShape.Channel,
                        time = UiText.Plain("Mon, 10:00 AM"),
                        preview = UiText.Plain("Build 0.9.4 is live on the testing track"),
                        note = "",
                        isDone = false,
                    ),
                    ReminderRow(
                        chatId = 3,
                        messageId = 13,
                        chatTitle = "Meera Iyer",
                        photo = null,
                        avatarShape = AvatarShape.Circle,
                        time = UiText.Plain("Fri, 6:00 PM"),
                        preview = UiText.Plain("Can you send the Portra scans by Friday?"),
                        note = "Upload to shared album",
                        isDone = false,
                    ),
                ),
            done =
                persistentListOf(
                    ReminderRow(
                        chatId = 4,
                        messageId = 14,
                        chatTitle = "Rahul Shetty",
                        photo = null,
                        avatarShape = AvatarShape.Circle,
                        time = UiText.Plain("Thu"),
                        preview = UiText.Plain("Call the homestay about late check-in"),
                        note = "",
                        isDone = true,
                    ),
                ),
            sort = ReminderSort.ByTime,
        )

    /** One reminder whose time went by without being marked done. */
    val overdue =
        content.copy(
            upcoming =
                persistentListOf(
                    content.upcoming.first().copy(time = UiText.Plain("9:00 AM"), isOverdue = true),
                ),
            done = persistentListOf(),
        )
}

@PingogramPreviews
@Composable
private fun RemindersScreenPreview() {
    PingogramPreview { RemindersScreen(RemindersPreviewData.content, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun RemindersScreenEmptyPreview() {
    PingogramPreview { RemindersScreen(RemindersUiState.Empty, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun RemindersScreenLoadingPreview() {
    PingogramPreview { RemindersScreen(RemindersUiState.Loading, onEvent = {}, onBack = {}) }
}
