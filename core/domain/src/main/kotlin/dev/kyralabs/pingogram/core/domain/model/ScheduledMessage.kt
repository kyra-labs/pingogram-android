package dev.kyralabs.pingogram.core.domain.model

/**
 * A message the user scheduled (`REQUIREMENTS.md` FR-C5): [message] as the conversation would show it, held on
 * Telegram's servers until its [schedule] sends it, outside the chat's history. Its id is not one of the history's:
 * once sent, it comes back as a new message with an id of its own.
 */
data class ScheduledMessage(
    val message: Message,
    val schedule: MessageSchedule,
)

/** When a [ScheduledMessage] goes out (td_api.tl `MessageSchedulingState`). */
sealed interface MessageSchedule {
    /**
     * At [sendDate] (Unix time, seconds); then again every [repeatPeriod] seconds where it is more than 0 (a day, a
     * week…), as Telegram repeats it.
     */
    data class At(
        val sendDate: Long,
        val repeatPeriod: Int = 0,
    ) : MessageSchedule

    /** As soon as the other user of a private chat comes online. */
    data object WhenOnline : MessageSchedule

    /**
     * Once Telegram has processed its video, at [sendDate] at the earliest (a video sent with a quality to convert).
     */
    data class WhenVideoProcessed(
        val sendDate: Long,
    ) : MessageSchedule
}
