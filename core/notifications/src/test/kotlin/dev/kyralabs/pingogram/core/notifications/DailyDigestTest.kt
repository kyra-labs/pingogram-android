package dev.kyralabs.pingogram.core.notifications

import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatPermissions
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.MessageReminder
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** What the Daily digest counts and names (`REQUIREMENTS.md` FR-X23), across accounts, as Catch-up takes the chats. */
class DailyDigestTest {
    private val today = LocalDate.of(2026, 11, 26)

    private fun digest(
        vararg accounts: DigestAccount,
        includeMuted: Boolean = false,
        hidesContent: Boolean = false,
    ) = dailyDigestOf(accounts.toList(), today, ZoneOffset.UTC, includeMuted, hidesContent)

    private fun reminder(
        chatId: Long,
        at: String,
        isDone: Boolean = false,
    ) = MessageReminder(chatId = chatId, messageId = 1, at = Instant.parse(at), isDone = isDone)

    @Test
    fun `counts the chats catch-up takes, those that mention the user, and today's reminders not done`() {
        val digest =
            digest(
                DigestAccount(
                    chats =
                        listOf(
                            chat(1, unread = 2),
                            chat(2, unread = 1, mentions = 1),
                            chat(3),
                            chat(4, unread = 1, muted = true),
                        ),
                    reminders =
                        listOf(
                            reminder(1, "2026-11-26T09:00:00Z"),
                            reminder(1, "2026-11-26T18:00:00Z", isDone = true),
                            reminder(2, "2026-11-27T09:00:00Z"),
                        ),
                ),
            )

        assertEquals(DailyDigest(2, mentionChats = 1, reminders = 1, names = listOf("Chat 2", "Chat 1")), digest)
    }

    @Test
    fun `sums every account, names the mentions first, at most five, by their names on this phone`() {
        val first = DigestAccount(chats = (1L..4L).map { chat(it, unread = 1) }, localNames = mapOf(2L to "Ada"))
        val second = DigestAccount(chats = listOf(chat(10, unread = 1), chat(11, unread = 1, mentions = 1)))

        val digest = digest(first, second)

        assertEquals(6, digest.unreadChats)
        assertEquals(1, digest.mentionChats)
        assertEquals(listOf("Chat 11", "Chat 1", "Ada", "Chat 3", "Chat 4"), digest.names)
    }

    @Test
    fun `a contact's private chat keeps the contact name, not the name on this phone`() {
        val ada = chat(5, unread = 1).copy(type = ChatType.Private(userId = 50), title = "Ada Lovelace")
        val bob = chat(6, unread = 1).copy(type = ChatType.Private(userId = 60), title = "Bob")
        val account =
            DigestAccount(
                chats = listOf(ada, bob),
                localNames = mapOf(5L to "Ada from work", 6L to "Bob the plumber"),
                contacts = setOf(50L),
            )

        assertEquals(setOf("Ada Lovelace", "Bob the plumber"), digest(account).names.toSet())
    }

    @Test
    fun `locked chats and their reminders never count`() {
        val account =
            DigestAccount(
                chats = listOf(chat(1, unread = 1), chat(2, unread = 1, mentions = 1)),
                locked = setOf(2L),
                reminders = listOf(reminder(2, "2026-11-26T09:00:00Z")),
            )

        assertEquals(DailyDigest(1, mentionChats = 0, reminders = 0, names = listOf("Chat 1")), digest(account))
    }

    @Test
    fun `muted chats count only when catch-up includes them, and hidden content names nothing`() {
        val account = DigestAccount(chats = listOf(chat(1, unread = 1, muted = true)))

        assertTrue(digest(account).isEmpty)
        assertEquals(DailyDigest(1, 0, 0, emptyList()), digest(account, includeMuted = true, hidesContent = true))
    }

    private fun chat(
        id: Long,
        unread: Int = 0,
        mentions: Int = 0,
        muted: Boolean = false,
    ) = Chat(
        id = id,
        type = ChatType.BasicGroup(basicGroupId = id),
        title = "Chat $id",
        photo = null,
        lastMessage = null,
        positions = persistentListOf(),
        unreadCount = unread,
        unreadMentionCount = mentions,
        unreadReactionCount = 0,
        lastReadInboxMessageId = 0,
        lastReadOutboxMessageId = 0,
        isMarkedAsUnread = false,
        isMuted = muted,
        draft = null,
        hasProtectedContent = false,
        messageAutoDeleteTime = 0,
        permissions = ChatPermissions.All,
    )
}
