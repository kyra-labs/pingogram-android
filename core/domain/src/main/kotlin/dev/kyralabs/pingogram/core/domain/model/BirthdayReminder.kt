package dev.kyralabs.pingogram.core.domain.model

/**
 * A reminder the user set for user [userId]'s birthday, ringing each year on [day] of [month] (1–12), the day and
 * month their birthday had when it was set.
 */
data class BirthdayReminder(
    val userId: Long,
    val day: Int,
    val month: Int,
)
