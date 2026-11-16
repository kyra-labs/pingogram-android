package dev.kyralabs.pingogram.feature.chat.ui.reminders

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.UiText
import kotlinx.collections.immutable.persistentListOf

/** The reminder sheet's states for previews and screenshot tests: board 29b's late evening, and a reminder set. */
internal object ReminderSheetPreviewData {
    val new =
        ReminderSheetUiState(
            messageId = 1,
            sender = UiText.Plain("Aisha"),
            text = UiText.Plain("Train leaves 6:40 sharp. Be at Yeshwantpur by 6:20 and bring your ID."),
            options =
                persistentListOf(
                    ReminderOption(ReminderChoice.InOneHour, UiText.Plain("11:30 PM")),
                    ReminderOption(ReminderChoice.TomorrowMorning, UiText.Plain("Sun, 9:00 AM")),
                    ReminderOption(ReminderChoice.NextWeek, UiText.Plain("Mon, 9:00 AM")),
                    ReminderOption(ReminderChoice.Custom, null),
                ),
            chosen = ReminderChoice.TomorrowMorning,
            note = "",
            isExisting = false,
        )

    /** Before 20:00, "This evening" is offered too. */
    val afternoon =
        new.copy(
            options =
                persistentListOf(
                    ReminderOption(ReminderChoice.InOneHour, UiText.Plain("4:30 PM")),
                    ReminderOption(ReminderChoice.ThisEvening, UiText.Plain("8:00 PM")),
                    ReminderOption(ReminderChoice.TomorrowMorning, UiText.Plain("Sun, 9:00 AM")),
                    ReminderOption(ReminderChoice.NextWeek, UiText.Plain("Mon, 9:00 AM")),
                    ReminderOption(ReminderChoice.Custom, null),
                ),
            chosen = ReminderChoice.ThisEvening,
        )

    /** A message with a reminder: picked for Friday, with a note; "Delete reminder" in place of "Cancel". */
    val existing =
        new.copy(
            options =
                persistentListOf(
                    ReminderOption(ReminderChoice.InOneHour, UiText.Plain("11:30 PM")),
                    ReminderOption(ReminderChoice.TomorrowMorning, UiText.Plain("Sun, 9:00 AM")),
                    ReminderOption(ReminderChoice.NextWeek, UiText.Plain("Mon, 9:00 AM")),
                    ReminderOption(ReminderChoice.Custom, UiText.Plain("Fri, 6:00 PM")),
                ),
            chosen = ReminderChoice.Custom,
            note = "Pack ID and charger",
            isExisting = true,
        )
}

@PingogramPreviews
@Composable
private fun ReminderSheetPreview() {
    PingogramPreview { Surface { ReminderSheetContent(ReminderSheetPreviewData.new, onEvent = {}) } }
}

@PingogramPreviews
@Composable
private fun ReminderSheetAfternoonPreview() {
    PingogramPreview { Surface { ReminderSheetContent(ReminderSheetPreviewData.afternoon, onEvent = {}) } }
}

@PingogramPreviews
@Composable
private fun ReminderSheetExistingPreview() {
    PingogramPreview { Surface { ReminderSheetContent(ReminderSheetPreviewData.existing, onEvent = {}) } }
}
