package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.StickerDraft
import dev.kyralabs.pingogram.core.domain.model.StickerSetSummary

/**
 * Making a sticker from a photo (boards 14a, 14b): the image the editor drew written as a sticker on the device, the
 * user's own sticker sets it can join (td_api.tl `getOwnedStickerSets`), and adding it to one (`addStickerToSet`). A
 * new set for it is `StickerSetCreationRepository.create`.
 */
interface StickerMakerRepository {
    /**
     * The user's own sets of stickers that can take one more: not custom emoji or masks, fewer than
     * `StickerSetLimits.STICKER_COUNT_MAX` stickers; the first 100, newest first as Telegram lists them.
     */
    suspend fun ownedSets(): Outcome<List<StickerSetSummary>>

    /**
     * Writes [image], the editor's drawing encoded as a PNG, into the upload cache as a sticker: its longer side
     * `StickerSetLimits.STICKER_SIDE_PX`, transparency kept, as WebP. Its path; `DomainError.PhotoInvalid` for bytes
     * Android cannot decode as an image.
     */
    suspend fun prepareSticker(image: ByteArray): Outcome<String>

    /** Deletes [path], a copy [prepareSticker] made, once nothing will use it. */
    fun discardSticker(path: String)

    /** Adds [sticker] at the end of the user's set of link name [setName]. The copy is the caller's to discard. */
    suspend fun addSticker(
        setName: String,
        sticker: StickerDraft,
    ): Outcome<Unit>
}
