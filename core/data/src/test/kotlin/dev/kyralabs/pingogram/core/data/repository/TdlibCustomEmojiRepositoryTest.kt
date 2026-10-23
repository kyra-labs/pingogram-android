package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.StickerFormat
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.closed
import dev.kyralabs.pingogram.core.testing.customEmojiSticker
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The custom emoji of the texts on screen: what TDLib is asked (batches of 200, nothing twice), what comes back, the
 * failures, a cancelled caller, and forgetting everything when TDLib closes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TdlibCustomEmojiRepositoryTest {
    private val client = FakeTdlibClient()

    private fun TestScope.repository() = TdlibCustomEmojiRepository(client, backgroundScope)

    /** What [TdlibCustomEmojiRepository.customEmoji] holds once its state has reached it. */
    private fun TestScope.loadedOf(repository: TdlibCustomEmojiRepository) =
        run {
            runCurrent()
            repository.customEmoji.value
        }

    /** TDLib answers with a sticker for every id asked for except those in [unknown]. */
    private fun answering(unknown: Set<Long> = emptySet()) =
        client.on(TdApi.GetCustomEmojiStickers::class) { request ->
            val ids = request.customEmojiIds.filterNot { it in unknown }
            TdResult.Success(TdApi.Stickers(ids.map { customEmojiSticker(it) }.toTypedArray()))
        }

    @Test
    fun `loads the stickers of the ids asked for, by custom emoji id, with whether they take the text's color`() =
        runTest {
            client.on(
                TdApi.GetCustomEmojiStickers::class,
                TdResult.Success(
                    TdApi.Stickers(
                        arrayOf(
                            customEmojiSticker(1),
                            customEmojiSticker(2, TdApi.StickerFormatWebp(), needsRepainting = true),
                        ),
                    ),
                ),
            )
            val repository = repository()

            assertEquals(Outcome.Success(Unit), repository.load(listOf(1, 2)))

            val loaded = loadedOf(repository)
            assertEquals(setOf(1L, 2L), loaded.keys)
            assertEquals(StickerFormat.Tgs, loaded.getValue(1).sticker.format)
            assertFalse(loaded.getValue(1).needsRepainting)
            assertEquals(StickerFormat.Webp, loaded.getValue(2).sticker.format)
            assertTrue(loaded.getValue(2).needsRepainting)
            assertEquals(
                listOf(1L, 2L),
                client
                    .sentOf<TdApi.GetCustomEmojiStickers>()
                    .single()
                    .customEmojiIds
                    .toList(),
            )
        }

    @Test
    fun `asks for at most 200 ids at once`() =
        runTest {
            answering()
            val repository = repository()

            repository.load((1L..450L).toList())

            assertEquals(
                listOf(200, 200, 50),
                client.sentOf<TdApi.GetCustomEmojiStickers>().map { it.customEmojiIds.size },
            )
            assertEquals(450, loadedOf(repository).size)
        }

    @Test
    fun `never asks twice for an id it has, nor for one TDLib did not know`() =
        runTest {
            answering(unknown = setOf(3))
            val repository = repository()

            repository.load(listOf(1, 1, 2, 3))
            repository.load(listOf(1, 2, 3))
            repository.load(listOf(2, 4))

            assertEquals(
                listOf(listOf(1L, 2L, 3L), listOf(4L)),
                client.sentOf<TdApi.GetCustomEmojiStickers>().map { it.customEmojiIds.toList() },
            )
            assertEquals(setOf(1L, 2L, 4L), loadedOf(repository).keys)
        }

    @Test
    fun `a caller does not ask again for ids another caller waits for`() =
        runTest {
            val answer = CompletableDeferred<TdResult<TdApi.Stickers>>()
            client.on(TdApi.GetCustomEmojiStickers::class) { answer.await() }
            val repository = repository()

            launch { repository.load(listOf(1, 2)) }
            runCurrent()
            launch { repository.load(listOf(2, 1)) }
            runCurrent()
            answer.complete(TdResult.Success(TdApi.Stickers(arrayOf(customEmojiSticker(1), customEmojiSticker(2)))))
            runCurrent()

            assertEquals(1, client.sentOf<TdApi.GetCustomEmojiStickers>().size)
            assertEquals(setOf(1L, 2L), loadedOf(repository).keys)
        }

    @Test
    fun `ids a cancelled caller waited for are asked for again by the next one`() =
        runTest {
            val never = CompletableDeferred<TdResult<TdApi.Stickers>>()
            client.on(TdApi.GetCustomEmojiStickers::class) { never.await() }
            val repository = repository()
            val waiting = launch { repository.load(listOf(1)) }
            runCurrent()

            waiting.cancel()
            runCurrent()
            answering()
            repository.load(listOf(1))

            assertEquals(2, client.sentOf<TdApi.GetCustomEmojiStickers>().size)
            assertEquals(setOf(1L), loadedOf(repository).keys)
        }

    @Test
    fun `a failed request fails, loads nothing, and is asked again next time`() =
        runTest {
            client.on(
                TdApi.GetCustomEmojiStickers::class,
                TdResult.Failure(TdError(429, "Too Many Requests: retry after 5")),
            )
            val repository = repository()

            assertEquals(Outcome.Failure(DomainError.FloodWait(5)), repository.load(listOf(1)))
            assertTrue(loadedOf(repository).isEmpty())

            answering()
            assertEquals(Outcome.Success(Unit), repository.load(listOf(1)))
            assertEquals(setOf(1L), loadedOf(repository).keys)
        }

    @Test
    fun `a failed batch keeps what the others loaded`() =
        runTest {
            client.on(TdApi.GetCustomEmojiStickers::class) { request ->
                if (request.customEmojiIds.first() == 1L) {
                    TdResult.Failure(TdError(401, "Unauthorized"))
                } else {
                    TdResult.Success(
                        TdApi.Stickers(request.customEmojiIds.map { customEmojiSticker(it) }.toTypedArray()),
                    )
                }
            }
            val repository = repository()

            assertEquals(Outcome.Failure(DomainError.Unauthorized), repository.load((1L..201L).toList()))
            assertEquals(setOf(201L), loadedOf(repository).keys)
        }

    @Test
    fun `no answer in time is a network error`() =
        runTest {
            client.on(TdApi.GetCustomEmojiStickers::class) { CompletableDeferred<TdResult<TdApi.Stickers>>().await() }

            assertEquals(Outcome.Failure(DomainError.Network), repository().load(listOf(1)))
        }

    @Test
    fun `forgets everything when TDLib closes, whose file ids the next session reuses`() =
        runTest {
            answering(unknown = setOf(2))
            val repository = repository()
            repository.load(listOf(1, 2))

            client.emitAuthorizationState(closed())
            runCurrent()

            assertTrue(loadedOf(repository).isEmpty())
            repository.load(listOf(1, 2))
            assertEquals(2, client.sentOf<TdApi.GetCustomEmojiStickers>().size)
            assertEquals(
                listOf(1L, 2L),
                client
                    .sentOf<TdApi.GetCustomEmojiStickers>()
                    .last()
                    .customEmojiIds
                    .toList(),
            )
        }
}
