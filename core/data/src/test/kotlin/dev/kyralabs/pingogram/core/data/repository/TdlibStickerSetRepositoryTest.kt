package dev.kyralabs.pingogram.core.data.repository

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.StickerSetKind
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** [TdlibStickerSetRepository]: Settings → Stickers' lists and changes. */
class TdlibStickerSetRepositoryTest {
    private val client = FakeTdlibClient()
    private val repository = TdlibStickerSetRepository(client)

    private fun setInfo(
        id: Long,
        title: String = "Set $id",
        installed: Boolean = false,
        archived: Boolean = false,
        viewed: Boolean = true,
        type: TdApi.StickerType = TdApi.StickerTypeRegular(),
    ) = TdApi.StickerSetInfo().apply {
        this.id = id
        this.title = title
        name = "set$id"
        size = 12
        covers = arrayOf(TdApiFixtures.sticker(setId = id))
        isInstalled = installed
        isArchived = archived
        isViewed = viewed
        stickerType = type
    }

    @Test
    fun `custom emoji sets are their own lists, read again only when the custom emoji sets change`() =
        runTest {
            var installed = listOf(setInfo(5, installed = true, type = TdApi.StickerTypeCustomEmoji()))
            client.on(TdApi.GetInstalledStickerSets::class) {
                TdResult.Success(TdApi.StickerSets(installed.size, installed.toTypedArray()))
            }

            repository.installedSets(StickerSetKind.Emoji).test {
                val first = (awaitItem() as Outcome.Success).value.single()
                assertEquals(5L, first.id)
                assertEquals(StickerSetKind.Emoji, first.kind)
                val request = client.sentOf<TdApi.GetInstalledStickerSets>().single()
                assertInstanceOf(TdApi.StickerTypeCustomEmoji::class.java, request.stickerType)

                installed = emptyList()
                client.emit(TdApi.UpdateInstalledStickerSets(TdApi.StickerTypeRegular(), longArrayOf()))
                client.emit(TdApi.UpdateInstalledStickerSets(TdApi.StickerTypeCustomEmoji(), longArrayOf()))
                assertEquals(emptyList<Any>(), (awaitItem() as Outcome.Success).value)
                expectNoEvents()
            }
        }

    @Test
    fun `reordering custom emoji sets sends their type`() =
        runTest {
            client.on(TdApi.ReorderInstalledStickerSets::class, TdResult.Success(TdApi.Ok()))

            repository.reorder(listOf(3L, 1L), StickerSetKind.Emoji)

            val request = client.sentOf<TdApi.ReorderInstalledStickerSets>().single()
            assertInstanceOf(TdApi.StickerTypeCustomEmoji::class.java, request.stickerType)
            assertEquals(listOf(3L, 1L), request.stickerSetIds.toList())
        }

    @Test
    fun `archived sets are Telegram's first 100 regular ones, read again when the installed sets change`() =
        runTest {
            var archived = listOf(setInfo(1, archived = true))
            client.on(TdApi.GetArchivedStickerSets::class) {
                TdResult.Success(TdApi.StickerSets(archived.size, archived.toTypedArray()))
            }

            repository.archivedSets().test {
                val first = (awaitItem() as Outcome.Success).value.single()
                assertEquals(1L, first.id)
                assertTrue(first.isArchived)
                assertEquals("set1", first.name)
                val request = client.sentOf<TdApi.GetArchivedStickerSets>().single()
                assertEquals(100, request.limit)
                assertInstanceOf(TdApi.StickerTypeRegular::class.java, request.stickerType)

                archived = emptyList()
                client.emit(TdApi.UpdateInstalledStickerSets(TdApi.StickerTypeMask(), longArrayOf()))
                client.emit(TdApi.UpdateInstalledStickerSets(TdApi.StickerTypeRegular(), longArrayOf(1)))
                assertEquals(emptyList<Any>(), (awaitItem() as Outcome.Success).value)
                expectNoEvents()
            }
        }

    @Test
    fun `trending sets keep whether they were viewed, and are read again on their update`() =
        runTest {
            var sets = arrayOf(setInfo(5, viewed = false))
            client.on(TdApi.GetTrendingStickerSets::class) {
                TdResult.Success(TdApi.TrendingStickerSets(sets.size, sets, false))
            }

            repository.trendingSets().test {
                assertFalse((awaitItem() as Outcome.Success).value.single().isViewed)

                sets = arrayOf(setInfo(5, viewed = true), setInfo(6, viewed = false))
                client.emit(TdApi.UpdateTrendingStickerSets(TdApi.StickerTypeRegular(), TdApi.TrendingStickerSets()))
                assertEquals(listOf(5L, 6L), (awaitItem() as Outcome.Success).value.map { it.id })
            }
        }

    @Test
    fun `a set is read with its stickers, again when it changes but not for other sets`() =
        runTest {
            var installed = false
            client.on(TdApi.GetStickerSet::class) { request ->
                TdResult.Success(
                    TdApi.StickerSet().apply {
                        id = request.setId
                        title = "Garden"
                        name = "garden"
                        isInstalled = installed
                        stickerType = TdApi.StickerTypeRegular()
                        stickers = arrayOf(TdApiFixtures.sticker(emoji = "🐝"), TdApiFixtures.sticker(emoji = "🌻"))
                        emojis = emptyArray()
                    },
                )
            }

            repository.stickerSet(3).test {
                val details = (awaitItem() as Outcome.Success).value
                assertEquals(listOf("🐝", "🌻"), details.stickers.map { it.emoji })
                assertEquals(2, details.set.size)
                assertFalse(details.set.isInstalled)

                client.emit(TdApi.UpdateStickerSet(TdApi.StickerSet().apply { id = 4 }))
                installed = true
                client.emit(TdApi.UpdateStickerSet(TdApi.StickerSet().apply { id = 3 }))
                assertTrue((awaitItem() as Outcome.Success).value.set.isInstalled)
                expectNoEvents()
            }
        }

    @Test
    fun `installing, archiving and removing change the set's two flags`() =
        runTest {
            client.on(TdApi.ChangeStickerSet::class, TdResult.Success(TdApi.Ok()))

            repository.install(1)
            repository.archive(2)
            repository.remove(3)

            assertEquals(
                listOf(Triple(1L, true, false), Triple(2L, false, true), Triple(3L, false, false)),
                client.sentOf<TdApi.ChangeStickerSet>().map { Triple(it.setId, it.isInstalled, it.isArchived) },
            )
        }

    @Test
    fun `reordering sends the regular sets' ids in their new order`() =
        runTest {
            client.on(TdApi.ReorderInstalledStickerSets::class, TdResult.Success(TdApi.Ok()))

            assertEquals(Outcome.Success(Unit), repository.reorder(listOf(3, 1, 2)))

            val request = client.sentOf<TdApi.ReorderInstalledStickerSets>().single()
            assertEquals(listOf(3L, 1L, 2L), request.stickerSetIds.toList())
            assertInstanceOf(TdApi.StickerTypeRegular::class.java, request.stickerType)
        }

    @Test
    fun `marking trending sets viewed sends nothing without sets`() =
        runTest {
            client.on(TdApi.ViewTrendingStickerSets::class, TdResult.Success(TdApi.Ok()))

            repository.markTrendingViewed(emptyList())
            repository.markTrendingViewed(listOf(6))

            val sent = client.sentOf<TdApi.ViewTrendingStickerSets>()
            assertEquals(listOf(listOf(6L)), sent.map { it.stickerSetIds.toList() })
        }

    @Test
    fun `a set's name is looked up, and a name no set has is not found`() =
        runTest {
            client.on(TdApi.SearchStickerSet::class) { request ->
                if (request.name == "garden") {
                    TdResult.Success(TdApi.StickerSet().apply { id = 3 })
                } else {
                    TdResult.Failure(TdError(400, "STICKERSET_INVALID"))
                }
            }

            assertEquals(Outcome.Success(3L), repository.findSet("garden"))
            assertEquals(Outcome.Failure(DomainError.NotFound), repository.findSet("nothing"))
        }
}
