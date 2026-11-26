package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.repository.ChatOpenings
import dev.kyralabs.pingogram.core.testing.FakeChatLockRepository
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** When chats were last opened (`REQUIREMENTS.md` FR-X24), kept in a file of their own, begun anew at logout only. */
class FileChatOpeningsRepositoryTest {
    @TempDir
    lateinit var directory: File

    private val client = FakeTdlibClient()
    private val openChats = OpenChats()
    private val chatLock = FakeChatLockRepository(locked = setOf(LOCKED))
    private var now: Instant = BEGUN

    private val clock =
        object : WallClock {
            override val minutes = MutableStateFlow(BEGUN)

            override fun now(): Instant = now

            override fun zone(): ZoneId = ZoneOffset.UTC
        }

    private val file get() = File(directory, FileChatOpeningsRepository.FILE_NAME)

    private fun TestScope.repository(file: File? = this@FileChatOpeningsRepositoryTest.file) =
        FileChatOpeningsRepository(client, clock, backgroundScope, file, StandardTestDispatcher(testScheduler)).also {
            val chats = TdlibChatRepository(client, backgroundScope, StandardTestDispatcher(testScheduler), openChats)
            ChatOpeningRecorder(openChats, it, chats, chatLock, backgroundScope)
            testScheduler.runCurrent()
        }

    @Test
    fun `begun when first read, a chat whose conversation shows counts as opened, and both survive the process`() =
        runTest {
            val repository = repository()
            assertEquals(ChatOpenings(since = BEGUN), repository.openings.first())

            now = LATER
            openChats.open(7)
            testScheduler.runCurrent()

            val expected = ChatOpenings(since = BEGUN, lastOpened = persistentMapOf(7L to LATER))
            assertEquals(expected, repository.openings.first())
            assertEquals(expected, repository().openings.first())
            assertEquals("since\t${BEGUN.epochSecond}\n7\t${LATER.epochSecond}\n", file.readText())
        }

    @Test
    fun `a locked chat and a secret chat are never recorded`() =
        runTest {
            val repository = repository()
            val secret = TdApiFixtures.chat(id = SECRET, type = TdApi.ChatTypeSecret(3, 42))
            client.emit(TdApiFixtures.updateNewChat(secret))
            testScheduler.runCurrent()

            now = LATER
            listOf(LOCKED, SECRET, 7L).forEach(openChats::open)
            testScheduler.runCurrent()

            assertEquals(persistentMapOf(7L to LATER), repository.openings.first().lastOpened)
        }

    @Test
    fun `marked opened, as not now does, and lines that are not one are skipped`() =
        runTest {
            file.writeText("since\t100\n7\t200\nnot a line\n8\n")
            val repository = repository()

            now = LATER
            repository.markOpened(9)

            assertEquals(
                ChatOpenings(
                    since = Instant.ofEpochSecond(100),
                    lastOpened = persistentMapOf(7L to Instant.ofEpochSecond(200), 9L to LATER),
                ),
                repository.openings.first(),
            )
        }

    @Test
    fun `logging out forgets them, and the record begins anew`() =
        runTest {
            val repository = repository()
            repository.markOpened(7)

            client.emitAuthorizationState(TdApi.AuthorizationStateClosed())
            testScheduler.runCurrent()
            assertEquals(
                1,
                repository.openings
                    .first()
                    .lastOpened.size,
            )

            client.emitAuthorizationState(TdApi.AuthorizationStateLoggingOut())
            testScheduler.runCurrent()
            now = LATER
            assertEquals(ChatOpenings(since = LATER), repository.openings.first())
        }

    @Test
    fun `the demo keeps them in memory`() =
        runTest {
            val repository = repository(file = null)
            repository.markOpened(7)

            assertEquals(
                setOf(7L),
                repository.openings
                    .first()
                    .lastOpened.keys,
            )
            assertFalse(file.exists())
        }

    private companion object {
        val BEGUN: Instant = Instant.parse("2026-11-26T09:00:00Z")
        val LATER: Instant = Instant.parse("2026-11-27T10:00:00Z")
        const val LOCKED = 11L
        const val SECRET = 12L
    }
}
