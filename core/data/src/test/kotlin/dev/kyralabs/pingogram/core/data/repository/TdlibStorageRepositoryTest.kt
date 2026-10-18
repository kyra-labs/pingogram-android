package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.StorageUsage
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
}
