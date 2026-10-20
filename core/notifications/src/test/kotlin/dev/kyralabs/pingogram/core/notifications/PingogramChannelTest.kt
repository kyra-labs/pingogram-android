package dev.kyralabs.pingogram.core.notifications

import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.NotificationGroupType
import dev.kyralabs.pingogram.core.domain.model.NotificationScope
import dev.kyralabs.pingogram.core.domain.model.notificationScope
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PingogramChannelTest {
    @Test
    fun `private and secret chats share the private channel`() {
        assertEquals(
            PingogramChannel.PrivateChats,
            PingogramChannel.of(NotificationGroupType.Messages, ChatType.Private(1)),
        )
        assertEquals(
            PingogramChannel.PrivateChats,
            PingogramChannel.of(NotificationGroupType.SecretChat, ChatType.Secret(secretChatId = 2, userId = 1)),
        )
    }

    @Test
    fun `groups and channels have their own channels, mentions too`() {
        assertEquals(
            PingogramChannel.Groups,
            PingogramChannel.of(NotificationGroupType.Messages, ChatType.BasicGroup(3)),
        )
        assertEquals(
            PingogramChannel.Groups,
            PingogramChannel.of(NotificationGroupType.Mentions, ChatType.Supergroup(4, isChannel = false)),
        )
        assertEquals(
            PingogramChannel.Channels,
            PingogramChannel.of(NotificationGroupType.Messages, ChatType.Supergroup(5, isChannel = true)),
        )
    }

    @Test
    fun `calls go to the calls channel whatever the chat, unknown chats to other`() {
        assertEquals(PingogramChannel.Calls, PingogramChannel.of(NotificationGroupType.Calls, ChatType.Private(1)))
        assertEquals(PingogramChannel.Other, PingogramChannel.of(NotificationGroupType.Messages, chatType = null))
    }

    @Test
    fun `channel ids are unique and never mention Telegram`() {
        val ids = PingogramChannel.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.none { it.contains("telegram", ignoreCase = true) })
    }

    @Test
    fun `a scope's channel is the one its chats' messages go to`() {
        val types =
            listOf(
                ChatType.Private(1),
                ChatType.Secret(secretChatId = 2, userId = 1),
                ChatType.BasicGroup(3),
                ChatType.Supergroup(4, isChannel = false),
                ChatType.Supergroup(5, isChannel = true),
            )

        for (type in types) {
            assertEquals(
                PingogramChannel.of(NotificationGroupType.Messages, type),
                PingogramChannel.of(type.notificationScope),
            )
        }
        assertEquals(
            NotificationScope.entries.size,
            NotificationScope.entries
                .map { PingogramChannel.of(it) }
                .toSet()
                .size,
        )
    }
}
