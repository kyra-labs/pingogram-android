package dev.kyralabs.pingogram.core.data.repository

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.StickerFormat
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

/** [TdlibStickerRepository]: the sticker panel's lists, read when collected and again on their updates. */
class TdlibStickerRepositoryTest {
    private val client = FakeTdlibClient()
    private val repository = TdlibStickerRepository(client)

    private fun setInfo(
        id: Long,
        title: String,
        size: Int,
        covers: Array<TdApi.Sticker> = emptyArray(),
    ) = TdApi.StickerSetInfo().apply {
        this.id = id
        this.title = title
        this.size = size
        this.covers = covers
        stickerType = TdApi.StickerTypeRegular()
    }

    @Test
    fun `installed sets are read as regular stickers, with their size and first sticker`() =
        runTest {
            val cover = TdApiFixtures.sticker(format = TdApi.StickerFormatTgs(), emoji = "🐝", setId = 3)
            client.on(
                TdApi.GetInstalledStickerSets::class,
                TdResult.Success(TdApi.StickerSets(1, arrayOf(setInfo(3, "Garden", 24, arrayOf(cover))))),
            )

            repository.installedSets().test {
                val sets = (awaitItem() as Outcome.Success).value
                assertEquals(listOf(Triple(3L, "Garden", 24)), sets.map { Triple(it.id, it.title, it.size) })
                assertEquals(StickerFormat.Tgs, sets.single().cover?.format)
                assertInstanceOf(
                    TdApi.StickerTypeRegular::class.java,
                    client.sentOf<TdApi.GetInstalledStickerSets>().single().stickerType,
                )
            }
        }

    @Test
    fun `installed sets are read again when regular sets change, not for other kinds`() =
        runTest {
            var title = "Old"
            client.on(TdApi.GetInstalledStickerSets::class) {
                TdResult.Success(TdApi.StickerSets(1, arrayOf(setInfo(1, title, 1))))
            }

            repository.installedSets().test {
                assertEquals("Old", (awaitItem() as Outcome.Success).value.single().title)

                client.emit(TdApi.UpdateInstalledStickerSets(TdApi.StickerTypeCustomEmoji(), longArrayOf(9)))
                title = "New"
                client.emit(TdApi.UpdateInstalledStickerSets(TdApi.StickerTypeRegular(), longArrayOf(1)))

                assertEquals("New", (awaitItem() as Outcome.Success).value.single().title)
                expectNoEvents()
            }
        }

    @Test
    fun `recent stickers are the sent ones, read again when they change`() =
        runTest {
            var emoji = "👍"
            client.on(TdApi.GetRecentStickers::class) {
                TdResult.Success(TdApi.Stickers(arrayOf(TdApiFixtures.sticker(emoji = emoji))))
            }

            repository.recentStickers().test {
                assertEquals("👍", (awaitItem() as Outcome.Success).value.single().emoji)
                assertEquals(false, client.sentOf<TdApi.GetRecentStickers>().single().isAttached)

                client.emit(TdApi.UpdateRecentStickers(true, intArrayOf(1)))
                emoji = "🎉"
                client.emit(TdApi.UpdateRecentStickers(false, intArrayOf(2)))

                assertEquals("🎉", (awaitItem() as Outcome.Success).value.single().emoji)
                expectNoEvents()
            }
        }

    @Test
    fun `a failed read is emitted, and the next change reads again`() =
        runTest {
            var fails = true
            client.on(TdApi.GetFavoriteStickers::class) {
                if (fails) {
                    TdResult.Failure(TdError(400, "BAD_REQUEST"))
                } else {
                    TdResult.Success(TdApi.Stickers(emptyArray()))
                }
            }

            repository.favoriteStickers().test {
                assertInstanceOf(Outcome.Failure::class.java, awaitItem())
                fails = false
                client.emit(TdApi.UpdateFavoriteStickers(intArrayOf()))
                assertEquals(Outcome.Success(emptyList<Any>()), awaitItem())
            }
        }

    @Test
    fun `a set's stickers leave out formats this version does not know`() =
        runTest {
            val known = TdApiFixtures.sticker(emoji = "🐝")
            val unknown = TdApiFixtures.sticker(emoji = "?", format = UnknownFormat)
            client.on(TdApi.GetStickerSet::class) { request ->
                assertEquals(3L, request.setId)
                TdResult.Success(TdApi.StickerSet().apply { stickers = arrayOf(known, unknown) })
            }

            val stickers = (repository.stickerSet(3) as Outcome.Success).value

            assertEquals(listOf("🐝"), stickers.map { it.emoji })
        }

    @Test
    fun `a set that cannot be read fails with the domain error`() =
        runTest {
            client.on(
                TdApi.GetStickerSet::class,
                TdResult.Failure(TdError(429, "Too Many Requests: retry after 7")),
            )

            assertEquals(Outcome.Failure(DomainError.FloodWait(7)), repository.stickerSet(3))
        }
}

/** A sticker format from a newer TDLib. */
private object UnknownFormat : TdApi.StickerFormat() {
    override fun getConstructor() = 0
}
