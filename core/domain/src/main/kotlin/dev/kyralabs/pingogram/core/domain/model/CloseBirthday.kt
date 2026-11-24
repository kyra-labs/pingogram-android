package dev.kyralabs.pingogram.core.domain.model

/** A contact, user [userId], whose [birthdate] is close: Telegram names the contacts with a birthday these days. */
data class CloseBirthday(
    val userId: Long,
    val birthdate: Birthdate,
)
