package dev.kyralabs.pingogram.feature.settings.ui.cleanup

import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.repository.ChatOpenings
import java.time.Instant
import java.time.ZoneId

/**
 * Which chats Cleanup suggestions offer (`REQUIREMENTS.md` FR-X24, owner decisions 2026-11-25): a chat of the main list
 * not opened on this phone since [cutoff] (or not since [openings] began, before it), unless it is pinned, locked, a
 * secret chat, Saved Messages ([me]'s own chat), Telegram's service notifications or another support account, or a
 * group or channel joined or created since [cutoff].
 */
internal class CleanupRule(
    private val me: Long?,
    private val locked: Set<Long>,
    private val openings: ChatOpenings,
    private val cutoff: Instant,
) {
    /** Whether [chat], whose private chat is with [user] if any, is suggested. */
    fun suggests(
        chat: Chat,
        user: User?,
    ): Boolean = !openings.lastOpenedOf(chat.id).isAfter(cutoff) && !isKept(chat, user)

    /** Whether a chat the user [joined] then is still suggested: not when that is after [cutoff]. */
    fun suggestsJoined(joined: Instant?): Boolean = joined?.isAfter(cutoff) != true

    private fun isKept(
        chat: Chat,
        user: User?,
    ): Boolean =
        chat.id in locked ||
            chat.positions.any { it.list == ChatListType.Main && it.isPinned } ||
            chat.type is ChatType.Secret ||
            (chat.type as? ChatType.Private)?.userId?.let { it == me || it == SERVICE_NOTIFICATIONS } == true ||
            user?.isSupport == true

    companion object {
        /** Telegram's service notifications account (TDLib's `UserManager::get_service_notifications_user_id`). */
        const val SERVICE_NOTIFICATIONS = 777_000L

        /** [months] before [now] in [zone]: a chat not opened since is suggested. */
        fun cutoffOf(
            now: Instant,
            zone: ZoneId,
            months: Int,
        ): Instant =
            now
                .atZone(zone)
                .minusMonths(months.toLong())
                .toInstant()
    }
}
