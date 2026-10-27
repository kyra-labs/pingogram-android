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

    /**
     * The weekly clean-up (`docs/PERFORMANCE.md` §3): deletes the files [clearCache] would delete that were not used
     * for 30 days, then the oldest of them until they take at most 4 GB. Files of chats pinned in the main list or the
     * archive stay, and so do files TDLib got in the last hour.
     *
     * @return how many bytes were freed.
     */
    suspend fun optimizeStorage(): Outcome<Long>
}
