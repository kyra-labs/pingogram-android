package dev.kyralabs.pingogram.feature.chat.ui.reminders

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.UiText
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/** The reminder sheet's states for previews and screenshot tests: a new reminder, and one set already. */
internal object ReminderSheetPreviewData {
    val new =
        ReminderSheetUiState(
            messageId = 42,
            sender = UiText.Plain("Meera Iyer"),
            text = UiText.Plain("Can you check the price for 4 people? The good homestay fills up fast"),
            choices =
                persistentListOf(
                    ReminderChoice(ReminderOption.InOneHour, at = 1, UiText.Plain("Today, 10:41 AM")),
                    ReminderChoice(ReminderOption.ThisEvening, at = 2, UiText.Plain("Today, 8:00 PM")),
                    ReminderChoice(ReminderOption.TomorrowMorning, at = 3, UiText.Plain("Tomorrow, 9:00 AM")),
                    ReminderChoice(ReminderOption.NextWeek, at = 4, UiText.Plain("Monday, November 23, 9:00 AM")),
                    ReminderChoice(ReminderOption.Custom, at = null, whenText = null),
                ),
            selected = ReminderOption.TomorrowMorning,
            note = "",
            hasReminder = false,
        )

    val set =
        new.copy(
            choices =
                (
                    new.choices.dropLast(1) +
                        ReminderChoice(ReminderOption.Custom, 5, UiText.Plain("Sunday, November 29, 7:30 AM"))
                ).toImmutableList(),
            selected = ReminderOption.Custom,
            note = "Ask about breakfast",
            hasReminder = true,
        )
}

@PingogramPreviews
@Composable
private fun ReminderSheetPreview() {
    PingogramPreview { Surface { ReminderSheetContent(ReminderSheetPreviewData.new, onEvent = {}) } }
}

@PingogramPreviews
@Composable
private fun ReminderSheetSetPreview() {
    PingogramPreview { Surface { ReminderSheetContent(ReminderSheetPreviewData.set, onEvent = {}) } }
}
