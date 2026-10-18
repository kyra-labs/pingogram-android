package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.StorageUsage

/**
 * Pingogram's storage on this device (`REQUIREMENTS.md` FR-S1, Data and storage): how much space TDLib's files and
 * databases take, and clearing the cache of downloaded files. TDLib keeps both, so nothing is cached here; each call
 * asks TDLib. A request that gets no answer in time fails with `DomainError.Network`.
 */
interface StorageRepository {
    /** How much space TDLib's files and databases take now, from its quick, approximate statistics. */
    suspend fun storageUsage(): Outcome<StorageUsage>

    /**
     * Deletes the downloaded files that download again when they are opened: photos, videos, GIFs, voice and video
     * messages, music, documents and stories, however recently they were downloaded. Never the databases (chats and
     * messages stay), secret chats' files (which cannot be downloaded again), Telegram Passport files, view-once
     * media, or the small files TDLib keeps by default (thumbnails, profile photos, stickers, wallpapers, notification
     * sounds).
     *
     * @return how many bytes were freed.
     */
    suspend fun clearCache(): Outcome<Long>
}
