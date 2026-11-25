package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.AppInfo
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.NOW
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerMode
import dev.kyralabs.pingogram.feature.chat.ui.thread.ThreadState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.util.Locale

/** [ChatStateMapper] on its own: what the conversation shows before its chat is known, and its kept formatter. */
class ChatStateMapperTest {
    private var locale = Locale.US

    private val mapper =
        ChatStateMapper(
            clock = FakeWallClock(NOW),
            timePatterns = FakeTimePatterns(),
            locale = { locale },
            appInfo = AppInfo(versionName = ""),
            inThread = false,
        )

    @Test
    fun `shows a chat that was not found as not found`() {
        val state = mapper.map(ChatLookup.Failed(DomainError.NotFound), unreadAfterId = null)

        assertEquals(ChatContent.NotFound, state.content)
    }

    @Test
    fun `shows another failure to find the chat as an error`() {
        val state = mapper.map(ChatLookup.Failed(DomainError.Network), unreadAfterId = null)

        assertEquals(ChatContent.Error(DomainError.Network), state.content)
    }

    @Test
    fun `loads while the chat is found but the unread divider is not known yet`() {
        val state = mapper.map(ChatLookup.Found(ChatFixtures.chat()), unreadAfterId = null)

        assertEquals(ChatContent.Loading, state.content)
    }

    @Test
    fun `keeps its time formatter until the language changes`() {
        val first = mapper.timeFormatter()

        assertSame(first, mapper.timeFormatter())
        locale = Locale.GERMANY
        assertNotSame(first, mapper.timeFormatter())
    }

    private fun ChatStateMapper.map(
        lookup: ChatLookup,
        unreadAfterId: Long?,
    ): ChatUiState =
        state(
            chatSources = ChatSources(lookup, ChatGroup(), emptyList(), unreadAfterId, ThreadState.WholeChat),
            messages = MessageSources(MessageState(ChatFixtures.history(), emptyMap(), emptySet()), WindowState()),
            people = ChatPeople(users = emptyMap(), myUserId = null, chats = emptyMap()),
            now = NOW,
            overlays =
                Overlays(
                    composerTargets = ComposerTargetState(ComposerMode.Writing, emptyMap(), elsewhere = null),
                    sheet = null,
                    deleteDialog = null,
                    preview = null,
                    pollOpen = false,
                    checklistOpen = false,
                ),
        )
}
