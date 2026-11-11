package dev.kyralabs.pingogram.core.domain.model

/**
 * How a message is sent (`REQUIREMENTS.md` FR-C5): [silent] delivers it without a notification sound; [scheduledAt]
 * (Unix time, seconds) holds it on Telegram's servers until then, outside the chat's history; null sends it now.
 * [repeatPeriod] sends a scheduled message again every so many seconds after that, one of [REPEAT_PERIODS] (td_api.tl
 * `messageSchedulingStateSendAtDate.repeat_period`, which Telegram takes from Telegram Premium subscribers and for one
 * message at a time); 0 sends it once. [topic] is the forum topic or message thread it goes into (FR-C9); null sends it
 * to the chat itself.
 */
data class SendOptions(
    val silent: Boolean = false,
    val scheduledAt: Long? = null,
    val topic: MessageTopic? = null,
    val repeatPeriod: Int = 0,
) {
    companion object {
        private const val DAY = 86_400
        private const val WEEK = 7 * DAY
        private const val TWO_WEEKS = 14 * DAY
        private const val MONTH = 30 * DAY
        private const val THREE_MONTHS = 91 * DAY
        private const val SIX_MONTHS = 182 * DAY
        private const val YEAR = 365 * DAY

        /** The periods a scheduled message repeats at, in seconds, as Telegram's apps offer them: a day to a year. */
        val REPEAT_PERIODS: List<Int> = listOf(DAY, WEEK, TWO_WEEKS, MONTH, THREE_MONTHS, SIX_MONTHS, YEAR)
    }
}
