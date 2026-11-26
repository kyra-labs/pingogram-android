package dev.kyralabs.pingogram.core.domain.keyword

import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatPermissions
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.MutedWordSettings
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Muted words outside the conversation (FR-X21): groups only, others' messages, never a mention of the user. */
class MutedWordsTest {
    private val on = MutedWords(MutedWordSettings(enabled = true, words = persistentListOf("spoiler")))

    private val group = chat(ChatType.BasicGroup(basicGroupId = 8))

    private fun chat(type: ChatType) =
        Chat(
            id = -100,
            type = type,
            title = "Group",
            photo = null,
            lastMessage = null,
            positions = persistentListOf(),
            unreadCount = 0,
            unreadMentionCount = 0,
            unreadReactionCount = 0,
            lastReadInboxMessageId = 0,
            lastReadOutboxMessageId = 0,
            isMarkedAsUnread = false,
            isMuted = false,
            draft = null,
            hasProtectedContent = false,
            messageAutoDeleteTime = 0,
            permissions = ChatPermissions.All,
        )

    private fun message(
        content: MessageContent = MessageContent.Text(text("Big spoiler ahead"), linkPreview = null),
        isOutgoing: Boolean = false,
        mentionsMe: Boolean = false,
    ) = Message(
        id = 1,
        chatId = -100,
        sender = MessageSender.User(2),
        date = 0,
        editDate = 0,
        isOutgoing = isOutgoing,
        sendingState = null,
        isPinned = false,
        isChannelPost = false,
        containsUnreadMention = mentionsMe,
        mediaAlbumId = 0,
        authorSignature = "",
        viaBotUserId = 0,
        replyTo = null,
        forwardInfo = null,
        interactionInfo = null,
        selfDestruct = null,
        content = content,
    )

    private fun text(value: String) = FormattedText(value, persistentListOf())

    @Test
    fun `a group's message with a muted word is muted`() {
        assertTrue(on.mutes(group, message()))
        assertTrue(on.mutes(chat(ChatType.Supergroup(supergroupId = 1, isChannel = false)), message()))
    }

    @Test
    fun `channels, private chats, the user's own messages and mentions of the user are not`() {
        assertFalse(on.mutes(chat(ChatType.Supergroup(supergroupId = 1, isChannel = true)), message()))
        assertFalse(on.mutes(chat(ChatType.Private(userId = 2)), message()))
        assertFalse(on.mutes(group, message(isOutgoing = true)))
        assertFalse(on.mutes(group, message(mentionsMe = true)))
    }

    @Test
    fun `nothing is muted while off, without words or without the word`() {
        assertFalse(MutedWords(MutedWordSettings(words = persistentListOf("spoiler"))).mutes(group, message()))
        assertFalse(MutedWords(MutedWordSettings(enabled = true)).mutes(group, message()))
        assertFalse(on.mutes(group, message(MessageContent.Text(text("Nothing to see"), linkPreview = null))))
    }
}
