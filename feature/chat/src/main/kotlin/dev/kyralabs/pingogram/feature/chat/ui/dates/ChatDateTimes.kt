package dev.kyralabs.pingogram.feature.chat.ui.dates

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.feature.chat.ui.ChatEffect
import dev.kyralabs.pingogram.feature.chat.ui.DateTimeEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The sheet of a tapped date in a message: its moment [unixTime], worded in full in the reader's time zone ([text])
 * and that zone's name ([zoneName]).
 */
@Immutable
data class DateTimeSheet(
    val unixTime: Long,
    val text: String,
    val zoneName: String,
)

/**
 * The dates of the conversation's messages, for `ChatViewModel`: a tapped date opens its sheet ([sheet], kept across
 * process death by its moment), worded by [describe]; "Add to calendar" hands the moment to the calendar app
 * ([ChatEffect.AddToCalendar]), which asks before it saves anything and needs no permission; "Copy" puts the sheet's
 * words on the clipboard. Kept out of `ChatUiState`, like the conversation's other sheets.
 */
internal class ChatDateTimes(
    private val describe: (unixTime: Long) -> DateTimeSheet,
    private val savedState: SavedStateHandle,
    private val onEffect: (ChatEffect) -> Unit,
) {
    private val open = MutableStateFlow(savedState.get<Long>(KEY_OPEN)?.let(describe))

    /** The sheet of the tapped date; null while it is closed. */
    val sheet: StateFlow<DateTimeSheet?> = open.asStateFlow()

    fun onEvent(event: DateTimeEvent) {
        when (event) {
            is DateTimeEvent.Open -> {
                show(describe(event.unixTime))
            }

            DateTimeEvent.AddToCalendar -> {
                open.value
                    ?.let {
                        onEffect(
                            ChatEffect.AddToCalendar(it.unixTime),
                        )
                    }.also { close() }
            }

            DateTimeEvent.Copy -> {
                open.value?.let { onEffect(ChatEffect.CopyText(it.text)) }.also { close() }
            }

            DateTimeEvent.Dismiss -> {
                close()
            }
        }
    }

    private fun show(sheet: DateTimeSheet) {
        open.value = sheet
        savedState[KEY_OPEN] = sheet.unixTime
    }

    private fun close() {
        open.value = null
        savedState.remove<Long>(KEY_OPEN)
    }

    internal companion object {
        const val KEY_OPEN = "date_time_sheet"
    }
}
