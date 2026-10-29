package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TdlibPersonalChannelRepositoryTest {
    private val client = FakeTdlibClient()
    private val repository = TdlibPersonalChannelRepository(client)

    @Test
    fun `lists the channels that may be shown, and shows one or none`() =
        runTest {
            client.on(TdApi.GetSuitablePersonalChats::class, TdResult.Success(TdApi.Chats(2, longArrayOf(CHANNEL, 7))))
            client.on(TdApi.SetPersonalChat::class, TdResult.Success(TdApi.Ok()))

            assertEquals(listOf(CHANNEL, 7L), (repository.candidates() as Outcome.Success).value)
            assertEquals(Outcome.Success(Unit), repository.setPersonalChannel(CHANNEL))
            assertEquals(Outcome.Success(Unit), repository.setPersonalChannel(0))

            assertEquals(listOf(CHANNEL, 0L), client.sentOf<TdApi.SetPersonalChat>().map { it.chatId })
        }

    private companion object {
        const val CHANNEL = -1_001_234L
    }
}
