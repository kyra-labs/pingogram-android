package dev.kyralabs.pingogram.feature.chats.ui

import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.ME
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.NOW
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.chat
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.message
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.user
import kotlinx.collections.immutable.persistentMapOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.time.ZoneOffset
import java.util.Locale

/** Rows made again only when what they show changed (`docs/PERFORMANCE.md` §4), muted words matched once a message. */
class ChatListItemMapperTest {
    private val formatter = ChatListTimeFormatter(ZoneOffset.UTC, Locale.US, is24HourClock = false, FakeTimePatterns())
    private val group =
        chat(
            id = GROUP,
            type = ChatType.BasicGroup(basicGroupId = 1),
            lastMessage = message(chatId = GROUP, sender = MessageSender.User(BOB)),
        )
    private var matched = 0
    private val mutes: MessageMutes = { _, _, _ ->
        matched++
        true
    }

    private fun sources(statusOf: Long) =
        RowSources(
            list = ChatListType.Main,
            snapshot =
                RowSnapshot(
                    users = persistentMapOf(ME to user(ME), BOB to user(BOB), statusOf to user(statusOf, "Changed")),
                    supergroups = persistentMapOf(),
                    secretChats = persistentMapOf(),
                    actionsByChat = persistentMapOf(),
                    myUserId = ME,
                    now = NOW,
                    mutes = mutes,
                ),
            timeFormatter = formatter,
        )

    @Test
    fun `a row kept as it was does not look for muted words again`() {
        val mapper = ChatListItemMapper()

        val first = mapper.map(listOf(group), sources(statusOf = OTHER))
        val second = mapper.map(listOf(group), sources(statusOf = OTHER + 1))

        assertSame(first.single(), second.single())
        assertEquals(1, matched)
    }

    @Test
    fun `a new last message is matched again`() {
        val mapper = ChatListItemMapper()
        mapper.map(listOf(group), sources(statusOf = OTHER))

        val newer = group.copy(lastMessage = message(chatId = GROUP, id = 11, sender = MessageSender.User(BOB)))
        mapper.map(listOf(newer), sources(statusOf = OTHER))

        assertEquals(2, matched)
    }

    private companion object {
        const val GROUP = -10L
        const val BOB = 3L
        const val OTHER = 40L
    }
}
