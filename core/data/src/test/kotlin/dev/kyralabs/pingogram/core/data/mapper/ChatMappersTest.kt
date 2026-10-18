package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatDraft
import dev.kyralabs.pingogram.core.domain.model.ChatFolder
import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.model.ChatPermissions
import dev.kyralabs.pingogram.core.domain.model.ChatPosition
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.DraftContent
import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.Minithumbnail
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import dev.kyralabs.pingogram.core.domain.model.UnreadChatCount
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.chat
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.chatNotificationSettings
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.chatPosition
import kotlinx.collections.immutable.persistentListOf
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ChatMappersTest {
    private val noMutedScopes = emptySet<NotificationScope>()

    private val groupChat = TdApi.ChatTypeBasicGroup(5)

    @Test
    fun `a chat maps its id, title, counters, read state and flags`() {
        val tdChat =
            chat(
                id = 100,
                type = TdApi.ChatTypePrivate(42),
                title = "Ada",
                unreadCount = 3,
                unreadMentionCount = 2,
                unreadReactionCount = 1,
                lastReadInboxMessageId = 10,
                lastReadOutboxMessageId = 9,
                isMarkedAsUnread = true,
                hasProtectedContent = true,
                messageAutoDeleteTime = 86_400,
            )

        assertEquals(
            Chat(
                id = 100,
                type = ChatType.Private(userId = 42),
                title = "Ada",
                photo = null,
                lastMessage = null,
                positions = persistentListOf(ChatPosition(ChatListType.Main, order = 1_000, isPinned = false)),
                unreadCount = 3,
                unreadMentionCount = 2,
                unreadReactionCount = 1,
                lastReadInboxMessageId = 10,
                lastReadOutboxMessageId = 9,
                isMarkedAsUnread = true,
                isMuted = false,
                draft = null,
                hasProtectedContent = true,
                messageAutoDeleteTime = 86_400,
                permissions = ChatPermissions.All,
            ),
            tdChat.toChat(noMutedScopes),
        )
    }

    @Test
    fun `maps every chat type`() {
        val types =
            listOf(
                TdApi.ChatTypePrivate(42),
                TdApi.ChatTypeSecret(7, 42),
                TdApi.ChatTypeBasicGroup(5),
                TdApi.ChatTypeSupergroup(6, false),
                TdApi.ChatTypeSupergroup(8, true),
            ).map { chat(type = it).toChat(noMutedScopes)?.type }

        assertEquals(
            listOf(
                ChatType.Private(userId = 42),
                ChatType.Secret(secretChatId = 7, userId = 42),
                ChatType.BasicGroup(basicGroupId = 5),
                ChatType.Supergroup(supergroupId = 6, isChannel = false),
                ChatType.Supergroup(supergroupId = 8, isChannel = true),
            ),
            types,
        )
    }

    @Test
    fun `a chat of a type added by a newer TDLib is left out`() {
        val newerType =
            object : TdApi.ChatType() {
                override fun getConstructor() = 0
            }

        assertNull(chat(type = newerType).toChat(noMutedScopes))
    }

    @Test
    fun `maps the photo's files and minithumbnail`() {
        val photo =
            TdApiFixtures.chatPhotoInfo(
                smallFileId = 12,
                bigFileId = 13,
                minithumbnail = TdApiFixtures.minithumbnail(data = byteArrayOf(4, 2)),
            )

        assertEquals(
            ProfilePhoto(FileRef(12, 1_024), FileRef(13, 1_024), Minithumbnail(40, 30, byteArrayOf(4, 2))),
            chat(photo = photo).toChat(noMutedScopes)?.photo,
        )
    }

    @Test
    fun `maps the last message like any message`() {
        val message = TdApiFixtures.message(id = 5, chatId = 100)

        assertEquals(message.toMessage(), chat(lastMessage = message).toChat(noMutedScopes)?.lastMessage)
    }

    @Test
    fun `maps the positions in every kind of list`() {
        val positions =
            arrayOf(
                chatPosition(TdApi.ChatListMain(), order = 30, isPinned = true),
                chatPosition(TdApi.ChatListArchive(), order = 20),
                chatPosition(TdApi.ChatListFolder(3), order = 10),
            )

        assertEquals(
            listOf(
                ChatPosition(ChatListType.Main, order = 30, isPinned = true),
                ChatPosition(ChatListType.Archive, order = 20, isPinned = false),
                ChatPosition(ChatListType.Folder(3), order = 10, isPinned = false),
            ),
            chat(positions = positions).toChat(noMutedScopes)?.positions,
        )
    }

    @Test
    fun `chat lists map to TDLib's and back`() {
        val lists = listOf(ChatListType.Main, ChatListType.Archive, ChatListType.Folder(3))

        assertEquals(lists, lists.map { it.toTdChatList().toChatListType() })
    }

    @Test
    fun `a chat's own mute setting mutes it`() {
        val settings = chatNotificationSettings(useDefaultMuteFor = false, muteFor = 3_600)

        assertEquals(true, chat(notificationSettings = settings).toChat(noMutedScopes)?.isMuted)
    }

    @Test
    fun `a chat's own unmute setting overrides a muted default`() {
        val settings = chatNotificationSettings(useDefaultMuteFor = false, muteFor = 0)
        val tdChat = chat(type = groupChat, notificationSettings = settings)

        assertEquals(false, tdChat.toChat(setOf(NotificationScope.GroupChats))?.isMuted)
    }

    @Test
    fun `a chat that follows the default is muted when its scope is`() {
        val tdChat = chat(type = groupChat, notificationSettings = chatNotificationSettings(useDefaultMuteFor = true))

        assertEquals(
            listOf(true, false),
            listOf(setOf(NotificationScope.GroupChats), noMutedScopes).map { tdChat.toChat(it)?.isMuted },
        )
    }

    @Test
    fun `private and secret chats follow the private scope, groups the group scope, channels the channel scope`() {
        val scopes =
            listOf(
                TdApi.ChatTypePrivate(42),
                TdApi.ChatTypeSecret(7, 42),
                TdApi.ChatTypeBasicGroup(5),
                TdApi.ChatTypeSupergroup(6, false),
                TdApi.ChatTypeSupergroup(8, true),
            ).map { type ->
                NotificationScope.entries.single { scope -> chat(type = type).toChat(setOf(scope))?.isMuted == true }
            }

        assertEquals(
            listOf(
                NotificationScope.PrivateChats,
                NotificationScope.PrivateChats,
                NotificationScope.GroupChats,
                NotificationScope.GroupChats,
                NotificationScope.ChannelChats,
            ),
            scopes,
        )
    }

    @Test
    fun `maps a text draft with its date`() {
        val draft = TdApiFixtures.draftMessage(TdApiFixtures.draftText("Later"), date = 1_700_000_100)

        assertEquals(
            ChatDraft(DraftContent.Text(FormattedText("Later", persistentListOf())), date = 1_700_000_100),
            chat(draftMessage = draft).toChat(noMutedScopes)?.draft,
        )
    }

    @Test
    fun `maps voice note, video note and rich message drafts`() {
        val contents =
            listOf(
                TdApi.DraftMessageContentVoiceNote("voice.ogg", 3, byteArrayOf(), null),
                TdApi.DraftMessageContentVideoNote("video.mp4", 3, 384, null),
                TdApi.DraftMessageContentRichMessage(TdApi.RichMessage(emptyArray(), false, true)),
            ).map { chat(draftMessage = TdApiFixtures.draftMessage(it)).toChat(noMutedScopes)?.draft?.content }

        assertEquals(listOf(DraftContent.VoiceNote, DraftContent.VideoNote, DraftContent.Unsupported), contents)
    }

    @Test
    fun `maps a chat folder with its custom emoji title`() {
        val folder =
            TdApiFixtures.chatFolderInfo(id = 3, iconName = "Custom", colorId = 2).apply {
                name.text =
                    TdApi.FormattedText("⭐ Work", arrayOf(TdApi.TextEntity(0, 1, TdApi.TextEntityTypeCustomEmoji(9))))
            }

        assertEquals(
            ChatFolder(
                id = 3,
                title = FormattedText("⭐ Work", persistentListOf(TextEntity(0, 1, TextEntityType.CustomEmoji(9)))),
                iconName = "Custom",
                colorId = 2,
            ),
            folder.toChatFolder(),
        )
    }

    @Test
    fun `a chat where text may not be sent maps to permissions that forbid it`() {
        val chat = chat(permissions = TdApi.ChatPermissions())

        assertEquals(ChatPermissions.None, chat.toChat(noMutedScopes)?.permissions)
    }

    @Test
    fun `a chat folder without a color has none`() {
        assertNull(TdApiFixtures.chatFolderInfo(colorId = -1).toChatFolder().colorId)
    }

    @Test
    fun `maps every unread chat counter`() {
        val update =
            TdApiFixtures.updateUnreadChatCount(
                totalCount = 50,
                unreadCount = 5,
                unreadUnmutedCount = 4,
                markedAsUnreadCount = 2,
                markedAsUnreadUnmutedCount = 1,
            )

        assertEquals(UnreadChatCount(50, 5, 4, 2, 1), update.toUnreadChatCount())
    }
}
