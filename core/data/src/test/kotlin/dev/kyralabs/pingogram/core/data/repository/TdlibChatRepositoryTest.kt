package dev.kyralabs.pingogram.core.data.repository

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.data.mapper.toChatDraft
import dev.kyralabs.pingogram.core.data.mapper.toMessage
import dev.kyralabs.pingogram.core.data.mapper.toProfilePhoto
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatFolder
import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.model.ChatPosition
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.UnreadChatCount
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.chat
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.chatFolderInfo
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.chatNotificationSettings
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.chatPosition
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateChatFolders
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateChatPosition
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateNewChat
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateScopeNotificationSettings
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateUnreadChatCount
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TdlibChatRepositoryTest {
    private val client = FakeTdlibClient()

    private fun TestScope.repository() =
        TdlibChatRepository(client, backgroundScope, StandardTestDispatcher(testScheduler))

    private suspend fun TdlibChatRepository.cached(id: Long = CHAT_ID): Chat = checkNotNull(chat(id).first())

    /** The ids of the chats in [list], in the repository's order. */
    private suspend fun TdlibChatRepository.idsIn(list: ChatListType): List<Long> = chatList(list).first().map { it.id }

    private val main = TdApi.ChatListMain()
    private val archive = TdApi.ChatListArchive()
    private val folder = TdApi.ChatListFolder(FOLDER_ID)

    private val groupChat = TdApi.ChatTypeBasicGroup(5)
    private val channel = TdApi.ChatTypeSupergroup(6, true)
    private val followsDefault = chatNotificationSettings(useDefaultMuteFor = true)

    private fun ok() = TdResult.Success(TdApi.Ok())

    private fun failure(error: TdApi.Error) = TdResult.Failure(TdError(error.code, error.message))

    // The cache

    @Test
    fun `knows no chat before TDLib announces one`() =
        runTest {
            assertNull(repository().chat(CHAT_ID).first())
        }

    @Test
    fun `caches a chat announced right after it was created, before TDLib could start`() =
        runTest {
            val repository = repository()

            // No suspension in between: an update without a subscriber is lost, as it would be from TDLib.
            client.emit(updateNewChat(chat(id = CHAT_ID, title = "Ada")))

            assertEquals("Ada", repository.cached().title)
        }

    @Test
    fun `updates of a chat TDLib has not announced are ignored`() =
        runTest {
            val repository = repository()

            client.emit(TdApi.UpdateChatTitle(CHAT_ID, "Grace"))
            client.emit(updateChatPosition(CHAT_ID, chatPosition(order = 10)))

            assertNull(repository.chat(CHAT_ID).first())
            assertEquals(emptyList<Long>(), repository.idsIn(ChatListType.Main))
        }

    @Test
    fun `chat emits only changes of that chat`() =
        runTest {
            val repository = repository()
            client.emit(updateNewChat(chat(id = CHAT_ID)))
            client.emit(updateNewChat(chat(id = OTHER_CHAT_ID)))

            repository.chat(CHAT_ID).test {
                awaitItem()

                client.emit(TdApi.UpdateChatTitle(OTHER_CHAT_ID, "Grace"))
                client.emit(updateChatFolders(chatFolderInfo()))

                expectNoEvents()
            }
        }

    @Test
    fun `an update that changes nothing emits nothing`() =
        runTest {
            val repository = repository()
            client.emit(updateNewChat(chat(id = CHAT_ID, title = "Ada")))

            repository.chatList(ChatListType.Main).test {
                awaitItem()

                client.emit(TdApi.UpdateChatTitle(CHAT_ID, "Ada"))

                expectNoEvents()
            }
        }

    // Updates of a chat's fields

    @Test
    fun `updateChatTitle changes the title`() =
        runTest {
            val repository = repository()
            client.emit(updateNewChat(chat(title = "Ada")))

            client.emit(TdApi.UpdateChatTitle(CHAT_ID, "Grace"))

            assertEquals("Grace", repository.cached().title)
        }

    @Test
    fun `updateChatPhoto sets the photo`() =
        runTest {
            val repository = repository()
            client.emit(updateNewChat(chat(photo = null)))
            val photo = TdApiFixtures.chatPhotoInfo()

            client.emit(TdApi.UpdateChatPhoto(CHAT_ID, photo))

            assertEquals(photo.toProfilePhoto(), repository.cached().photo)
        }

    @Test
    fun `updateChatPhoto without a photo removes it`() =
        runTest {
            val repository = repository()
            client.emit(updateNewChat(chat(photo = TdApiFixtures.chatPhotoInfo())))

            client.emit(TdApi.UpdateChatPhoto(CHAT_ID, null))

            assertNull(repository.cached().photo)
        }

    @Test
    fun `updateChatLastMessage sets the last message`() =
        runTest {
            val repository = repository()
            client.emit(updateNewChat(chat()))
            val message = TdApiFixtures.message(id = 7, chatId = CHAT_ID)

            client.emit(TdApi.UpdateChatLastMessage(CHAT_ID, message, arrayOf(chatPosition(order = 2_000))))

            assertEquals(message.toMessage(), repository.cached().lastMessage)
        }

    @Test
    fun `updateChatLastMessage without a message clears it`() =
        runTest {
            val repository = repository()
            client.emit(updateNewChat(chat(lastMessage = TdApiFixtures.message(chatId = CHAT_ID))))

            client.emit(TdApi.UpdateChatLastMessage(CHAT_ID, null, arrayOf(chatPosition())))

            assertNull(repository.cached().lastMessage)
        }

    @Test
    fun `updateChatReadInbox sets the last read incoming message and the unread count`() =
        runTest {
            val repository = repository()
            client.emit(updateNewChat(chat(unreadCount = 5, lastReadInboxMessageId = 1)))

            client.emit(TdApi.UpdateChatReadInbox(CHAT_ID, 4, 2))

            val chat = repository.cached()
            assertEquals(4L to 2, chat.lastReadInboxMessageId to chat.unreadCount)
        }

    @Test
    fun `updateChatReadOutbox sets the last outgoing message read by the other side`() =
        runTest {
            val repository = repository()
            client.emit(updateNewChat(chat(lastReadOutboxMessageId = 1)))

            client.emit(TdApi.UpdateChatReadOutbox(CHAT_ID, 6))

            assertEquals(6L, repository.cached().lastReadOutboxMessageId)
        }

    @Test
    fun `updateChatUnreadMentionCount sets the unread mention count`() =
        runTest {
            val repository = repository()
            client.emit(updateNewChat(chat(unreadMentionCount = 0)))

            client.emit(TdApi.UpdateChatUnreadMentionCount(CHAT_ID, 3))

            assertEquals(3, repository.cached().unreadMentionCount)
        }

    @Test
    fun `updateMessageMentionRead sets the unread mention count`() =
        runTest {
            val repository = repository()
            client.emit(updateNewChat(chat(unreadMentionCount = 3)))

            client.emit(TdApi.UpdateMessageMentionRead(CHAT_ID, 7, 2))

            assertEquals(2, repository.cached().unreadMentionCount)
        }

    @Test
    fun `updateChatUnreadReactionCount sets the unread reaction count`() =
        runTest {
            val repository = repository()
            client.emit(updateNewChat(chat(unreadReactionCount = 0)))

            client.emit(TdApi.UpdateChatUnreadReactionCount(CHAT_ID, 4))

            assertEquals(4, repository.cached().unreadReactionCount)
        }

    @Test
    fun `updateMessageUnreadReactions sets the unread reaction count`() =
        runTest {
            val repository = repository()
            client.emit(updateNewChat(chat(unreadReactionCount = 4)))

            client.emit(TdApi.UpdateMessageUnreadReactions(CHAT_ID, 7, emptyArray(), 3))

            assertEquals(3, repository.cached().unreadReactionCount)
        }

    @Test
    fun `updateChatDraftMessage sets the draft`() =
        runTest {
            val repository = repository()
            client.emit(updateNewChat(chat(draftMessage = null)))
            val draft = TdApiFixtures.draftMessage(TdApiFixtures.draftText("Later"))

            client.emit(TdApi.UpdateChatDraftMessage(CHAT_ID, draft, arrayOf(chatPosition(order = 2_000))))

            assertEquals(draft.toChatDraft(), repository.cached().draft)
        }

    @Test
    fun `updateChatDraftMessage without a draft removes it`() =
        runTest {
            val repository = repository()
            client.emit(updateNewChat(chat(draftMessage = TdApiFixtures.draftMessage())))

            client.emit(TdApi.UpdateChatDraftMessage(CHAT_ID, null, arrayOf(chatPosition())))

            assertNull(repository.cached().draft)
        }

    @Test
    fun `updateChatIsMarkedAsUnread marks the chat`() =
        runTest {
            val repository = repository()
            client.emit(updateNewChat(chat(isMarkedAsUnread = false)))

            client.emit(TdApi.UpdateChatIsMarkedAsUnread(CHAT_ID, true))

            assertTrue(repository.cached().isMarkedAsUnread)
        }

    @Test
    fun `updateChatHasProtectedContent protects the content`() =
        runTest {
            val repository = repository()
            client.emit(updateNewChat(chat(hasProtectedContent = false)))

            client.emit(TdApi.UpdateChatHasProtectedContent(CHAT_ID, true))

            assertTrue(repository.cached().hasProtectedContent)
        }

    @Test
    fun `updateChatMessageAutoDeleteTime sets the auto-delete time`() =
        runTest {
            val repository = repository()
            client.emit(updateNewChat(chat(messageAutoDeleteTime = 0)))

            client.emit(TdApi.UpdateChatMessageAutoDeleteTime(CHAT_ID, 86_400))

            assertEquals(86_400, repository.cached().messageAutoDeleteTime)
        }

    // Chat lists

    @Test
    fun `chatList sorts the chats by order, highest first`() =
        runTest {
            val repository = repository()

            client.emit(updateNewChat(chat(id = 1, positions = arrayOf(chatPosition(order = 10)))))
            client.emit(updateNewChat(chat(id = 2, positions = arrayOf(chatPosition(order = 30)))))
            client.emit(updateNewChat(chat(id = 3, positions = arrayOf(chatPosition(order = 20)))))

            assertEquals(listOf(2L, 3L, 1L), repository.idsIn(ChatListType.Main))
        }

    @Test
    fun `pinned chats come first, as TDLib gives them the highest orders`() =
        runTest {
            val repository = repository()

            client.emit(updateNewChat(chat(id = 1, positions = arrayOf(chatPosition(order = 500)))))
            client.emit(updateNewChat(chat(id = 2, positions = arrayOf(chatPosition(order = PINNED, isPinned = true)))))

            val chats = repository.chatList(ChatListType.Main).first()
            assertEquals(listOf(2L, 1L), chats.map { it.id })
            assertEquals(true, chats.first().positionIn(ChatListType.Main)?.isPinned)
        }

    @Test
    fun `chats with the same order are sorted by id, highest first`() =
        runTest {
            val repository = repository()

            client.emit(updateNewChat(chat(id = 1, positions = arrayOf(chatPosition(order = 10)))))
            client.emit(updateNewChat(chat(id = 3, positions = arrayOf(chatPosition(order = 10)))))
            client.emit(updateNewChat(chat(id = 2, positions = arrayOf(chatPosition(order = 10)))))

            assertEquals(listOf(3L, 2L, 1L), repository.idsIn(ChatListType.Main))
        }

    @Test
    fun `chatList holds only the chats with a position in that list`() =
        runTest {
            val repository = repository()

            client.emit(updateNewChat(chat(id = 1, positions = arrayOf(chatPosition(main)))))
            client.emit(updateNewChat(chat(id = 2, positions = arrayOf(chatPosition(archive)))))
            client.emit(updateNewChat(chat(id = 3, positions = emptyArray())))

            assertEquals(listOf(2L), repository.idsIn(ChatListType.Archive))
        }

    @Test
    fun `a chat is in every list it has a position in`() =
        runTest {
            val repository = repository()

            client.emit(updateNewChat(chat(positions = arrayOf(chatPosition(main), chatPosition(folder)))))

            assertEquals(listOf(CHAT_ID), repository.idsIn(ChatListType.Main))
            assertEquals(listOf(CHAT_ID), repository.idsIn(ChatListType.Folder(FOLDER_ID)))
        }

    @Test
    fun `updateChatPosition moves the chat within the list`() =
        runTest {
            val repository = repository()
            client.emit(updateNewChat(chat(id = 1, positions = arrayOf(chatPosition(order = 10)))))
            client.emit(updateNewChat(chat(id = 2, positions = arrayOf(chatPosition(order = 20)))))

            client.emit(updateChatPosition(1, chatPosition(order = 30)))

            assertEquals(listOf(1L, 2L), repository.idsIn(ChatListType.Main))
        }

    @Test
    fun `updateChatPosition adds the chat to a list it was not in`() =
        runTest {
            val repository = repository()
            client.emit(updateNewChat(chat(positions = arrayOf(chatPosition(main)))))

            client.emit(updateChatPosition(CHAT_ID, chatPosition(folder, order = 10)))

            assertEquals(listOf(CHAT_ID), repository.idsIn(ChatListType.Folder(FOLDER_ID)))
        }

    @Test
    fun `updateChatPosition with order 0 takes the chat out of the list but keeps it cached`() =
        runTest {
            val repository = repository()
            client.emit(updateNewChat(chat(positions = arrayOf(chatPosition(main)))))

            client.emit(updateChatPosition(CHAT_ID, chatPosition(main, order = 0)))

            assertEquals(emptyList<Long>(), repository.idsIn(ChatListType.Main))
            assertNotNull(repository.chat(CHAT_ID).first())
        }

    @Test
    fun `updateChatPosition in one list leaves the positions in other lists alone`() =
        runTest {
            val repository = repository()
            client.emit(updateNewChat(chat(positions = arrayOf(chatPosition(main, order = 10)))))

            client.emit(updateChatPosition(CHAT_ID, chatPosition(archive, order = 20)))

            assertEquals(
                setOf(
                    ChatPosition(ChatListType.Main, order = 10, isPinned = false),
                    ChatPosition(ChatListType.Archive, order = 20, isPinned = false),
                ),
                repository.cached().positions.toSet(),
            )
        }

    @Test
    fun `updateChatLastMessage replaces every position`() =
        runTest {
            val repository = repository()
            client.emit(updateNewChat(chat(positions = arrayOf(chatPosition(main), chatPosition(folder)))))

            client.emit(TdApi.UpdateChatLastMessage(CHAT_ID, null, arrayOf(chatPosition(archive, order = 40))))

            assertEquals(
                listOf(ChatPosition(ChatListType.Archive, order = 40, isPinned = false)),
                repository.cached().positions,
            )
        }

    @Test
    fun `updateChatDraftMessage replaces every position`() =
        runTest {
            val repository = repository()
            client.emit(updateNewChat(chat(positions = arrayOf(chatPosition(main, order = 10)))))

            client.emit(TdApi.UpdateChatDraftMessage(CHAT_ID, null, arrayOf(chatPosition(main, order = 50))))

            assertEquals(
                listOf(ChatPosition(ChatListType.Main, order = 50, isPinned = false)),
                repository.cached().positions,
            )
        }

    @Test
    fun `chatList emits a new list when a chat in it changes`() =
        runTest {
            val repository = repository()
            client.emit(updateNewChat(chat(title = "Ada")))

            repository.chatList(ChatListType.Main).test {
                awaitItem()

                client.emit(TdApi.UpdateChatTitle(CHAT_ID, "Grace"))

                assertEquals(listOf("Grace"), awaitItem().map { it.title })
            }
        }

    @Test
    fun `chatList emits nothing when only other lists, folders or counts change`() =
        runTest {
            val repository = repository()
            client.emit(updateNewChat(chat(id = 1, positions = arrayOf(chatPosition(main)))))
            client.emit(updateNewChat(chat(id = 2, positions = arrayOf(chatPosition(archive)))))

            repository.chatList(ChatListType.Main).test {
                awaitItem()

                client.emit(TdApi.UpdateChatTitle(2, "Archived"))
                client.emit(updateChatFolders(chatFolderInfo()))
                client.emit(updateUnreadChatCount(main, unreadCount = 1))

                expectNoEvents()
            }
        }

    @Test
    fun `snapshots of an unchanged list are equal`() =
        runTest {
            val repository = repository()
            client.emit(updateNewChat(chat(id = 1)))
            client.emit(updateNewChat(chat(id = 2)))

            val first = repository.chatList(ChatListType.Main).first()
            client.emit(updateUnreadChatCount(main, unreadCount = 1))

            assertEquals(first, repository.chatList(ChatListType.Main).first())
        }

    // Mute

    @Test
    fun `a chat muted by its own setting is muted`() =
        runTest {
            val repository = repository()
            val settings = chatNotificationSettings(useDefaultMuteFor = false, muteFor = 3_600)

            client.emit(updateNewChat(chat(notificationSettings = settings)))

            assertTrue(repository.cached().isMuted)
        }

    @Test
    fun `a chat announced while its scope is muted starts muted`() =
        runTest {
            val repository = repository()
            client.emit(updateScopeNotificationSettings(TdApi.NotificationSettingsScopeGroupChats(), muteFor = MUTED))

            client.emit(updateNewChat(chat(type = groupChat, notificationSettings = followsDefault)))

            assertTrue(repository.cached().isMuted)
        }

    @Test
    fun `muting a scope mutes the chats that follow its default`() =
        runTest {
            val repository = repository()
            client.emit(updateNewChat(chat(type = groupChat, notificationSettings = followsDefault)))

            client.emit(updateScopeNotificationSettings(TdApi.NotificationSettingsScopeGroupChats(), muteFor = MUTED))

            assertTrue(repository.cached().isMuted)
        }

    @Test
    fun `unmuting a scope unmutes the chats that follow its default`() =
        runTest {
            val repository = repository()
            client.emit(updateScopeNotificationSettings(TdApi.NotificationSettingsScopeGroupChats(), muteFor = MUTED))
            client.emit(updateNewChat(chat(type = groupChat, notificationSettings = followsDefault)))

            client.emit(updateScopeNotificationSettings(TdApi.NotificationSettingsScopeGroupChats(), muteFor = 0))

            assertFalse(repository.cached().isMuted)
        }

    @Test
    fun `a scope change leaves chats with their own setting alone`() =
        runTest {
            val repository = repository()
            val unmuted = chatNotificationSettings(useDefaultMuteFor = false, muteFor = 0)
            client.emit(updateNewChat(chat(type = groupChat, notificationSettings = unmuted)))

            client.emit(updateScopeNotificationSettings(TdApi.NotificationSettingsScopeGroupChats(), muteFor = MUTED))

            assertFalse(repository.cached().isMuted)
        }

    @Test
    fun `a scope change leaves chats of other scopes alone`() =
        runTest {
            val repository = repository()
            client.emit(updateNewChat(chat(type = channel, notificationSettings = followsDefault)))

            client.emit(updateScopeNotificationSettings(TdApi.NotificationSettingsScopeGroupChats(), muteFor = MUTED))

            assertFalse(repository.cached().isMuted)
        }

    @Test
    fun `updateChatNotificationSettings mutes the chat by its own setting`() =
        runTest {
            val repository = repository()
            client.emit(updateNewChat(chat(notificationSettings = followsDefault)))

            client.emit(
                TdApi.UpdateChatNotificationSettings(
                    CHAT_ID,
                    chatNotificationSettings(useDefaultMuteFor = false, muteFor = 3_600),
                ),
            )

            assertTrue(repository.cached().isMuted)
        }

    @Test
    fun `a chat that goes back to the default takes the mute of its scope`() =
        runTest {
            val repository = repository()
            client.emit(updateScopeNotificationSettings(TdApi.NotificationSettingsScopePrivateChats(), muteFor = MUTED))
            val unmuted = chatNotificationSettings(useDefaultMuteFor = false, muteFor = 0)
            client.emit(updateNewChat(chat(notificationSettings = unmuted)))

            client.emit(TdApi.UpdateChatNotificationSettings(CHAT_ID, followsDefault))

            assertTrue(repository.cached().isMuted)
        }

    @Test
    fun `a chat back on the default follows later scope changes`() =
        runTest {
            val repository = repository()
            val muted = chatNotificationSettings(useDefaultMuteFor = false, muteFor = 3_600)
            client.emit(updateNewChat(chat(notificationSettings = muted)))
            client.emit(TdApi.UpdateChatNotificationSettings(CHAT_ID, followsDefault))

            client.emit(updateScopeNotificationSettings(TdApi.NotificationSettingsScopePrivateChats(), muteFor = MUTED))

            assertTrue(repository.cached().isMuted)
        }

    // Folders and unread counts

    @Test
    fun `there are no folders before TDLib reports them`() =
        runTest {
            assertEquals(emptyList<ChatFolder>(), repository().folders.first())
        }

    @Test
    fun `folders follow updateChatFolders, in TDLib's order`() =
        runTest {
            val repository = repository()

            client.emit(
                updateChatFolders(chatFolderInfo(id = 2, title = "Work"), chatFolderInfo(id = 1, title = "Home")),
            )

            assertEquals(listOf(2, 1), repository.folders.first().map { it.id })
        }

    @Test
    fun `a new updateChatFolders replaces the folders`() =
        runTest {
            val repository = repository()
            client.emit(updateChatFolders(chatFolderInfo(id = 1, title = "Work")))

            client.emit(updateChatFolders(chatFolderInfo(id = 1, title = "Job")))

            assertEquals(
                listOf(FormattedText("Job", persistentListOf())),
                repository.folders.first().map { it.title },
            )
        }

    @Test
    fun `All chats comes first until TDLib reports the folders`() =
        runTest {
            assertEquals(0, repository().mainChatListPosition.first())
        }

    @Test
    fun `the position of All chats follows updateChatFolders`() =
        runTest {
            val repository = repository()

            client.emit(updateChatFolders(chatFolderInfo(id = 1), chatFolderInfo(id = 2), mainChatListPosition = 1))

            assertEquals(1, repository.mainChatListPosition.first())
        }

    @Test
    fun `unreadChatCount is None before TDLib counted the list`() =
        runTest {
            assertEquals(UnreadChatCount.None, repository().unreadChatCount(ChatListType.Main).first())
        }

    @Test
    fun `unreadChatCount follows the counts of that list`() =
        runTest {
            val repository = repository()

            client.emit(updateUnreadChatCount(folder, totalCount = 9, unreadCount = 3, unreadUnmutedCount = 2))

            assertEquals(
                UnreadChatCount(9, 3, 2, 0, 0),
                repository.unreadChatCount(ChatListType.Folder(FOLDER_ID)).first(),
            )
        }

    @Test
    fun `unreadChatCount emits nothing when other lists are counted`() =
        runTest {
            val repository = repository()

            repository.unreadChatCount(ChatListType.Main).test {
                awaitItem()

                client.emit(updateUnreadChatCount(archive, unreadCount = 3))

                expectNoEvents()
            }
        }

    // loadChats

    @Test
    fun `loadChats asks TDLib for the next chats of the list`() =
        runTest {
            client.on(TdApi.LoadChats::class, ok())

            val result = repository().loadChats(ChatListType.Folder(FOLDER_ID), limit = 20)

            val request = client.sentOf<TdApi.LoadChats>().single()
            assertEquals(FOLDER_ID, (request.chatList as TdApi.ChatListFolder).chatFolderId)
            assertEquals(20, request.limit)
            assertEquals(Outcome.Success(true), result)
        }

    @Test
    fun `loadChats loads pages of 50 by default`() =
        runTest {
            client.on(TdApi.LoadChats::class, ok())

            repository().loadChats(ChatListType.Main)

            assertEquals(50, client.sentOf<TdApi.LoadChats>().single().limit)
        }

    @Test
    fun `loadChats reports that the list is complete when TDLib answers 404`() =
        runTest {
            client.on(TdApi.LoadChats::class, failure(TdApi.Error(404, "Not Found")))

            assertEquals(Outcome.Success(false), repository().loadChats(ChatListType.Main))
        }

    @Test
    fun `loadChats reports nothing more to load for a folder that no longer exists`() =
        runTest {
            client.on(TdApi.LoadChats::class, failure(TdApi.Error(400, "Chat list not found")))

            assertEquals(Outcome.Success(false), repository().loadChats(ChatListType.Folder(FOLDER_ID)))
        }

    @Test
    fun `loadChats maps a flood wait`() =
        runTest {
            client.on(TdApi.LoadChats::class, failure(TdApiFixtures.floodWait(seconds = 30)))

            assertEquals(Outcome.Failure(DomainError.FloodWait(30)), repository().loadChats(ChatListType.Main))
        }

    @Test
    fun `loadChats fails with Network when TDLib does not answer in time`() =
        runTest {
            val never = CompletableDeferred<TdResult<TdApi.Ok>>()
            client.on(TdApi.LoadChats::class) { never.await() }

            // runTest skips the 30 s timeout in virtual time.
            assertEquals(Outcome.Failure(DomainError.Network), repository().loadChats(ChatListType.Main))
        }

    // getChat

    @Test
    fun `getChat answers from the cache without a request`() =
        runTest {
            val repository = repository()
            client.emit(updateNewChat(chat(id = CHAT_ID)))

            assertEquals(Outcome.Success(repository.cached()), repository.getChat(CHAT_ID))
            assertEquals(emptyList<TdApi.Function<*>>(), client.sent)
        }

    @Test
    fun `getChat asks TDLib for a chat it has not announced`() =
        runTest {
            client.on(TdApi.GetChat::class) { request -> TdResult.Success(chat(id = request.chatId, title = "Ada")) }

            val result = repository().getChat(OTHER_CHAT_ID)

            assertEquals(OTHER_CHAT_ID, client.sentOf<TdApi.GetChat>().single().chatId)
            assertEquals("Ada", (result as Outcome.Success).value.title)
        }

    @Test
    fun `getChat resolves the mute of a requested chat against the defaults`() =
        runTest {
            val repository = repository()
            client.emit(updateScopeNotificationSettings(TdApi.NotificationSettingsScopePrivateChats(), muteFor = MUTED))
            client.on(TdApi.GetChat::class) { request ->
                TdResult.Success(chat(id = request.chatId, notificationSettings = followsDefault))
            }

            val result = repository.getChat(OTHER_CHAT_ID)

            assertEquals(true, (result as Outcome.Success).value.isMuted)
        }

    @Test
    fun `getChat maps a chat TDLib does not know to NotFound`() =
        runTest {
            client.on(TdApi.GetChat::class, failure(TdApi.Error(400, "Chat not found")))

            assertEquals(Outcome.Failure(DomainError.NotFound), repository().getChat(OTHER_CHAT_ID))
        }

    @Test
    fun `getChat maps a flood wait`() =
        runTest {
            client.on(TdApi.GetChat::class, failure(TdApiFixtures.floodWait(seconds = 30)))

            assertEquals(Outcome.Failure(DomainError.FloodWait(30)), repository().getChat(OTHER_CHAT_ID))
        }

    // Logout

    @Test
    fun `forgets every chat when TDLib closes`() =
        runTest {
            val repository = repository()
            client.emit(updateNewChat(chat(id = CHAT_ID)))

            client.emitAuthorizationState(TdApiFixtures.closed())

            assertNull(repository.chat(CHAT_ID).first())
            assertEquals(emptyList<Long>(), repository.idsIn(ChatListType.Main))
        }

    @Test
    fun `forgets the folders and unread counts when TDLib closes`() =
        runTest {
            val repository = repository()
            client.emit(updateChatFolders(chatFolderInfo(), mainChatListPosition = 1))
            client.emit(updateUnreadChatCount(main, unreadCount = 3))

            client.emitAuthorizationState(TdApiFixtures.closed())

            assertEquals(emptyList<ChatFolder>(), repository.folders.first())
            assertEquals(0, repository.mainChatListPosition.first())
            assertEquals(UnreadChatCount.None, repository.unreadChatCount(ChatListType.Main).first())
        }

    @Test
    fun `forgets the muted defaults when TDLib closes`() =
        runTest {
            val repository = repository()
            client.emit(updateScopeNotificationSettings(TdApi.NotificationSettingsScopePrivateChats(), muteFor = MUTED))

            client.emitAuthorizationState(TdApiFixtures.closed())
            client.emit(updateNewChat(chat(notificationSettings = followsDefault)))

            assertFalse(repository.cached().isMuted)
        }

    private companion object {
        const val CHAT_ID = 100L
        const val OTHER_CHAT_ID = 200L
        const val FOLDER_ID = 3

        /** An order above every unpinned chat's, as TDLib gives pinned chats. */
        const val PINNED = 9_000_000_000_000_000_000L

        /** Seconds left of a scope's mute: any positive time mutes it. */
        const val MUTED = 3_600
    }
}
