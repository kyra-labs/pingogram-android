package dev.kyralabs.pingogram.core.domain.model

/**
 * How much space Pingogram takes on this device (`REQUIREMENTS.md` FR-S1, Data and storage), from TDLib's quick,
 * approximate statistics (`StorageRepository`).
 *
 * @property filesSize every file TDLib keeps, in bytes: downloaded media and documents, thumbnails, profile photos,
 *   stickers, secret chats' files.
 * @property fileCount how many files [filesSize] counts.
 * @property databaseSize TDLib's databases (chats, messages, users, settings, language pack), in bytes.
 */
data class StorageUsage(
    val filesSize: Long,
    val fileCount: Int,
    val databaseSize: Long,
)
