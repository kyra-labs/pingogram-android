package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.mapper.toSticker
import dev.kyralabs.pingogram.core.domain.model.Sticker
import dev.kyralabs.pingogram.core.domain.model.StickerSetSummary
import dev.kyralabs.pingogram.core.domain.repository.StickerRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onSubscription
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [StickerRepository] on TDLib's sticker lists: `getInstalledStickerSets`, `getRecentStickers` (sent, not attached to
 * photos) and `getFavoriteStickers`, each read again on its update (`updateInstalledStickerSets`,
 * `updateRecentStickers`, `updateFavoriteStickers`), which carry only ids; and `getStickerSet` for a set's stickers.
 * TDLib keeps all of them in its database and answers from it, so reading again costs little. No cache of its own:
 * the panel reads the lists only while it shows.
 */
@Singleton
class TdlibStickerRepository
    @Inject
    constructor(
        private val client: TdlibClient,
    ) : StickerRepository {
        override fun installedSets(): Flow<Outcome<List<StickerSetSummary>>> =
            client.readOn({ it is TdApi.UpdateInstalledStickerSets && it.stickerType is TdApi.StickerTypeRegular }) {
                client.request(TdApi.GetInstalledStickerSets(TdApi.StickerTypeRegular())).map { result ->
                    result.sets.orEmpty().map { it.toStickerSetSummary() }
                }
            }

        override fun recentStickers(): Flow<Outcome<List<Sticker>>> =
            client.readOn({ it is TdApi.UpdateRecentStickers && !it.isAttached }) {
                client.request(TdApi.GetRecentStickers(false)).map { it.toStickers() }
            }

        override fun favoriteStickers(): Flow<Outcome<List<Sticker>>> =
            client.readOn({ it is TdApi.UpdateFavoriteStickers }) {
                client.request(TdApi.GetFavoriteStickers()).map { it.toStickers() }
            }

        override suspend fun stickerSet(setId: Long): Outcome<List<Sticker>> =
            client.request(TdApi.GetStickerSet(setId)).map { set ->
                set.stickers.orEmpty().mapNotNull { it.toSticker() }
            }

        private fun TdApi.Stickers.toStickers(): List<Sticker> = stickers.orEmpty().mapNotNull { it.toSticker() }
    }

/** A sticker set as the panel and Settings → Stickers show it: its first cover stands for it. */
internal fun TdApi.StickerSetInfo.toStickerSetSummary() =
    StickerSetSummary(
        id = id,
        title = title.orEmpty(),
        size = size,
        cover = covers?.firstOrNull()?.toSticker(),
        name = name.orEmpty(),
        isInstalled = isInstalled,
        isArchived = isArchived,
        isOfficial = isOfficial,
        isViewed = isViewed,
    )

/**
 * [read] once subscribed to [TdlibClient.updates], so no change in between is missed, then again on every update that
 * [changes] says is about it. Updates that come while a read is under way wait for it (TDLib's updates are buffered),
 * so each read sees the list as it is after them.
 */
internal fun <T> TdlibClient.readOn(
    changes: (TdApi.Update) -> Boolean,
    read: suspend () -> Outcome<T>,
): Flow<Outcome<T>> =
    updates
        .onSubscription { emit(ReadNow) }
        .filter { it === ReadNow || changes(it) }
        .map { read() }

/** Marks the point of subscription in [readOn]: the first read; never sent by TDLib. */
private object ReadNow : TdApi.Update() {
    override fun getConstructor() = 0
}
