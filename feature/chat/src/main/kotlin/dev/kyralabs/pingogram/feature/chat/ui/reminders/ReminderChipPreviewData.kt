package dev.kyralabs.pingogram.feature.chat.ui.reminders

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.ui.ChatScreen
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf

/**
 * Reminder chips for the previews and goldens of [ChatPreviewData.privateChat]: on Ada's last message and on one of
 * the user's.
 */
internal object ReminderChipPreviewData {
    val chips: ImmutableMap<Long, UiText> =
        persistentMapOf(
            11L to UiText.Plain("Sun, 9:00 AM"),
            9L to UiText.Plain("6:20 PM"),
        )
}

/** [content] with [ReminderChipPreviewData.chips] provided, as the conversation's route provides its own. */
@Composable
internal fun WithReminderChips(content: @Composable () -> Unit) {
    val labels = remember { mutableStateOf(ReminderChipPreviewData.chips) }
    val chips = remember { ReminderChips(labels, open = {}) }
    CompositionLocalProvider(LocalReminderChips provides chips, content = content)
}

@PingogramPreviews
@Composable
private fun ReminderChipsPreview() {
    PingogramPreview {
        WithReminderChips { ChatScreen(state = ChatPreviewData.privateChat, onEvent = {}, onBack = {}) }
    }
}
