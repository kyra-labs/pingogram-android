package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.mapper.toSticker
import dev.kyralabs.pingogram.core.domain.model.StickerSetDetails
import dev.kyralabs.pingogram.core.domain.model.StickerSetSummary
import dev.kyralabs.pingogram.core.domain.repository.StickerSetRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.coroutines.flow.Flow
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [StickerSetRepository] on TDLib: `getArchivedStickerSets` and `getTrendingStickerSets` read again on
 * `updateInstalledStickerSets` (installing and archiving move sets between the lists) and `updateTrendingStickerSets`;
 * `getStickerSet` again on `updateStickerSet` of that set and on the installed sets changing; `searchStickerSet` for
 * a link's set; `changeStickerSet`, `reorderInstalledStickerSets` and `viewTrendingStickerSets` for the changes. TDLib
 * keeps all of it in its database, so no cache of its own. Regular stickers only.
 */
@Singleton
class TdlibStickerSetRepository
    @Inject
    constructor(
        private val client: TdlibClient,
    ) : StickerSetRepository {
        override fun archivedSets(): Flow<Outcome<List<StickerSetSummary>>> =
            client.readOn(::changesInstalledSets) {
                client.request(TdApi.GetArchivedStickerSets(TdApi.StickerTypeRegular(), 0, PAGE_SIZE)).map { result ->
                    result.sets.orEmpty().map { it.toStickerSetSummary() }
                }
            }

        override fun trendingSets(): Flow<Outcome<List<StickerSetSummary>>> =
            client.readOn({ changesInstalledSets(it) || it is TdApi.UpdateTrendingStickerSets && it.isRegular() }) {
                client.request(TdApi.GetTrendingStickerSets(TdApi.StickerTypeRegular(), 0, PAGE_SIZE)).map { result ->
                    result.sets.orEmpty().map { it.toStickerSetSummary() }
                }
            }

        override fun stickerSet(setId: Long): Flow<Outcome<StickerSetDetails>> =
            client.readOn({ changesInstalledSets(it) || it is TdApi.UpdateStickerSet && it.stickerSet?.id == setId }) {
                client.request(TdApi.GetStickerSet(setId)).map { it.toDetails() }
            }

        override suspend fun findSet(name: String): Outcome<Long> =
            client.request(TdApi.SearchStickerSet(name, false)).map { it.id }

        override suspend fun install(setId: Long): Outcome<Unit> = change(setId, installed = true, archived = false)

        override suspend fun archive(setId: Long): Outcome<Unit> = change(setId, installed = false, archived = true)

        override suspend fun remove(setId: Long): Outcome<Unit> = change(setId, installed = false, archived = false)

        override suspend fun reorder(setIds: List<Long>): Outcome<Unit> =
            client
                .request(TdApi.ReorderInstalledStickerSets(TdApi.StickerTypeRegular(), setIds.toLongArray()))
                .map { }

        override suspend fun markTrendingViewed(setIds: List<Long>): Outcome<Unit> =
            if (setIds.isEmpty()) {
                Outcome.Success(Unit)
            } else {
                client.request(TdApi.ViewTrendingStickerSets(setIds.toLongArray())).map { }
            }

        private suspend fun change(
            setId: Long,
            installed: Boolean,
            archived: Boolean,
        ): Outcome<Unit> = client.request(TdApi.ChangeStickerSet(setId, installed, archived)).map { }

        private fun changesInstalledSets(update: TdApi.Update): Boolean =
            update is TdApi.UpdateInstalledStickerSets && update.stickerType is TdApi.StickerTypeRegular

        private fun TdApi.UpdateTrendingStickerSets.isRegular() = stickerType is TdApi.StickerTypeRegular

        private fun TdApi.StickerSet.toDetails(): StickerSetDetails {
            val stickers = stickers.orEmpty().mapNotNull { it.toSticker() }
            return StickerSetDetails(
                set =
                    StickerSetSummary(
                        id = id,
                        title = title.orEmpty(),
                        size = stickers.size,
                        cover = stickers.firstOrNull(),
                        name = name.orEmpty(),
                        isInstalled = isInstalled,
                        isArchived = isArchived,
                        isOfficial = isOfficial,
                        isViewed = isViewed,
                    ),
                stickers = stickers,
            )
        }

        private companion object {
            /** The most sets TDLib returns at once from the archived and the trending lists. */
            const val PAGE_SIZE = 100
        }
    }
