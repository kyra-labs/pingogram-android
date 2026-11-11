package dev.kyralabs.pingogram.feature.chat.ui

/** The events of dates in messages ([dev.kyralabs.pingogram.core.domain.model.TextEntityType.DateTime]). */
sealed interface DateTimeEvent : ChatEvent {
    /** A date in a message was tapped: its sheet opens, with the moment [unixTime] in the reader's time zone. */
    data class Open(
        val unixTime: Long,
    ) : DateTimeEvent

    /** "Add to calendar": the calendar app opens a new event at the sheet's moment. */
    data object AddToCalendar : DateTimeEvent

    /** "Copy": the sheet's date goes on the clipboard, as the sheet words it. */
    data object Copy : DateTimeEvent

    data object Dismiss : DateTimeEvent
}
