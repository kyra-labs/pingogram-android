package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.StickerDraft
import dev.kyralabs.pingogram.core.domain.model.StickerSetNameCheck

/**
 * Making a sticker set of the user's own (td_api.tl `createNewStickerSet`): its link name checked and suggested by
 * Telegram, each sticker an image prepared on the device from a picked photo, then the set published at once. Only
 * static stickers; Telegram makes the set public under `t.me/addstickers/<name>`.
 */
interface StickerSetCreationRepository {
    /** Telegram's suggestion of a link name for [title] (`getSuggestedStickerSetName`). */
    suspend fun suggestName(title: String): Outcome<String>

    /** Whether [name] is free for a new set (`checkStickerSetName`). */
    suspend fun checkName(name: String): Outcome<StickerSetNameCheck>

    /**
     * Copies the image at [uri] (the photo picker's content URI) into the upload cache as a sticker: upright, its
     * longer side `StickerSetLimits.STICKER_SIDE_PX`, transparency kept, as WebP. Its path; `DomainError.PhotoInvalid`
     * for what cannot be read as an image.
     */
    suspend fun prepareSticker(uri: String): Outcome<String>

    /** Deletes [path], a copy [prepareSticker] made, once nothing will use it. */
    fun discardSticker(path: String)

    /**
     * Publishes a set of [stickers], in that order, titled [title] under link name [name]: its id. The copies are the
     * caller's to discard once it answered.
     */
    suspend fun create(
        title: String,
        name: String,
        stickers: List<StickerDraft>,
    ): Outcome<Long>
}
