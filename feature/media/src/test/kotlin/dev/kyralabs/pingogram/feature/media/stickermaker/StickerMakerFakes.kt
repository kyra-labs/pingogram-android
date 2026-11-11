package dev.kyralabs.pingogram.feature.media.stickermaker

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.EmojiCategory
import dev.kyralabs.pingogram.core.domain.model.EmojiGroup
import dev.kyralabs.pingogram.core.domain.model.StickerDraft
import dev.kyralabs.pingogram.core.domain.model.StickerSetNameCheck
import dev.kyralabs.pingogram.core.domain.model.StickerSetSummary
import dev.kyralabs.pingogram.core.domain.repository.EmojiCatalogRepository
import dev.kyralabs.pingogram.core.domain.repository.StickerMakerRepository
import dev.kyralabs.pingogram.core.domain.repository.StickerSetCreationRepository
import kotlinx.collections.immutable.persistentListOf

/** The user's own sets ([sets]), writing drawings ([written], one path each) and what was added ([added]). */
class FakeStickerMakerRepository : StickerMakerRepository {
    var sets: Outcome<List<StickerSetSummary>> = Outcome.Success(emptyList())
    var prepared: Outcome<String> = Outcome.Success(STICKER_PATH)
    var addResult: Outcome<Unit> = Outcome.Success(Unit)
    val written = mutableListOf<ByteArray>()
    val discarded = mutableListOf<String>()
    val added = mutableListOf<Pair<String, StickerDraft>>()

    override suspend fun ownedSets() = sets

    override suspend fun prepareSticker(image: ByteArray): Outcome<String> {
        written += image
        return prepared
    }

    override fun discardSticker(path: String) {
        discarded += path
    }

    override suspend fun addSticker(
        setName: String,
        sticker: StickerDraft,
    ): Outcome<Unit> {
        added += setName to sticker
        return addResult
    }

    companion object {
        const val STICKER_PATH = "/uploads/1/sticker.webp"

        fun set(
            name: String,
            title: String,
            size: Int = 12,
        ) = StickerSetSummary(id = name.hashCode().toLong(), title = title, size = size, cover = null, name = name)
    }
}

/** A new set: Telegram's suggestion is the title's words joined, every name is free unless [occupied]. */
class FakeStickerSetCreationRepository : StickerSetCreationRepository {
    val occupied = mutableSetOf<String>()
    var createResult: Outcome<Long> = Outcome.Success(SET_ID)
    val created = mutableListOf<Triple<String, String, List<StickerDraft>>>()
    val checked = mutableListOf<String>()

    override suspend fun suggestName(title: String) =
        Outcome.Success(title.trim().lowercase().replace(' ', '_') + "_kl")

    override suspend fun checkName(name: String): Outcome<StickerSetNameCheck> {
        checked += name
        return Outcome.Success(if (name in occupied) StickerSetNameCheck.Occupied else StickerSetNameCheck.Available)
    }

    override suspend fun prepareSticker(uri: String): Outcome<String> = error("The sticker maker draws its sticker")

    override fun discardSticker(path: String) = Unit

    override suspend fun create(
        title: String,
        name: String,
        stickers: List<StickerDraft>,
    ): Outcome<Long> {
        created += Triple(title, name, stickers)
        return createResult
    }

    companion object {
        const val SET_ID = 77L
    }
}

/** Two groups of emoji. */
class FakeEmojiCatalogRepository : EmojiCatalogRepository {
    var reads = 0

    override suspend fun catalog(): List<EmojiGroup> {
        reads++
        return listOf(
            EmojiGroup(EmojiCategory.SmileysAndEmotion, persistentListOf("😀", "😂")),
            EmojiGroup(EmojiCategory.AnimalsAndNature, persistentListOf("😺")),
        )
    }
}
