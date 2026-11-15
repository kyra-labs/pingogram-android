package dev.kyralabs.pingogram.feature.chats.ui.catchup

import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.NOW
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.chat
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.message
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Which chats Catch-up takes, and in what order ([catchUpOrder]; owner decisions 2026-11-15). */
class CatchUpOrderTest {
    private fun unread(
        id: Long,
        at: Long,
        mentions: Int = 0,
        muted: Boolean = false,
        channel: Boolean = false,
    ) = chat(
        id = id,
        type = if (channel) ChatType.Supergroup(supergroupId = id, isChannel = true) else ChatType.Private(id),
        unreadCount = 2,
        unreadMentionCount = mentions,
        isMuted = muted,
        lastMessage = message(chatId = id, id = at, date = NOW.plusSeconds(at)),
    )

    @Test
    fun `mentions come first, then the most recent`() {
        val chats = listOf(unread(1, at = 5), unread(2, at = 50), unread(3, at = 1, mentions = 2), unread(4, at = 20))

        assertEquals(
            listOf(3L, 2L, 4L, 1L),
            catchUpOrder(chats, includeMuted = false, locked = emptySet()).map { it.id },
        )
    }

    @Test
    fun `muted chats and channels are left out unless included, and locked or read chats always`() {
        val chats =
            listOf(
                unread(1, at = 1),
                unread(2, at = 2, muted = true),
                unread(3, at = 3, channel = true),
                unread(4, at = 4),
                chat(id = 5),
            )

        assertEquals(listOf(1L), catchUpOrder(chats, includeMuted = false, locked = setOf(4L)).map { it.id })
        assertEquals(listOf(3L, 2L, 1L), catchUpOrder(chats, includeMuted = true, locked = setOf(4L)).map { it.id })
    }
}
