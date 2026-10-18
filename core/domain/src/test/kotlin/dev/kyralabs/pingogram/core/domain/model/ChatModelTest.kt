package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ChatModelTest {
    private val archived = ChatPosition(ChatListType.Archive, order = 20, isPinned = false)
    private val inFolder = ChatPosition(ChatListType.Folder(3), order = 10, isPinned = true)

    private fun chat(positions: List<ChatPosition> = emptyList()) =
        Chat(
            id = 100,
            type = ChatType.Private(userId = 42),
            title = "Ada",
            photo = null,
            lastMessage = null,
            positions = positions.toImmutableList(),
            unreadCount = 2,
            unreadMentionCount = 0,
            unreadReactionCount = 0,
            lastReadInboxMessageId = 0,
            lastReadOutboxMessageId = 0,
            isMarkedAsUnread = false,
            isMuted = true,
            draft = ChatDraft(DraftContent.Text(FormattedText("secret plans", persistentListOf())), date = 0),
            hasProtectedContent = false,
            messageAutoDeleteTime = 0,
            permissions = ChatPermissions.All,
        )

    @Test
    fun `positionIn finds the chat's position in a list`() {
        val chat = chat(positions = listOf(archived, inFolder))

        assertEquals(inFolder, chat.positionIn(ChatListType.Folder(3)))
    }

    @Test
    fun `positionIn is null for a list the chat is not in`() {
        assertNull(chat(positions = listOf(archived)).positionIn(ChatListType.Main))
    }

    @Test
    fun `toString leaves the title, the last message and the draft out`() {
        assertEquals(
            "Chat(id=100, type=Private(userId=42), unreadCount=2, isMuted=true, …)",
            chat().toString(),
        )
    }

    @Test
    fun `toString of a folder leaves its title out`() {
        val folder = ChatFolder(3, FormattedText("Family", persistentListOf()), iconName = "Home", colorId = null)

        assertEquals("ChatFolder(id=3, iconName=Home, colorId=null, …)", folder.toString())
    }
}
