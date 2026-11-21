package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * The seed color of each photo wallpaper a conversation has shown or the user applied, by Telegram background id,
 * kept on this device and backed up (`docs/adr/0041-chat-colors-from-the-wallpaper.md`): a chat with that wallpaper
 * takes its colors from it, so opening the chat never analyses the image again.
 */
interface WallpaperSeedRepository {
    /** The seed (ARGB) kept for background [wallpaperId], null until one is, then every change. Never fails. */
    fun seed(wallpaperId: Long): Flow<Int?>

    /** Keeps [argb] as background [wallpaperId]'s seed; fails with `DomainError.Unknown` if it cannot be written. */
    suspend fun setSeed(
        wallpaperId: Long,
        argb: Int,
    ): Outcome<Unit>
}
