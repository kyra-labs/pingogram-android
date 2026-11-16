package dev.kyralabs.pingogram.widget

import android.app.Application
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatPermissions
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.model.UserStatus
import dev.kyralabs.pingogram.core.domain.model.UserType
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The Conversation widget's messages and title ([widgetMessages], [widgetTitle]), as board 33 shows them. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ConversationContentTest {
    private val resources = RuntimeEnvironment.getApplication().resources
    private val users = listOf(user(ME, "Me"), user(MEERA, "Meera"), user(RAHUL, "Rahul")).associateBy { it.id }

    @Test
    fun `a group's newest messages come oldest first, each with its sender or You`() {
        val group = chat(GROUP, ChatType.Supergroup(supergroupId = 7, isChannel = false))
        val history =
            listOf(
                message(GROUP, 4, RAHUL, "Fourth"),
                message(GROUP, 3, ME, "Third", isOutgoing = true),
                message(GROUP, 2, MEERA, "Second"),
                message(GROUP, 1, MEERA, "First"),
            )

        val shown = widgetMessages(group, history, users, ME, resources)

        assertEquals(
            listOf(
                WidgetMessage(2, "Meera", "Second"),
                WidgetMessage(3, "You", "Third"),
                WidgetMessage(4, "Rahul", "Fourth"),
            ),
            shown,
        )
    }

    @Test
    fun `a channel's posts name no sender`() {
        val channel = chat(CHANNEL, ChatType.Supergroup(supergroupId = 8, isChannel = true), title = "News")
        val post = message(CHANNEL, 1, CHANNEL, "Out now", sender = MessageSender.Chat(CHANNEL), isChannelPost = true)

        val shown = widgetMessages(channel, listOf(post), users, ME, resources)

        assertEquals(listOf(WidgetMessage(1, null, "Out now")), shown)
    }

    @Test
    fun `a secret chat's messages stay off the home screen`() {
        val secret = chat(SECRET, ChatType.Secret(secretChatId = 3, userId = MEERA))

        assertEquals(
            emptyList<WidgetMessage>(),
            widgetMessages(secret, listOf(message(SECRET, 1, MEERA, "Psst")), users, ME, resources),
        )
    }

    @Test
    fun `the user's own chat is Saved Messages and a deleted account says so`() {
        val deleted = user(GONE, "", type = UserType.Deleted)

        assertEquals("Saved Messages", widgetTitle(chat(ME, ChatType.Private(ME)), users, ME, resources))
        assertEquals(
            "Deleted Account",
            widgetTitle(chat(GONE, ChatType.Private(GONE)), users + (GONE to deleted), ME, resources),
        )
        assertEquals("Meera", widgetTitle(chat(MEERA, ChatType.Private(MEERA), "Meera"), users, ME, resources))
    }

    private fun chat(
        id: Long,
        type: ChatType,
        title: String = "Trip",
    ) = Chat(
        id = id,
        type = type,
        title = title,
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

    @Suppress("LongParameterList") // A message's fields that the tests set.
    private fun message(
        chatId: Long,
        id: Long,
        senderId: Long,
        text: String,
        isOutgoing: Boolean = false,
        sender: MessageSender = MessageSender.User(senderId),
        isChannelPost: Boolean = false,
    ) = Message(
        id = id,
        chatId = chatId,
        sender = sender,
        date = 0,
        editDate = 0,
        isOutgoing = isOutgoing,
        sendingState = null,
        isPinned = false,
        isChannelPost = isChannelPost,
        containsUnreadMention = false,
        mediaAlbumId = 0,
        authorSignature = "",
        viaBotUserId = 0,
        replyTo = null,
        forwardInfo = null,
        interactionInfo = null,
        selfDestruct = null,
        content = MessageContent.Text(FormattedText(text, persistentListOf()), linkPreview = null),
    )

    private fun user(
        id: Long,
        firstName: String,
        type: UserType = UserType.Regular,
    ) = User(
        id = id,
        firstName = firstName,
        lastName = "",
        usernames = persistentListOf(),
        phoneNumber = "",
        status = UserStatus.Empty,
        profilePhoto = null,
        isVerified = false,
        isScam = false,
        isFake = false,
        isPremium = false,
        isContact = false,
        isMutualContact = false,
        isSupport = false,
        type = type,
        haveAccess = true,
    )

    private companion object {
        const val ME = 1L
        const val MEERA = 2L
        const val RAHUL = 3L
        const val GONE = 4L
        const val GROUP = -100L
        const val CHANNEL = -200L
        const val SECRET = -300L
    }
}
