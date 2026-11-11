package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.data.attachment.FakeContentSource
import dev.kyralabs.pingogram.core.data.attachment.FakeMediaProcessor
import dev.kyralabs.pingogram.core.data.attachment.UploadFiles
import dev.kyralabs.pingogram.core.domain.model.StickerDraft
import dev.kyralabs.pingogram.core.domain.model.StickerSetNameCheck
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

/** Making a sticker set: the link name's checks and suggestion, and publishing the set as the signed-in user. */
class TdlibStickerSetCreationRepositoryTest {
    @TempDir
    lateinit var cacheDir: File

    private val client = FakeTdlibClient()

    private fun TestScope.repository() =
        TdlibStickerSetCreationRepository(
            client,
            UploadFiles { cacheDir },
            FakeContentSource(),
            FakeMediaProcessor(),
            StandardTestDispatcher(testScheduler),
            backgroundScope,
        )

    @Test
    fun `a malformed link name is invalid without asking Telegram`() =
        runTest {
            val repository = repository()
            listOf("ab", "1cats", "cats__club", "cats_", "cats club").forEach { name ->
                assertEquals(Outcome.Success(StickerSetNameCheck.Invalid), repository.checkName(name))
            }
            assertTrue(client.sent.isEmpty())
        }

    @Test
    fun `Telegram says whether a link name is free`() =
        runTest {
            val repository = repository()
            client.on(TdApi.CheckStickerSetName::class, TdResult.Success(TdApi.CheckStickerSetNameResultOk()))
            assertEquals(Outcome.Success(StickerSetNameCheck.Available), repository.checkName("officecats_kl"))
            client.on(
                TdApi.CheckStickerSetName::class,
                TdResult.Success(TdApi.CheckStickerSetNameResultNameOccupied()),
            )
            assertEquals(Outcome.Success(StickerSetNameCheck.Occupied), repository.checkName("officecats"))
        }

    @Test
    fun `suggests a link name for the title`() =
        runTest {
            client.on(TdApi.GetSuggestedStickerSetName::class, TdResult.Success(TdApi.Text("officecats")))
            assertEquals(Outcome.Success("officecats"), repository().suggestName(" Office cats "))
            assertEquals("Office cats", client.sentOf<TdApi.GetSuggestedStickerSetName>().single().title)
        }

    @Test
    fun `publishes the stickers in order as the signed-in user`() =
        runTest {
            client.on(TdApi.GetMe::class, TdResult.Success(TdApiFixtures.user(id = 7)))
            client.on(TdApi.CreateNewStickerSet::class, TdResult.Success(TdApi.StickerSet().apply { id = 99 }))
            val stickers = listOf(StickerDraft("/up/1/sticker.webp", "😺"), StickerDraft("/up/2/sticker.webp", "😴"))

            assertEquals(Outcome.Success(99L), repository().create(" Office cats ", "officecats_kl", stickers))

            val sent = client.sentOf<TdApi.CreateNewStickerSet>().single()
            assertEquals(listOf<Any>(7L, "Office cats", "officecats_kl"), listOf(sent.userId, sent.title, sent.name))
            assertEquals(listOf("😺", "😴"), sent.stickers.map { it.emojis })
            assertEquals("/up/1/sticker.webp", (sent.stickers[0].sticker as TdApi.InputFileLocal).path)
            assertTrue(sent.stickers.all { it.format is TdApi.StickerFormatWebp })
            assertTrue(sent.stickerType is TdApi.StickerTypeRegular)
        }

    @Test
    fun `a blank title or no stickers is refused before Telegram`() =
        runTest {
            val repository = repository()
            val one = listOf(StickerDraft("/up/1/sticker.webp", "😺"))
            assertEquals(Outcome.Failure(DomainError.TitleInvalid), repository.create(" ", "cats_kl", one))
            assertEquals(Outcome.Failure(DomainError.InputRejected), repository.create("Cats", "cats_kl", emptyList()))
            assertTrue(client.sent.isEmpty())
        }
}
