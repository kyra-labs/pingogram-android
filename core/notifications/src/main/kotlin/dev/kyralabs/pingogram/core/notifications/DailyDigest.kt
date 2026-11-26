package dev.kyralabs.pingogram.core.notifications

import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.MessageReminder
import dev.kyralabs.pingogram.core.domain.model.catchUpOrder
import java.time.LocalDate
import java.time.ZoneId

/**
 * What the Daily digest says (`REQUIREMENTS.md` FR-X23), across every signed-in account: the chats Catch-up would go
 * through ([unreadChats]), those of them that mention the user ([mentionChats]), the reminders due that day
 * ([reminders]), and up to [MAX_NAMES] of those chats' names in Catch-up's order ([names]; none while the content is
 * hidden). Locked chats never count.
 */
internal data class DailyDigest(
    val unreadChats: Int,
    val mentionChats: Int,
    val reminders: Int,
    val names: List<String>,
) {
    /** Nothing to say: no notification. */
    val isEmpty: Boolean get() = unreadChats == 0 && reminders == 0

    companion object {
        const val MAX_NAMES = 5
    }
}

/** One account's part of the digest: its main list's loaded chats, locked chats, reminders and names on this phone. */
internal class DigestAccount(
    val chats: List<Chat>,
    val locked: Set<Long> = emptySet(),
    val reminders: List<MessageReminder> = emptyList(),
    val localNames: Map<Long, String> = emptyMap(),
)

/**
 * The digest of [accounts] on [today] in [zone]: chats as Catch-up takes them (muted ones and channels only if
 * [includeMuted]), the reminders not done that ring [today], and no names at all while [hidesContent].
 */
internal fun dailyDigestOf(
    accounts: List<DigestAccount>,
    today: LocalDate,
    zone: ZoneId,
    includeMuted: Boolean,
    hidesContent: Boolean,
): DailyDigest {
    val unread =
        accounts.flatMap { account ->
            catchUpOrder(account.chats, includeMuted, account.locked).map { chat -> account to chat }
        }
    val shown =
        unread.sortedWith(
            compareByDescending<Pair<DigestAccount, Chat>> { it.second.unreadMentionCount > 0 }
                .thenByDescending { it.second.lastMessage?.date ?: 0L },
        )
    val reminders =
        accounts.sumOf { account ->
            account.reminders.count {
                !it.isDone && it.chatId !in account.locked && it.at.atZone(zone).toLocalDate() == today
            }
        }
    return DailyDigest(
        unreadChats = unread.size,
        mentionChats = unread.count { it.second.unreadMentionCount > 0 },
        reminders = reminders,
        names =
            if (hidesContent) {
                emptyList()
            } else {
                shown.take(DailyDigest.MAX_NAMES).map { (account, chat) -> account.localNames[chat.id] ?: chat.title }
            },
    )
}
