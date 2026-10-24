package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatFolderExcludedType
import dev.kyralabs.pingogram.core.domain.model.ChatFolderIncludedType
import dev.kyralabs.pingogram.core.domain.model.ChatFolderSettings
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TdlibChatFolderRepositoryTest {
    private val client = FakeTdlibClient()
    private val repository = TdlibChatFolderRepository(client)

    private fun tdFolder(
        title: String = "Work",
        icon: TdApi.ChatFolderIcon? = TdApi.ChatFolderIcon("Work"),
    ) = TdApi.ChatFolder(
        TdApi.ChatFolderName(TdApi.FormattedText(title, emptyArray()), true),
        icon,
        2,
        false,
        longArrayOf(1),
        longArrayOf(2, 3),
        longArrayOf(4),
        true,
        false,
        true,
        false,
        false,
        true,
        true,
        false,
    )

    private val settings =
        ChatFolderSettings(
            title = FormattedText("Work", persistentListOf()),
            iconName = "Work",
            colorId = 2,
            pinnedChatIds = listOf(1),
            includedChatIds = listOf(1, 2, 3),
            excludedChatIds = listOf(4),
            includedTypes = setOf(ChatFolderIncludedType.Groups, ChatFolderIncludedType.Bots),
            excludedTypes = setOf(ChatFolderExcludedType.Muted, ChatFolderExcludedType.Archived),
        )

    @Test
    fun `reads what a folder holds`() =
        runTest {
            client.on(TdApi.GetChatFolder::class, TdResult.Success(tdFolder()))

            val folder = (repository.folder(7) as Outcome.Success).value

            assertEquals("Work", folder.title.text)
            assertEquals("Work", folder.iconName)
            assertEquals(2, folder.colorId)
            assertEquals(listOf(1L), folder.pinnedChatIds)
            assertEquals(listOf(2L, 3L), folder.includedChatIds)
            assertEquals(listOf(4L), folder.excludedChatIds)
            assertEquals(setOf(ChatFolderIncludedType.Groups, ChatFolderIncludedType.Bots), folder.includedTypes)
            assertEquals(setOf(ChatFolderExcludedType.Muted, ChatFolderExcludedType.Archived), folder.excludedTypes)
            assertEquals(7, client.sentOf<TdApi.GetChatFolder>().single().chatFolderId)
        }

    @Test
    fun `a folder without a chosen icon lets Telegram choose`() =
        runTest {
            client.on(TdApi.GetChatFolder::class, TdResult.Success(tdFolder(icon = null)))

            assertNull((repository.folder(7) as Outcome.Success).value.iconName)
        }

    @Test
    fun `creates a folder, its pinned chats sent once`() =
        runTest {
            client.on(
                TdApi.CreateChatFolder::class,
                TdResult.Success(
                    TdApi.ChatFolderInfo(
                        9,
                        TdApi.ChatFolderName(TdApi.FormattedText("Work", emptyArray()), true),
                        TdApi.ChatFolderIcon("Work"),
                        2,
                        false,
                        false,
                    ),
                ),
            )

            assertEquals(Outcome.Success(9), repository.createFolder(settings))
            val sent = client.sentOf<TdApi.CreateChatFolder>().single().folder
            assertArrayEquals(longArrayOf(1), sent.pinnedChatIds)
            assertArrayEquals(longArrayOf(2, 3), sent.includedChatIds)
            assertTrue(sent.includeGroups && sent.includeBots && sent.excludeMuted && sent.excludeArchived)
            assertFalse(sent.includeContacts || sent.includeChannels || sent.excludeRead)
            assertEquals("Work", sent.icon?.name)
        }

    @Test
    fun `a folder that includes nothing is refused before TDLib`() =
        runTest {
            val empty = ChatFolderSettings(title = FormattedText("Empty", persistentListOf()))

            assertEquals(Outcome.Failure(DomainError.ChatFolderEmpty), repository.createFolder(empty))
            assertEquals(Outcome.Failure(DomainError.ChatFolderEmpty), repository.editFolder(3, empty))
            assertTrue(client.sent.isEmpty())
        }

    @Test
    fun `edits, deletes and reorders folders`() =
        runTest {
            client.on(TdApi.EditChatFolder::class, TdResult.Success(TdApi.ChatFolderInfo()))
            client.on(TdApi.DeleteChatFolder::class, TdResult.Success(TdApi.Ok()))
            client.on(TdApi.ReorderChatFolders::class, TdResult.Success(TdApi.Ok()))

            assertEquals(Outcome.Success(Unit), repository.editFolder(3, settings))
            assertEquals(Outcome.Success(Unit), repository.deleteFolder(3, leaveChatIds = listOf(5)))
            assertEquals(Outcome.Success(Unit), repository.reorderFolders(listOf(4, 3), mainChatListPosition = 0))

            assertEquals(3, client.sentOf<TdApi.EditChatFolder>().single().chatFolderId)
            assertArrayEquals(longArrayOf(5), client.sentOf<TdApi.DeleteChatFolder>().single().leaveChatIds)
            assertArrayEquals(intArrayOf(4, 3), client.sentOf<TdApi.ReorderChatFolders>().single().chatFolderIds)
        }

    @Test
    fun `too many folders fails as such`() =
        runTest {
            client.on(TdApi.CreateChatFolder::class, TdResult.Failure(TdError(400, "DIALOG_FILTERS_TOO_MUCH")))

            assertEquals(Outcome.Failure(DomainError.TooManyChatFolders), repository.createFolder(settings))
        }

    @Test
    fun `lists the recommended folders with what they hold`() =
        runTest {
            client.on(
                TdApi.GetRecommendedChatFolders::class,
                TdResult.Success(
                    TdApi.RecommendedChatFolders(
                        arrayOf(TdApi.RecommendedChatFolder(tdFolder("Unread"), "Unread chats")),
                    ),
                ),
            )

            val recommended = (repository.recommendedFolders() as Outcome.Success).value.single()

            assertEquals("Unread", recommended.settings.title.text)
            assertEquals("Unread chats", recommended.description)
        }

    @Test
    fun `checks a folder invite link and adds its folder with the chosen chats`() =
        runTest {
            client.on(
                TdApi.CheckChatFolderInviteLink::class,
                TdResult.Success(
                    TdApi.ChatFolderInviteLinkInfo(
                        TdApi.ChatFolderInfo(
                            0,
                            TdApi.ChatFolderName(TdApi.FormattedText("Friends", emptyArray()), true),
                            TdApi.ChatFolderIcon("Custom"),
                            -1,
                            true,
                            false,
                        ),
                        longArrayOf(10, 11),
                        longArrayOf(12),
                    ),
                ),
            )
            client.on(TdApi.AddChatFolderByInviteLink::class, TdResult.Success(TdApi.Ok()))

            val invite = (repository.checkInviteLink("https://t.me/addlist/abc") as Outcome.Success).value
            assertFalse(invite.isAdded)
            assertEquals(listOf(10L, 11L), invite.missingChatIds)
            assertEquals(listOf(12L), invite.addedChatIds)

            assertEquals(
                Outcome.Success(Unit),
                repository.addFolderByInviteLink("https://t.me/addlist/abc", listOf(10)),
            )
            assertArrayEquals(longArrayOf(10), client.sentOf<TdApi.AddChatFolderByInviteLink>().single().chatIds)
        }

    @Test
    fun `an expired folder invite link fails as an invalid link`() =
        runTest {
            client.on(TdApi.CheckChatFolderInviteLink::class, TdResult.Failure(TdError(400, "INVITE_SLUG_EXPIRED")))

            assertEquals(
                Outcome.Failure(DomainError.InviteLinkInvalid),
                repository.checkInviteLink("https://t.me/addlist/old"),
            )
        }

    @Test
    fun `creates and lists a folder's invite links`() =
        runTest {
            val link = TdApi.ChatFolderInviteLink("https://t.me/addlist/abc", "", longArrayOf(10))
            client.on(TdApi.CreateChatFolderInviteLink::class, TdResult.Success(link))
            client.on(
                TdApi.GetChatFolderInviteLinks::class,
                TdResult.Success(TdApi.ChatFolderInviteLinks(arrayOf(link))),
            )
            client.on(
                TdApi.GetChatsForChatFolderInviteLink::class,
                TdResult.Success(TdApi.Chats(2, longArrayOf(10, 11))),
            )

            assertEquals(listOf(10L, 11L), (repository.shareableChats(3) as Outcome.Success).value)
            assertEquals(
                "https://t.me/addlist/abc",
                (repository.createInviteLink(3, listOf(10)) as Outcome.Success).value.link,
            )
            assertEquals(1, (repository.inviteLinks(3) as Outcome.Success).value.size)
        }
}
