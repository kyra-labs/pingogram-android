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

        override suspend fun optimizeStorage(): Outcome<Long> {
            val pinned = mutableListOf<Long>()
            for (list in listOf(TdApi.ChatListMain(), TdApi.ChatListArchive())) {
                when (val ids = pinnedChatIds(list)) {
                    is Outcome.Success -> pinned += ids.value

                    // Without the pinned chats, their files could go: try again later instead.
                    is Outcome.Failure -> return ids
                }
            }
            return client.request(optimizeStorageRequest(pinned.toLongArray())).map { it.size }
        }

        /**
         * The chats pinned in [list]: they come first in TDLib's order (`getChats` loads the list as far as it needs),
         * so the ids are read until the first chat that is not pinned there.
         */
        private suspend fun pinnedChatIds(list: TdApi.ChatList): Outcome<List<Long>> =
            when (val chats = client.request(TdApi.GetChats(list, PINNED_CHATS_MAX))) {
                is Outcome.Success -> leadingPinned(list, chats.value.chatIds)
                is Outcome.Failure -> chats
            }

        private suspend fun leadingPinned(
            list: TdApi.ChatList,
            ids: LongArray,
        ): Outcome<List<Long>> {
            val pinned = mutableListOf<Long>()
            for (id in ids) {
                when (val chat = client.request(TdApi.GetChat(id))) {
                    is Outcome.Success -> {
                        val isPinned =
                            chat.value.positions.any {
                                it.list.constructor == list.constructor && it.isPinned
                            }
                        if (!isPinned) break
                        pinned += id
                    }

                    is Outcome.Failure -> {
                        return chat
                    }
                }
            }
            return Outcome.Success(pinned)
        }

        private companion object {
            /** The most chats Telegram lets be pinned in a list: 100 in the archive with Telegram Premium. */
            const val PINNED_CHATS_MAX = 100
        }
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
 * The weekly clean-up's request ([TdlibStorageRepository.optimizeStorage]): of [cacheFileTypes], the files not used for
 * [CACHE_TTL_SECONDS] go, then the oldest until the rest take at most [CACHE_SIZE_LIMIT_BYTES] (td_api.tl
 * `optimizeStorage`: `ttl`, then `size`). TDLib's default immunity keeps the files of the last hour, and the files of
 * [keepChatIds] stay whatever their age.
 */
internal fun optimizeStorageRequest(keepChatIds: LongArray) =
    TdApi.OptimizeStorage(
        CACHE_SIZE_LIMIT_BYTES,
        CACHE_TTL_SECONDS,
        // count: TDLib's default limit on how many files remain
        -1,
        // immunityDelay: TDLib's default, an hour
        -1,
        cacheFileTypes(),
        // chatIds: every chat but keepChatIds
        longArrayOf(),
        keepChatIds,
        // returnDeletedFileStatistics
        true,
        // chatLimit: no statistics by chat
        0,
    )

/** How much the cache may take after the weekly clean-up: 4 GB (`docs/PERFORMANCE.md` §8). */
internal const val CACHE_SIZE_LIMIT_BYTES: Long = 4L * 1024 * 1024 * 1024

/** How long a file unused stays through the weekly clean-up: 30 days (`docs/PERFORMANCE.md` §3). */
internal const val CACHE_TTL_SECONDS: Int = 30 * 24 * 60 * 60

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
