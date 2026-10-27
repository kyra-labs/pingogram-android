package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.StorageUsage
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TdlibStorageRepositoryTest {
    private val client = FakeTdlibClient()
    private val repository = TdlibStorageRepository(client)

    private fun statistics(
        filesSize: Long = 0,
        fileCount: Int = 0,
        databaseSize: Long = 0,
        languagePackDatabaseSize: Long = 0,
        logSize: Long = 0,
    ) = TdApi.StorageStatisticsFast(filesSize, fileCount, databaseSize, languagePackDatabaseSize, logSize)

    private fun deleted(size: Long) = TdApi.StorageStatistics(size, 0, emptyArray())

    @Test
    fun `storage usage maps TDLib's quick statistics, with both databases together`() =
        runTest {
            client.on(
                TdApi.GetStorageStatisticsFast::class,
                TdResult.Success(
                    statistics(
                        filesSize = 1_500_000_000,
                        fileCount = 3_456,
                        databaseSize = 80_000_000,
                        languagePackDatabaseSize = 5_000_000,
                        logSize = 10_000_000,
                    ),
                ),
            )

            assertEquals(
                Outcome.Success(StorageUsage(filesSize = 1_500_000_000, fileCount = 3_456, databaseSize = 85_000_000)),
                repository.storageUsage(),
            )
        }

    @Test
    fun `storage usage never reports negative sizes`() =
        runTest {
            client.on(
                TdApi.GetStorageStatisticsFast::class,
                TdResult.Success(statistics(filesSize = -1, fileCount = -1, databaseSize = -1)),
            )

            assertEquals(Outcome.Success(StorageUsage(0, 0, 0)), repository.storageUsage())
        }

    @Test
    fun `storage usage maps errors`() =
        runTest {
            client.on(
                TdApi.GetStorageStatisticsFast::class,
                TdResult.Failure(TdError(429, "Too Many Requests: retry after 7")),
            )

            assertEquals(Outcome.Failure(DomainError.FloodWait(7)), repository.storageUsage())
        }

    @Test
    fun `storage usage without an answer in time fails with a network error`() =
        runTest {
            client.on(TdApi.GetStorageStatisticsFast::class) {
                CompletableDeferred<TdResult<TdApi.StorageStatisticsFast>>().await()
            }

            val usage = async { repository.storageUsage() }
            advanceTimeBy(REQUEST_TIMEOUT)
            runCurrent()

            assertEquals(Outcome.Failure(DomainError.Network), usage.await())
        }

    @Test
    fun `clearing the cache deletes every downloaded file, however new, and returns what was freed`() =
        runTest {
            client.on(TdApi.OptimizeStorage::class, TdResult.Success(deleted(size = 1_234_567)))

            assertEquals(Outcome.Success(1_234_567L), repository.clearCache())

            val request = client.sentOf<TdApi.OptimizeStorage>().single()
            assertEquals(
                listOf(0L, 0L, 0L, 0L),
                listOf(request.size, request.ttl.toLong(), request.count.toLong(), request.immunityDelay.toLong()),
            )
            assertTrue(request.returnDeletedFileStatistics)
            assertArrayEquals(longArrayOf(), request.chatIds)
            assertArrayEquals(longArrayOf(), request.excludeChatIds)
            assertEquals(0, request.chatLimit)
        }

    @Test
    fun `clearing the cache deletes downloaded media and documents only`() =
        runTest {
            client.on(TdApi.OptimizeStorage::class, TdResult.Success(deleted(size = 0)))

            repository.clearCache()

            assertEquals(
                setOf(
                    TdApi.FileTypePhoto.CONSTRUCTOR,
                    TdApi.FileTypeLivePhotoVideo.CONSTRUCTOR,
                    TdApi.FileTypeVideo.CONSTRUCTOR,
                    TdApi.FileTypeAnimation.CONSTRUCTOR,
                    TdApi.FileTypeVoiceNote.CONSTRUCTOR,
                    TdApi.FileTypeVideoNote.CONSTRUCTOR,
                    TdApi.FileTypeAudio.CONSTRUCTOR,
                    TdApi.FileTypeDocument.CONSTRUCTOR,
                    TdApi.FileTypePhotoStory.CONSTRUCTOR,
                    TdApi.FileTypeVideoStory.CONSTRUCTOR,
                ),
                client
                    .sentOf<TdApi.OptimizeStorage>()
                    .single()
                    .fileTypes
                    .map { it.constructor }
                    .toSet(),
            )
        }

    @Test
    fun `clearing the cache never deletes secret chats' files, Telegram Passport files or view-once media`() =
        runTest {
            client.on(TdApi.OptimizeStorage::class, TdResult.Success(deleted(size = 0)))

            repository.clearCache()

            val types =
                client
                    .sentOf<TdApi.OptimizeStorage>()
                    .single()
                    .fileTypes
                    .map { it.constructor }
            // An empty list would mean every type but TDLib's defaults, these included.
            assertTrue(types.isNotEmpty())
            val kept =
                listOf(
                    TdApi.FileTypeSecret.CONSTRUCTOR,
                    TdApi.FileTypeSecretThumbnail.CONSTRUCTOR,
                    TdApi.FileTypeSecure.CONSTRUCTOR,
                    TdApi.FileTypeSelfDestructingPhoto.CONSTRUCTOR,
                    TdApi.FileTypeSelfDestructingVideo.CONSTRUCTOR,
                    TdApi.FileTypeSelfDestructingVideoNote.CONSTRUCTOR,
                    TdApi.FileTypeSelfDestructingVoiceNote.CONSTRUCTOR,
                    TdApi.FileTypeSelfDestructingLivePhotoVideo.CONSTRUCTOR,
                    TdApi.FileTypeUnknown.CONSTRUCTOR,
                )
            assertTrue(kept.none { it in types })
        }

    @Test
    fun `each clearing sends its own file types`() =
        runTest {
            client.on(TdApi.OptimizeStorage::class, TdResult.Success(deleted(size = 0)))

            repository.clearCache()
            repository.clearCache()

            val (first, second) = client.sentOf<TdApi.OptimizeStorage>()
            assertTrue(first.fileTypes !== second.fileTypes)
        }

    @Test
    fun `clearing the cache maps errors`() =
        runTest {
            client.on(TdApi.OptimizeStorage::class, TdResult.Failure(TdError(400, "Request aborted")))

            assertEquals(Outcome.Failure(DomainError.Unknown(400, "Request aborted")), repository.clearCache())
        }

    /** Answers getChats of each list with [ids] and getChat with [chats] by id. */
    private fun chats(
        main: LongArray,
        archive: LongArray,
        chats: Map<Long, TdApi.Chat>,
    ) {
        client.on(TdApi.GetChats::class) { request ->
            val ids = if (request.chatList is TdApi.ChatListArchive) archive else main
            TdResult.Success(TdApi.Chats(ids.size, ids))
        }
        client.on(TdApi.GetChat::class) { request ->
            chats[request.chatId]?.let { TdResult.Success(it) } ?: TdResult.Failure(TdError(400, "Chat not found"))
        }
    }

    private fun pinnedIn(
        id: Long,
        list: TdApi.ChatList,
        pinned: Boolean = true,
    ) = TdApiFixtures.chat(id = id, positions = arrayOf(TdApiFixtures.chatPosition(list = list, isPinned = pinned)))

    @Test
    fun `the weekly clean-up keeps the pinned chats' files, deletes what is old, and keeps 4 GB at most`() =
        runTest {
            chats(
                main = longArrayOf(1, 2, 3),
                archive = longArrayOf(7, 8),
                chats =
                    mapOf(
                        1L to pinnedIn(1, TdApi.ChatListMain()),
                        2L to pinnedIn(2, TdApi.ChatListMain()),
                        3L to pinnedIn(3, TdApi.ChatListMain(), pinned = false),
                        7L to pinnedIn(7, TdApi.ChatListArchive()),
                        8L to pinnedIn(8, TdApi.ChatListArchive(), pinned = false),
                    ),
            )
            client.on(TdApi.OptimizeStorage::class, TdResult.Success(deleted(size = 2_000)))

            assertEquals(Outcome.Success(2_000L), repository.optimizeStorage())

            val request = client.sentOf<TdApi.OptimizeStorage>().single()
            assertArrayEquals(longArrayOf(1, 2, 7), request.excludeChatIds)
            assertArrayEquals(longArrayOf(), request.chatIds)
            assertEquals(4L * 1024 * 1024 * 1024, request.size)
            assertEquals(30 * 24 * 60 * 60, request.ttl)
            assertEquals(-1, request.immunityDelay)
            assertEquals(
                cacheFileTypes().map { it.constructor },
                request.fileTypes.map { it.constructor },
            )
            // The list ends at its first chat that is not pinned: no chat after it is asked for.
            assertEquals(listOf(1L, 2L, 3L, 7L, 8L), client.sentOf<TdApi.GetChat>().map { it.chatId })
        }

    @Test
    fun `a chat pinned in another list only does not count as pinned in this one`() =
        runTest {
            chats(
                main = longArrayOf(4),
                archive = longArrayOf(),
                chats = mapOf(4L to pinnedIn(4, TdApi.ChatListFolder(3))),
            )
            client.on(TdApi.OptimizeStorage::class, TdResult.Success(deleted(size = 0)))

            repository.optimizeStorage()

            assertArrayEquals(longArrayOf(), client.sentOf<TdApi.OptimizeStorage>().single().excludeChatIds)
        }

    @Test
    fun `without the pinned chats the weekly clean-up deletes nothing`() =
        runTest {
            chats(main = longArrayOf(1), archive = longArrayOf(), chats = emptyMap())

            assertTrue(repository.optimizeStorage() is Outcome.Failure)
            assertTrue(client.sentOf<TdApi.OptimizeStorage>().isEmpty())
        }
}
