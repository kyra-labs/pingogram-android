package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.data.mapper.toUser
import dev.kyralabs.pingogram.core.domain.model.AccentColorOption
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Name colors: the built-in ones until TDLib announces Telegram's, setting the user's own, and a user's colors. */
class TdlibAccentColorRepositoryTest {
    private val client = FakeTdlibClient()

    private fun TestScope.repository() = TdlibAccentColorRepository(client, backgroundScope)

    @Test
    fun `offers the built-in colors until TDLib announces Telegram's, each with the color it is drawn like`() =
        runTest {
            val repository = repository()
            assertEquals((0..6).toList(), repository.options.value.map { it.id })

            val extra = TdApi.AccentColor(8, 2, intArrayOf(), intArrayOf(), 0)
            client.emit(TdApi.UpdateAccentColors(arrayOf(extra), intArrayOf(0, 1, 8)))

            assertEquals(
                listOf(AccentColorOption(0, 0), AccentColorOption(1, 1), AccentColorOption(8, 2)),
                repository.options.value,
            )
            assertTrue(
                repository.options.value
                    .take(2)
                    .all { it.isBuiltIn },
            )
        }

    @Test
    fun `sets the user's name color without a background emoji`() =
        runTest {
            client.on(TdApi.SetAccentColor::class, TdResult.Success(TdApi.Ok()))
            assertEquals(Outcome.Success(Unit), repository().setAccentColor(4))
            with(client.sentOf<TdApi.SetAccentColor>().single()) {
                assertEquals(4, accentColorId)
                assertEquals(0L, backgroundCustomEmojiId)
            }
        }

    @Test
    fun `a user carries their profile color and emoji status`() {
        val user =
            TdApiFixtures.user(id = 7).apply {
                profileAccentColorId = 3
                emojiStatus = TdApi.EmojiStatus(TdApi.EmojiStatusTypeCustomEmoji(99), 0)
            }
        with(user.toUser()) {
            assertEquals(3, profileAccentColorId)
            assertEquals(99L, emojiStatusId)
        }
    }
}
