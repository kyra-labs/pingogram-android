package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Sticker
import dev.kyralabs.pingogram.core.domain.model.StickerSetSummary
import kotlinx.coroutines.flow.Flow

/**
 * The stickers of the composer's sticker panel (`REQUIREMENTS.md` FR-C5): the user's installed sticker sets, the
 * stickers they sent recently and their favorites, as Telegram keeps them for the account (regular stickers only, not
 * masks or custom emoji). Sending one is `AttachmentRepository.sendSticker`, after which Telegram puts it first among
 * the recent ones.
 *
 * Each list is read when collected, then again whenever Telegram reports that it changed (another device installed a
 * set, a sticker was sent); a failed read emits its `Outcome.Failure` (`DomainError.Network` without an answer in time)
 * and the next change reads it again.
 */
interface StickerRepository {
    /** The installed sticker sets, in the user's order. */
    fun installedSets(): Flow<Outcome<List<StickerSetSummary>>>

    /** The stickers the user sent most recently, newest first. */
    fun recentStickers(): Flow<Outcome<List<Sticker>>>

    /** The stickers the user marked as favorites, in their order. */
    fun favoriteStickers(): Flow<Outcome<List<Sticker>>>

    /** The stickers of set [setId], in its order; stickers in formats this TDLib does not know are left out. */
    suspend fun stickerSet(setId: Long): Outcome<List<Sticker>>
}
