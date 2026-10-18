package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WriteAccessTest {
    private fun chat(
        type: ChatType,
        permissions: ChatPermissions = ChatPermissions.All,
    ) = Chat(
        id = 100,
        type = type,
        title = "Chat",
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
        permissions = permissions,
    )

    private fun user(type: UserType = UserType.Regular) =
        User(
            id = 42,
            firstName = "Ada",
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

    private fun basicGroup(
        status: ChatMemberStatus = ChatMemberStatus.Member,
        isActive: Boolean = true,
    ) = BasicGroup(id = 5, memberCount = 3, status = status, isActive = isActive, upgradedToSupergroupId = null)

    private fun supergroup(
        status: ChatMemberStatus = ChatMemberStatus.Member,
        isChannel: Boolean = false,
    ) = Supergroup(
        id = 6,
        usernames = persistentListOf(),
        memberCount = 0,
        status = status,
        isChannel = isChannel,
        isForum = false,
        isVerified = false,
        isScam = false,
        isFake = false,
    )

    private val private = ChatType.Private(userId = 42)
    private val basic = ChatType.BasicGroup(basicGroupId = 5)
    private val group = ChatType.Supergroup(supergroupId = 6, isChannel = false)
    private val channel = ChatType.Supergroup(supergroupId = 6, isChannel = true)

    private fun Chat.access(
        user: User? = null,
        basicGroup: BasicGroup? = null,
        supergroup: Supergroup? = null,
    ) = writeAccess(user, basicGroup, supergroup)

    @Test
    fun `a private chat allows writing, also before its user is known`() {
        assertEquals(WriteAccess.Allowed, chat(private).access(user()))
        assertEquals(WriteAccess.Allowed, chat(private).access())
    }

    @Test
    fun `a deleted account cannot be written to`() {
        assertEquals(WriteAccess.Restricted, chat(private).access(user(UserType.Deleted)))
    }

    @Test
    fun `a service chat TDLib restricts is read-only`() {
        assertEquals(WriteAccess.Restricted, chat(private, ChatPermissions.None).access(user(UserType.Bot)))
    }

    @Test
    fun `a secret chat follows the same rules as a private one`() {
        val secret = ChatType.Secret(secretChatId = 7, userId = 42)

        assertEquals(WriteAccess.Allowed, chat(secret).access(user()))
        assertEquals(WriteAccess.Restricted, chat(secret).access(user(UserType.Deleted)))
    }

    @Test
    fun `members write in groups as the chat's permissions allow`() {
        assertEquals(WriteAccess.Allowed, chat(basic).access(basicGroup = basicGroup()))
        assertEquals(WriteAccess.Allowed, chat(group).access(supergroup = supergroup()))
        assertEquals(
            WriteAccess.Restricted,
            chat(group, ChatPermissions.None).access(supergroup = supergroup()),
        )
    }

    @Test
    fun `owners and administrators write in groups whatever the permissions`() {
        val closed = chat(group, ChatPermissions.None)

        assertEquals(WriteAccess.Allowed, closed.access(supergroup = supergroup(ChatMemberStatus.Creator(true))))
        assertEquals(
            WriteAccess.Allowed,
            closed.access(supergroup = supergroup(ChatMemberStatus.Administrator(canPostMessages = false))),
        )
    }

    @Test
    fun `a restricted member writes only if both the restriction and the permissions allow it`() {
        val mayWrite = ChatMemberStatus.Restricted(isMember = true, canSendBasicMessages = true)
        val mayNot = ChatMemberStatus.Restricted(isMember = true, canSendBasicMessages = false)

        assertEquals(WriteAccess.Allowed, chat(group).access(supergroup = supergroup(mayWrite)))
        assertEquals(WriteAccess.Restricted, chat(group).access(supergroup = supergroup(mayNot)))
        assertEquals(
            WriteAccess.Restricted,
            chat(group, ChatPermissions.None).access(supergroup = supergroup(mayWrite)),
        )
    }

    @Test
    fun `someone who left or was removed from a group is not a member`() {
        val statuses =
            listOf(
                ChatMemberStatus.Left,
                ChatMemberStatus.Banned,
                ChatMemberStatus.Creator(isMember = false),
                ChatMemberStatus.Restricted(isMember = false, canSendBasicMessages = true),
            )

        assertEquals(
            List(statuses.size) { WriteAccess.NotMember },
            statuses.map { chat(group).access(supergroup = supergroup(it)) },
        )
        assertEquals(WriteAccess.NotMember, chat(basic).access(basicGroup = basicGroup(ChatMemberStatus.Left)))
    }

    @Test
    fun `a deactivated basic group allows no messages`() {
        assertEquals(WriteAccess.Restricted, chat(basic).access(basicGroup = basicGroup(isActive = false)))
    }

    @Test
    fun `only the owner and administrators who may post write in a channel`() {
        fun access(status: ChatMemberStatus) = chat(channel).access(supergroup = supergroup(status, isChannel = true))

        assertEquals(WriteAccess.Allowed, access(ChatMemberStatus.Creator(isMember = true)))
        assertEquals(WriteAccess.Allowed, access(ChatMemberStatus.Administrator(canPostMessages = true)))
        assertEquals(WriteAccess.ReadOnly, access(ChatMemberStatus.Administrator(canPostMessages = false)))
        assertEquals(WriteAccess.ReadOnly, access(ChatMemberStatus.Member))
        assertEquals(WriteAccess.ReadOnly, access(ChatMemberStatus.Left))
        assertEquals(WriteAccess.ReadOnly, access(ChatMemberStatus.Creator(isMember = false)))
    }

    @Test
    fun `a group or channel TDLib has not announced is unknown`() {
        assertEquals(WriteAccess.Unknown, chat(basic).access())
        assertEquals(WriteAccess.Unknown, chat(channel).access())
    }
}
