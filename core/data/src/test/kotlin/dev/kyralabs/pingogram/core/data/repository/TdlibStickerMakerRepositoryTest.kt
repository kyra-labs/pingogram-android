package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.data.attachment.FakeMediaProcessor
import dev.kyralabs.pingogram.core.data.attachment.ImageInfo
import dev.kyralabs.pingogram.core.data.attachment.Orientation
import dev.kyralabs.pingogram.core.data.attachment.UploadFiles
import dev.kyralabs.pingogram.core.domain.model.StickerDraft
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** Making a sticker from a photo: the user's own sets it can join, writing the editor's drawing, adding it. */
class TdlibStickerMakerRepositoryTest {
    @TempDir
    lateinit var cacheDir: File

    private val client = FakeTdlibClient()
    private val media = FakeMediaProcessor()

    private fun TestScope.repository() =
        TdlibStickerMakerRepository(
            client,
            UploadFiles { cacheDir },
            media,
            StandardTestDispatcher(testScheduler),
            backgroundScope,
        )

    private fun set(
        id: Long,
        size: Int,
        type: TdApi.StickerType = TdApi.StickerTypeRegular(),
    ) = TdApi.StickerSetInfo().apply {
        this.id = id
        title = "Set $id"
        name = "set_$id"
        this.size = size
        stickerType = type
        covers = emptyArray()
    }

    @Test
    fun `lists the user's own sets of stickers that can take one more`() =
        runTest {
            val sets =
                arrayOf(
                    set(1, size = 12),
                    set(2, size = 120),
                    set(3, size = 4, type = TdApi.StickerTypeCustomEmoji()),
                    set(4, size = 3, type = TdApi.StickerTypeMask()),
                    set(5, size = 119),
                )
            client.on(TdApi.GetOwnedStickerSets::class, TdResult.Success(TdApi.StickerSets(sets.size, sets)))

            val owned = repository().ownedSets()

            assertEquals(listOf(1L, 5L), (owned as Outcome.Success).value.map { it.id })
            assertEquals(listOf("set_1", "set_5"), owned.value.map { it.name })
            assertEquals(0L, client.sentOf<TdApi.GetOwnedStickerSets>().single().offsetStickerSetId)
        }

    @Test
    fun `writes the drawing as a sticker whose longer side is 512 pixels`() =
        runTest {
            media.images[DRAWING] = ImageInfo(1024, 768, Orientation.Normal)

            val written = repository().prepareSticker(byteArrayOf(DRAWING, 1, 2))

            val path = (written as Outcome.Success).value
            assertTrue(File(path).exists())
            assertTrue(File(path).path.startsWith(cacheDir.path))
            assertEquals(listOf(512 to 384), media.stickerPlans.map { it.width to it.height })
            // The PNG it came from goes once written.
            assertEquals(listOf("sticker.webp"), File(path).parentFile.list()?.toList())
        }

    @Test
    fun `bytes that are no image are refused and leave nothing behind`() =
        runTest {
            val written = repository().prepareSticker(byteArrayOf(9, 9))

            assertEquals(Outcome.Failure(DomainError.PhotoInvalid), written)
            assertTrue(cacheDir.walk().none { it.isFile })
        }

    @Test
    fun `adds the sticker to the set as the signed-in user`() =
        runTest {
            client.on(TdApi.GetMe::class, TdResult.Success(TdApiFixtures.user(id = 7)))
            client.on(TdApi.AddStickerToSet::class, TdResult.Success(TdApi.Ok()))

            val added = repository().addSticker("officecats_kl", StickerDraft("/up/1/sticker.webp", "😺"))

            assertEquals(Outcome.Success(Unit), added)
            val sent = client.sentOf<TdApi.AddStickerToSet>().single()
            assertEquals(listOf<Any>(7L, "officecats_kl", "😺"), listOf(sent.userId, sent.name, sent.sticker.emojis))
            assertEquals("/up/1/sticker.webp", (sent.sticker.sticker as TdApi.InputFileLocal).path)
            assertTrue(sent.sticker.format is TdApi.StickerFormatWebp)
        }

    @Test
    fun `Telegram's refusal to add comes back`() =
        runTest {
            client.on(TdApi.GetMe::class, TdResult.Success(TdApiFixtures.user(id = 7)))
            client.on(TdApi.AddStickerToSet::class, TdResult.Failure(TdError(400, "STICKERS_TOO_MUCH")))

            val added = repository().addSticker("full_set", StickerDraft("/up/1/sticker.webp", "😺"))

            assertTrue(added is Outcome.Failure)
        }

    private companion object {
        const val DRAWING: Byte = 5
    }
}
