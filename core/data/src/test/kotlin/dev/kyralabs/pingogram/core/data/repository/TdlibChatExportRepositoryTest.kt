package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ExportChatKind
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.repository.ChatExportAccess
import dev.kyralabs.pingogram.core.domain.repository.ChatExportRepository
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.chat
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.error
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.file
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.floodWait
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.message
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.user
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * [TdlibChatExportRepository] on a [FakeTdlibClient]: what may be exported, paging the history from the newest message
 * back, waiting out flood waits, the errors that end an export, names, and downloads that are waited for and
 * cancelled.
 */
class TdlibChatExportRepositoryTest {
    private val client = FakeTdlibClient()
    private val repository = TdlibChatExportRepository(client)

    private fun history(vararg ids: Long) =
        TdResult.Success(TdApi.Messages(ids.size, Array(ids.size) { message(id = ids[it], date = ids[it].toInt()) }))

    @Test
    fun `a chat of people may be exported, a secret chat and one with protected content not`() =
        runTest {
            client.on(TdApi.GetMe::class, TdResult.Success(user(id = 1)))
            client.on(TdApi.GetUser::class, TdResult.Success(user(id = 42)))
            client.on(TdApi.GetChat::class) { request ->
                TdResult.Success(
                    when (request.chatId) {
                        1L -> chat(id = 1, type = TdApi.ChatTypeSecret(5, 42))
                        2L -> chat(id = 2, type = TdApi.ChatTypeSupergroup(7, true), hasProtectedContent = true)
                        else -> chat(id = 100, title = "Ada")
                    },
                )
            }

            val (chat, access) = (repository.chat(100) as Outcome.Success).value
            assertEquals(100L, chat.id)
            assertEquals("Ada", chat.title)
            assertEquals(ExportChatKind.Private, chat.kind)
            assertEquals(ChatExportAccess.Allowed, access)
            assertEquals(ChatExportAccess.SecretChat, (repository.chat(1) as Outcome.Success).value.second)
            val protected = (repository.chat(2) as Outcome.Success).value
            assertEquals(ChatExportAccess.ProtectedContent, protected.second)
            assertEquals(ExportChatKind.Channel, protected.first.kind)
        }

    @Test
    fun `the user's own chat is Saved Messages, a bot's chat a bot`() =
        runTest {
            client.on(TdApi.GetMe::class, TdResult.Success(user(id = 42)))
            client.on(TdApi.GetChat::class, TdResult.Success(chat(id = 42, type = TdApi.ChatTypePrivate(42))))
            assertEquals(ExportChatKind.SavedMessages, (repository.chat(42) as Outcome.Success).value.first.kind)

            client.on(TdApi.GetMe::class, TdResult.Success(user(id = 1)))
            client.on(TdApi.GetUser::class, TdResult.Success(user(id = 42, type = TdApi.UserTypeBot())))
            assertEquals(ExportChatKind.Bot, (repository.chat(42) as Outcome.Success).value.first.kind)
        }

    @Test
    fun `an unknown chat fails`() =
        runTest {
            client.on(TdApi.GetChat::class, TdResult.Failure(TdError(404, "Not Found")))

            assertEquals(Outcome.Failure(DomainError.NotFound), repository.chat(100))
        }

    @Test
    fun `pages the history from the server, newest first, never repeating the message it starts from`() =
        runTest {
            client.on(TdApi.GetChatHistory::class, history(30, 20, 10))

            val page = (repository.page(chatId = 100, fromMessageId = 30) as Outcome.Success).value

            assertEquals(listOf(20L, 10L), page.map { it.id })
            val request = client.sentOf<TdApi.GetChatHistory>().single()
            assertEquals(100L, request.chatId)
            assertEquals(30L, request.fromMessageId)
            assertEquals(0, request.offset)
            assertEquals(ChatExportRepository.PAGE_SIZE, request.limit)
            assertFalse(request.onlyLocal)
        }

    @Test
    fun `the first page starts at the newest message and is sorted newest first`() =
        runTest {
            client.on(TdApi.GetChatHistory::class, history(10, 30, 20))

            val page = (repository.page(chatId = 100, fromMessageId = 0) as Outcome.Success).value

            assertEquals(listOf(30L, 20L, 10L), page.map { it.id })
        }

    @Test
    fun `a flood wait is waited out for the seconds Telegram asks, then the page is asked for again`() =
        runTest {
            var calls = 0
            client.on(TdApi.GetChatHistory::class) {
                calls++
                if (calls == 1) TdResult.Failure(floodWait(5).toTdError()) else history(1)
            }
            val waits = mutableListOf<Long>()

            val result = async { repository.page(100, 0) { waits += it } }
            runCurrent()
            assertEquals(listOf(5L), waits)
            advanceTimeBy(4_999)
            assertFalse(result.isCompleted)
            advanceTimeBy(2)

            assertEquals(listOf(1L), (result.await() as Outcome.Success).value.map { it.id })
            assertEquals(2, calls)
        }

    @Test
    fun `flood waits that do not end fail with the last one, never in a tight loop`() =
        runTest {
            client.on(TdApi.GetChatHistory::class, TdResult.Failure(floodWait(10).toTdError()))

            val result = repository.page(100, 0)

            assertEquals(Outcome.Failure(DomainError.FloodWait(10)), result)
            assertEquals(ChatExportRepository.MAX_FLOOD_WAITS + 1, client.sentOf<TdApi.GetChatHistory>().size)
            assertEquals(ChatExportRepository.MAX_FLOOD_WAITS * 10_000L, currentTime)
        }

    @Test
    fun `no network and a logged-out session fail the page`() =
        runTest {
            client.on(TdApi.GetChatHistory::class) {
                // TDLib never answers while offline; the request times out.
                CompletableDeferred<Unit>().await()
                history()
            }
            assertEquals(Outcome.Failure(DomainError.Network), repository.page(100, 0))

            client.on(TdApi.GetChatHistory::class, TdResult.Failure(error(401, "Unauthorized").toTdError()))
            assertEquals(Outcome.Failure(DomainError.Unauthorized), repository.page(100, 0))
        }

    @Test
    fun `counts the history with a search for every message, unknown when TDLib says nothing`() =
        runTest {
            client.on(
                TdApi.SearchChatMessages::class,
                TdResult.Success(TdApi.FoundChatMessages(1_234, emptyArray(), 0)),
            )
            assertEquals(Outcome.Success(1_234), repository.messageCount(100))
            val search = client.sentOf<TdApi.SearchChatMessages>().single()
            assertEquals("", search.query)
            assertNull(search.filter)

            client.on(TdApi.SearchChatMessages::class, TdResult.Success(TdApi.FoundChatMessages(-1, emptyArray(), 0)))
            assertEquals(Outcome.Success(null), repository.messageCount(100))
        }

    @Test
    fun `names users by their full name and chats by their title`() =
        runTest {
            client.on(TdApi.GetUser::class, TdResult.Success(user(id = 42, firstName = "Ada", lastName = "Lovelace")))
            client.on(TdApi.GetChat::class, TdResult.Success(chat(id = 7, title = "News")))

            assertEquals("Ada Lovelace", repository.senderName(MessageSender.User(42)))
            assertEquals("News", repository.senderName(MessageSender.Chat(7)))
            client.on(TdApi.GetUser::class, TdResult.Success(user(id = 43, firstName = "")))
            assertNull(repository.senderName(MessageSender.User(43)))
        }

    @Test
    fun `downloads a file at the lowest priority and waits for all of it`() =
        runTest {
            client.on(
                TdApi.DownloadFile::class,
                TdResult.Success(file(id = 9, localPath = "/files/photo.jpg", isDownloadingCompleted = true)),
            )

            assertEquals(Outcome.Success("/files/photo.jpg"), repository.download(9))
            val request = client.sentOf<TdApi.DownloadFile>().single()
            assertEquals(9, request.fileId)
            assertEquals(1, request.priority)
            assertTrue(request.synchronous)
        }

    @Test
    fun `a file that did not download is not found`() =
        runTest {
            client.on(TdApi.DownloadFile::class, TdResult.Success(file(id = 9)))

            assertEquals(Outcome.Failure(DomainError.NotFound), repository.download(9))
        }

    @Test
    fun `a download that is cancelled is cancelled in TDLib too`() =
        runTest {
            client.on(TdApi.CancelDownloadFile::class, TdResult.Success(TdApi.Ok()))
            client.on(TdApi.DownloadFile::class) {
                CompletableDeferred<Unit>().await()
                TdResult.Success(file(id = 9))
            }

            val download = async { repository.download(9) }
            runCurrent()
            download.cancel()
            runCurrent()

            assertEquals(9, client.sentOf<TdApi.CancelDownloadFile>().single().fileId)
        }
}

private fun TdApi.Error.toTdError() = TdError(code, message)
