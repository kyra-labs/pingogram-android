package dev.kyralabs.pingogram.core.demo

import org.drinkless.tdlib.TdApi

/**
 * The demo's stickers (`docs/adr/0034-demo-mode-for-store-review.md`): the sample sets ([DemoData.stickerSets]: "Pings"
 * installed, "Ring shapes" trending), drawn on the phone, which the panel, Settings → Stickers and a set's sheet show,
 * install, archive, remove and reorder. It keeps the sets' state itself; their changes go into the journal like every
 * other change. No custom emoji sets: the demo's texts have none.
 *
 * One branch per function: hence the suppression.
 */
@Suppress("CyclomaticComplexMethod", "LongMethod")
internal class DemoStickers : DemoArea {
    private var installed: MutableList<Long>? = null
    private val archived = mutableListOf<Long>()

    override fun answer(
        world: DemoWorld,
        function: TdApi.Function<*>,
    ): DemoAnswer? {
        val installed =
            installed
                ?: world.data.stickerSets
                    .filter { it.isInstalled }
                    .map { it.id }
                    .toMutableList()
                    .also { installed = it }
        return when (function) {
            is TdApi.GetInstalledStickerSets -> {
                ok(sets(world, if (function.stickerType is TdApi.StickerTypeRegular) installed else emptyList()))
            }

            is TdApi.GetArchivedStickerSets -> {
                ok(sets(world, if (function.stickerType is TdApi.StickerTypeRegular) archived else emptyList()))
            }

            is TdApi.GetTrendingStickerSets -> {
                val trending =
                    if (function.stickerType is TdApi.StickerTypeRegular && function.offset == 0) {
                        world.data.stickerSets
                            .map { it.id }
                            .filter { it !in installed && it !in archived }
                    } else {
                        emptyList()
                    }
                ok(
                    TdApi.TrendingStickerSets(
                        trending.size,
                        trending.mapNotNull { info(world, it) }.toTypedArray(),
                        false,
                    ),
                )
            }

            is TdApi.GetStickerSet -> {
                set(world, function.setId)?.let(::ok) ?: notFound()
            }

            is TdApi.SearchStickerSet -> {
                world.data.stickerSets.firstOrNull { it.name.equals(function.name, ignoreCase = true) }?.let {
                    ok(set(world, it.id)!!)
                } ?: notFound()
            }

            is TdApi.ChangeStickerSet -> {
                if (set(world, function.setId) == null) return notFound()
                installed.remove(function.setId)
                archived.remove(function.setId)
                when {
                    function.isArchived -> archived.add(0, function.setId)
                    function.isInstalled -> installed.add(0, function.setId)
                }
                okChanged(listOf(TdApi.UpdateInstalledStickerSets(TdApi.StickerTypeRegular(), installed.toLongArray())))
            }

            is TdApi.ReorderInstalledStickerSets -> {
                val order = function.stickerSetIds.toList().filter { it in installed }
                installed.removeAll(order)
                installed.addAll(0, order)
                okChanged(listOf(TdApi.UpdateInstalledStickerSets(TdApi.StickerTypeRegular(), installed.toLongArray())))
            }

            is TdApi.ViewTrendingStickerSets -> {
                ok(TdApi.Ok())
            }

            // The experimental look's "Wave" greets with the first sample set's stickers.
            is TdApi.GetGreetingStickers -> {
                val first =
                    world.data.stickerSets
                        .firstOrNull()
                        ?.let { set(world, it.id) }
                ok(TdApi.Stickers(first?.stickers ?: emptyArray()))
            }

            is TdApi.GetRecentStickers, is TdApi.GetFavoriteStickers -> {
                ok(TdApi.Stickers(emptyArray()))
            }

            // No saved GIFs: the GIF tab shows its empty state.
            is TdApi.GetSavedAnimations -> {
                ok(TdApi.Animations(emptyArray()))
            }

            else -> {
                null
            }
        }
    }

    private fun set(
        world: DemoWorld,
        id: Long,
    ): TdApi.StickerSet? {
        val sample = world.data.stickerSets.firstOrNull { it.id == id } ?: return null
        return DemoCodec.decode<TdApi.StickerSet>(DemoCodec.encode(sample)).apply {
            isInstalled = id in installed.orEmpty()
            isArchived = id in archived
            isViewed = true
        }
    }

    private fun info(
        world: DemoWorld,
        id: Long,
    ): TdApi.StickerSetInfo? {
        val set = set(world, id) ?: return null
        return TdApi.StickerSetInfo(
            set.id,
            set.title,
            set.name,
            null,
            null,
            false,
            set.isInstalled,
            set.isArchived,
            false,
            set.stickerType,
            false,
            false,
            set.isViewed,
            set.stickers.size,
            set.stickers.take(COVERS).toTypedArray(),
        )
    }

    private fun sets(
        world: DemoWorld,
        ids: List<Long>,
    ) = TdApi.StickerSets(ids.size, ids.mapNotNull { info(world, it) }.toTypedArray())

    private companion object {
        const val COVERS = 4
    }
}
