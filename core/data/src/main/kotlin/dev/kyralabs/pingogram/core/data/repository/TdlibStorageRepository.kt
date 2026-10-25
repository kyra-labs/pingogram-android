package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.domain.model.StorageUsage
import dev.kyralabs.pingogram.core.domain.repository.StorageRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import org.drinkless.tdlib.TdApi
import javax.inject.Inject

/**
 * [StorageRepository] on TDLib's storage statistics (`getStorageStatisticsFast`) and its file garbage collector
 * (`optimizeStorage`). Both work on this device's files only, without the network.
 */
@AccountScoped
class TdlibStorageRepository
    @Inject
    constructor(
        private val client: TdlibClient,
    ) : StorageRepository {
        override suspend fun storageUsage(): Outcome<StorageUsage> =
            client.request(TdApi.GetStorageStatisticsFast()).map { it.toStorageUsage() }

        // With returnDeletedFileStatistics, TDLib answers with what it deleted instead of what is left.
        override suspend fun clearCache(): Outcome<Long> = client.request(clearCacheRequest()).map { it.size }
    }

internal fun TdApi.StorageStatisticsFast.toStorageUsage() =
    StorageUsage(
        filesSize = filesSize.coerceAtLeast(0),
        fileCount = fileCount.coerceAtLeast(0),
        databaseSize = (databaseSize + languagePackDatabaseSize).coerceAtLeast(0),
    )

/**
 * Deletes every file of [cacheFileTypes], however recently it was used or downloaded (td_api.tl `optimizeStorage`
 * of TDLib 1.8.68): no size, age or count may remain, and no file is immune for being new. Files of other types stay:
 * with a list of file types, TDLib considers only those (td/telegram/files/FileGcWorker.cpp). Without one it would
 * consider every type but thumbnails, profile photos, stickers, wallpapers and notification sounds, secret chats' files
 * and Telegram Passport files included, which never download again.
 */
internal fun clearCacheRequest() =
    TdApi.OptimizeStorage(
        // size, ttl, count: the limits on what remains after deletion
        0,
        0,
        0,
        // immunityDelay: how long a new file is immune, an hour by default
        0,
        cacheFileTypes(),
        // chatIds, excludeChatIds: every chat, and the files of no chat
        longArrayOf(),
        longArrayOf(),
        // returnDeletedFileStatistics
        true,
        // chatLimit: no statistics by chat
        0,
    )

/**
 * The files that "Clear cache" deletes: what downloads again when it is opened. TDLib's defaults without the types it
 * keeps (thumbnails, profile photos, stickers, wallpapers, notification sounds), secret chats' files and their
 * thumbnails, Telegram Passport files, view-once media (whose views count) and files of unknown type. Documents include
 * call logs and documents sent as files, which TDLib counts as documents.
 *
 * A new array each time: TdApi objects are mutable.
 */
internal fun cacheFileTypes(): Array<TdApi.FileType> =
    arrayOf(
        TdApi.FileTypePhoto(),
        TdApi.FileTypeLivePhotoVideo(),
        TdApi.FileTypeVideo(),
        TdApi.FileTypeAnimation(),
        TdApi.FileTypeVoiceNote(),
        TdApi.FileTypeVideoNote(),
        TdApi.FileTypeAudio(),
        TdApi.FileTypeDocument(),
        TdApi.FileTypePhotoStory(),
        TdApi.FileTypeVideoStory(),
    )
