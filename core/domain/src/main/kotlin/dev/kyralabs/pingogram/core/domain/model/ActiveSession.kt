package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.ImmutableList

/**
 * The sessions of the account (`SessionRepository`): every app and device logged in to it.
 *
 * @property sessions in TDLib's order: the current session first, then sessions waiting for their 2-step verification
 *   password, unconfirmed ones, and the others by when they were last active, latest first.
 * @property inactiveSessionTtlDays after how many days without activity Telegram terminates a session by itself.
 */
data class ActiveSessions(
    val sessions: ImmutableList<ActiveSession>,
    val inactiveSessionTtlDays: Int,
)

/**
 * One session: an app logged in to the account on a device, as that app describes itself. Times are Unix timestamps
 * in seconds.
 *
 * `toString()` leaves out what describes the device and where it is, so a session can be logged.
 *
 * @property isCurrent the session of this app on this device.
 * @property isPasswordPending logged in, but the 2-step verification password has not been entered yet.
 * @property isUnconfirmed a new login that no other session has confirmed yet.
 * @property isOfficialApplication an official Telegram app, or one that uses the API ID of one.
 * @property location where the session was created, from its IP address ("Berlin, Germany"); may be empty.
 */
data class ActiveSession(
    val id: Long,
    val isCurrent: Boolean,
    val isPasswordPending: Boolean,
    val isUnconfirmed: Boolean,
    val device: SessionDevice,
    val applicationName: String,
    val applicationVersion: String,
    val isOfficialApplication: Boolean,
    val deviceModel: String,
    val platform: String,
    val systemVersion: String,
    val logInDate: Long,
    val lastActiveDate: Long,
    val location: String,
) {
    override fun toString() =
        "ActiveSession(id=$id, isCurrent=$isCurrent, isPasswordPending=$isPasswordPending, " +
            "isUnconfirmed=$isUnconfirmed, device=$device, …)"
}

/** What kind of device a session runs on, as far as TDLib can tell from its system and app. */
enum class SessionDevice {
    Phone,
    Tablet,
    Desktop,
    Browser,
    GameConsole,
    Unknown,
}
