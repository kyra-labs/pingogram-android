package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.mapper.toSticker
import dev.kyralabs.pingogram.core.domain.model.StickerSetDetails
import dev.kyralabs.pingogram.core.domain.model.StickerSetKind
import dev.kyralabs.pingogram.core.domain.model.StickerSetSummary
import dev.kyralabs.pingogram.core.domain.repository.StickerSetRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.coroutines.flow.Flow
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [StickerSetRepository] on TDLib: `getInstalledStickerSets`, `getArchivedStickerSets` and `getTrendingStickerSets` of
 * the kind's `StickerType` (regular or custom emoji) read again on its `updateInstalledStickerSets` (installing and
 * archiving move sets between the lists) and `updateTrendingStickerSets`; `getStickerSet` again on `updateStickerSet`
 * of that set and on any installed sets changing; `searchStickerSet` for a link's set; `changeStickerSet`,
 * `reorderInstalledStickerSets` and `viewTrendingStickerSets` for the changes. TDLib keeps all of it in its database,
 * so no cache of its own.
 */
@Singleton
class TdlibStickerSetRepository
    @Inject
    constructor(
        private val client: TdlibClient,
    ) : StickerSetRepository {
        override fun installedSets(kind: StickerSetKind): Flow<Outcome<List<StickerSetSummary>>> =
            client.readOn({ it.changesInstalledSets(kind) }) {
                client.request(TdApi.GetInstalledStickerSets(kind.toStickerType())).map { result ->
                    result.sets.orEmpty().map { it.toStickerSetSummary() }
                }
            }

        override fun archivedSets(kind: StickerSetKind): Flow<Outcome<List<StickerSetSummary>>> =
            client.readOn({ it.changesInstalledSets(kind) }) {
                client.request(TdApi.GetArchivedStickerSets(kind.toStickerType(), 0, PAGE_SIZE)).map { result ->
                    result.sets.orEmpty().map { it.toStickerSetSummary() }
                }
            }

        override fun trendingSets(kind: StickerSetKind): Flow<Outcome<List<StickerSetSummary>>> =
            client.readOn({ it.changesInstalledSets(kind) || it.changesTrendingSets(kind) }) {
                client.request(TdApi.GetTrendingStickerSets(kind.toStickerType(), 0, PAGE_SIZE)).map { result ->
                    result.sets.orEmpty().map { it.toStickerSetSummary() }
                }
            }

        override fun stickerSet(setId: Long): Flow<Outcome<StickerSetDetails>> =
            client.readOn(
                {
                    it is TdApi.UpdateInstalledStickerSets ||
                        (it is TdApi.UpdateStickerSet && it.stickerSet?.id == setId)
                },
            ) {
                client.request(TdApi.GetStickerSet(setId)).map { it.toDetails() }
            }

        override suspend fun findSet(name: String): Outcome<Long> =
            client.request(TdApi.SearchStickerSet(name, false)).map { it.id }

        override suspend fun install(setId: Long): Outcome<Unit> =
            client.changeStickerSet(setId, installed = true, archived = false)

        override suspend fun archive(setId: Long): Outcome<Unit> =
            client.changeStickerSet(setId, installed = false, archived = true)

        override suspend fun remove(setId: Long): Outcome<Unit> =
            client.changeStickerSet(setId, installed = false, archived = false)

        override suspend fun reorder(
            setIds: List<Long>,
            kind: StickerSetKind,
        ): Outcome<Unit> =
            client
                .request(TdApi.ReorderInstalledStickerSets(kind.toStickerType(), setIds.toLongArray()))
                .map { }

        override suspend fun markTrendingViewed(setIds: List<Long>): Outcome<Unit> =
            if (setIds.isEmpty()) {
                Outcome.Success(Unit)
            } else {
                client.request(TdApi.ViewTrendingStickerSets(setIds.toLongArray())).map { }
            }

        private companion object {
            /** The most sets TDLib returns at once from the archived and the trending lists. */
            const val PAGE_SIZE = 100
        }
    }

/** Sets set [setId]'s two flags: installed, archived, or neither (removed). */
private suspend fun TdlibClient.changeStickerSet(
    setId: Long,
    installed: Boolean,
    archived: Boolean,
): Outcome<Unit> = request(TdApi.ChangeStickerSet(setId, installed, archived)).map { }

/** td_api.tl's sticker type of the sets of [StickerSetKind]. */
private fun StickerSetKind.toStickerType(): TdApi.StickerType =
    when (this) {
        StickerSetKind.Stickers -> TdApi.StickerTypeRegular()
        StickerSetKind.Emoji -> TdApi.StickerTypeCustomEmoji()
    }

private fun TdApi.StickerType?.isOf(kind: StickerSetKind): Boolean =
    when (kind) {
        StickerSetKind.Stickers -> this is TdApi.StickerTypeRegular
        StickerSetKind.Emoji -> this is TdApi.StickerTypeCustomEmoji
    }

private fun TdApi.Update.changesInstalledSets(kind: StickerSetKind): Boolean =
    this is TdApi.UpdateInstalledStickerSets && stickerType.isOf(kind)

private fun TdApi.Update.changesTrendingSets(kind: StickerSetKind): Boolean =
    this is TdApi.UpdateTrendingStickerSets && stickerType.isOf(kind)

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
                kind = stickerType.toStickerSetKind(),
            ),
        stickers = stickers,
    )
}
