package dev.kyralabs.pingogram.feature.chat.ui.listen

import dev.kyralabs.pingogram.feature.chat.ui.MessageAction
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/** "Listen" in the message menu only while Listen to messages is on (FR-X26). */
class ListenMenuTest {
    private val sheet =
        ChatPreviewData.incomingMessageActions.copy(
            actions = persistentListOf(MessageAction.Reply, MessageAction.Copy, MessageAction.Listen),
        )
    private val state = ChatPreviewData.privateChat.copy(messageActions = sheet)

    @Test
    fun `the menu keeps listen while the setting is on`() = assertSame(state, state.withListen(enabled = true))

    @Test
    fun `the menu leaves listen out while the setting is off`() =
        assertEquals(
            listOf(MessageAction.Reply, MessageAction.Copy),
            state.withListen(enabled = false).messageActions?.actions,
        )

    @Test
    fun `without a menu nothing changes`() {
        val closed = state.copy(messageActions = null)

        assertSame(closed, closed.withListen(enabled = false))
    }
}
