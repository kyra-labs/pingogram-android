package dev.kyralabs.pingogram.feature.chat.ui.panel

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.EmojiGroup
import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.Sticker
import dev.kyralabs.pingogram.core.domain.model.StickerFormat
import dev.kyralabs.pingogram.core.domain.model.StickerSetSummary
import dev.kyralabs.pingogram.core.domain.repository.EmojiCatalogRepository
import dev.kyralabs.pingogram.core.domain.repository.RecentEmojiRepository
import dev.kyralabs.pingogram.core.domain.repository.StickerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** A sticker of set [setId] with file [fileId]. */
internal fun sticker(
    fileId: Int,
    setId: Long = 0,
    emoji: String = "🦆",
) = Sticker(
    setId = setId,
    width = 512,
    height = 512,
    emoji = emoji,
    format = StickerFormat.Tgs,
    thumbnail = null,
    file = FileRef(fileId, 20_000),
)

internal class FakeEmojiCatalogRepository(
    var groups: List<EmojiGroup> = emptyList(),
) : EmojiCatalogRepository {
    var reads = 0

    override suspend fun catalog(): List<EmojiGroup> {
        reads++
        return groups
    }
}

internal class FakeRecentEmojiRepository : RecentEmojiRepository {
    override val recentEmoji = MutableStateFlow<List<String>>(emptyList())
    val added = mutableListOf<String>()

    override suspend fun addRecent(emoji: String): Outcome<Unit> {
        added += emoji
        recentEmoji.value = listOf(emoji) + (recentEmoji.value - emoji)
        return Outcome.Success(Unit)
    }
}

/**
 * Sticker lists as state flows the test sets, as TDLib's updates would change them, and set contents by id
 * ([setContents]; a missing id fails).
 */
internal class FakeStickerRepository : StickerRepository {
    val sets = MutableStateFlow<Outcome<List<StickerSetSummary>>>(Outcome.Success(emptyList()))
    val favorites = MutableStateFlow<Outcome<List<Sticker>>>(Outcome.Success(emptyList()))
    val recent = MutableStateFlow<Outcome<List<Sticker>>>(Outcome.Success(emptyList()))
    val setContents = mutableMapOf<Long, Outcome<List<Sticker>>>()

    /** Every set asked for, in order. */
    val setRequests = mutableListOf<Long>()
    var installedSetsCollections = 0

    override fun installedSets(): Flow<Outcome<List<StickerSetSummary>>> {
        installedSetsCollections++
        return sets
    }

    override fun recentStickers(): Flow<Outcome<List<Sticker>>> = recent

    override fun favoriteStickers(): Flow<Outcome<List<Sticker>>> = favorites

    override suspend fun stickerSet(setId: Long): Outcome<List<Sticker>> {
        setRequests += setId
        return setContents[setId] ?: Outcome.Failure(DomainError.NotFound)
    }
}
