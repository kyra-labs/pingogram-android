package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ArchiveSettings
import dev.kyralabs.pingogram.core.domain.model.ArchiveSettingsAvailability
import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TdlibArchiveSettingsRepositoryTest {
    private val client = FakeTdlibClient()
    private val repository = TdlibArchiveSettingsRepository(client)

    private fun answerSettings(settings: TdApi.ArchiveChatListSettings) =
        client.on(TdApi.GetArchiveChatListSettings::class, TdResult.Success(settings))

    @Test
    fun `reads the settings and whether new chats may be archived`() =
        runTest {
            answerSettings(TdApi.ArchiveChatListSettings(true, false, true))
            client.on(TdApi.GetOption::class, TdResult.Success(TdApi.OptionValueBoolean(true)))

            assertEquals(
                Outcome.Success(
                    ArchiveSettingsAvailability(
                        ArchiveSettings(
                            archiveAndMuteNewChatsFromNonContacts = true,
                            keepUnmutedChatsArchived = false,
                            keepChatsFromFoldersArchived = true,
                        ),
                        canArchiveAndMuteNewChats = true,
                    ),
                ),
                repository.archiveSettings(),
            )
            assertEquals(
                "can_archive_and_mute_new_chats_from_unknown_users",
                client.sentOf<TdApi.GetOption>().single().name,
            )
        }

    @Test
    fun `an option TDLib does not know, or cannot read, means new chats may not be archived`() =
        runTest {
            answerSettings(TdApi.ArchiveChatListSettings(false, true, false))
            client.on(TdApi.GetOption::class, TdResult.Success(TdApi.OptionValueEmpty()))
            val empty = repository.archiveSettings()
            client.on(TdApi.GetOption::class, TdResult.Failure(TdError(400, "BAD_REQUEST")))
            val failed = repository.archiveSettings()

            val expected =
                Outcome.Success(
                    ArchiveSettingsAvailability(
                        ArchiveSettings(keepUnmutedChatsArchived = true),
                        canArchiveAndMuteNewChats = false,
                    ),
                )
            assertEquals(expected, empty)
            assertEquals(expected, failed)
        }

    @Test
    fun `reading the settings maps TDLib's errors`() =
        runTest {
            client.on(
                TdApi.GetArchiveChatListSettings::class,
                TdResult.Failure(TdError(429, "Too Many Requests: retry after 4")),
            )

            assertEquals(Outcome.Failure(DomainError.FloodWait(4)), repository.archiveSettings())
            assertTrue(client.sentOf<TdApi.GetOption>().isEmpty())
        }

    @Test
    fun `reading the settings without an answer in time fails with a network error`() =
        runTest {
            client.on(TdApi.GetArchiveChatListSettings::class) {
                CompletableDeferred<TdResult<TdApi.ArchiveChatListSettings>>().await()
            }

            val result = async { repository.archiveSettings() }
            advanceTimeBy(REQUEST_TIMEOUT)
            runCurrent()

            assertEquals(Outcome.Failure(DomainError.Network), result.await())
        }

    @Test
    fun `sends the settings as they are`() =
        runTest {
            client.on(TdApi.SetArchiveChatListSettings::class, TdResult.Success(TdApi.Ok()))

            val result =
                repository.setArchiveSettings(
                    ArchiveSettings(
                        archiveAndMuteNewChatsFromNonContacts = false,
                        keepUnmutedChatsArchived = true,
                        keepChatsFromFoldersArchived = true,
                    ),
                )

            assertEquals(Outcome.Success(Unit), result)
            val sent = client.sentOf<TdApi.SetArchiveChatListSettings>().single().settings
            assertEquals(false, sent.archiveAndMuteNewChatsFromUnknownUsers)
            assertEquals(true, sent.keepUnmutedChatsArchived)
            assertEquals(true, sent.keepChatsFromFoldersArchived)
        }

    @Test
    fun `a refused change fails with the error`() =
        runTest {
            client.on(
                TdApi.SetArchiveChatListSettings::class,
                TdResult.Failure(TdError(400, "PREMIUM_ACCOUNT_REQUIRED")),
            )

            val result = repository.setArchiveSettings(ArchiveSettings(archiveAndMuteNewChatsFromNonContacts = true))

            assertInstanceOf(Outcome.Failure::class.java, result)
        }

    @Test
    fun `reads the main list, a folder or the archive`() =
        runTest {
            client.on(TdApi.ReadChatList::class, TdResult.Success(TdApi.Ok()))

            assertEquals(Outcome.Success(Unit), repository.readChatList(ChatListType.Main))
            repository.readChatList(ChatListType.Folder(3))
            repository.readChatList(ChatListType.Archive)

            val lists = client.sentOf<TdApi.ReadChatList>().map { it.chatList }
            assertInstanceOf(TdApi.ChatListMain::class.java, lists[0])
            assertEquals(3, (lists[1] as TdApi.ChatListFolder).chatFolderId)
            assertInstanceOf(TdApi.ChatListArchive::class.java, lists[2])
        }

    @Test
    fun `reading a list maps TDLib's errors`() =
        runTest {
            client.on(TdApi.ReadChatList::class, TdResult.Failure(TdError(401, "Unauthorized")))

            assertEquals(Outcome.Failure(DomainError.Unauthorized), repository.readChatList(ChatListType.Main))
        }
}
