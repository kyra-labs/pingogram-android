package dev.kyralabs.pingogram.core.data.repository

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** [TdlibFrequentContactsRepository]: TDLib's option `disable_top_chats`, inverted. */
class TdlibFrequentContactsRepositoryTest {
    private val client = FakeTdlibClient()
    private val repository = TdlibFrequentContactsRepository(client)

    @Test
    fun `suggests while the option is unset, then follows its updates`() =
        runTest {
            client.on(TdApi.GetOption::class, TdResult.Success(TdApi.OptionValueEmpty()))

            repository.suggests.test {
                assertTrue(awaitItem())
                assertEquals("disable_top_chats", client.sentOf<TdApi.GetOption>().single().name)

                client.emit(TdApi.UpdateOption("prefer_ipv6", TdApi.OptionValueBoolean(true)))
                client.emit(TdApi.UpdateOption("disable_top_chats", TdApi.OptionValueBoolean(true)))
                assertFalse(awaitItem())
                expectNoEvents()
            }
        }

    @Test
    fun `an option TDLib cannot read counts as suggesting`() =
        runTest {
            client.on(TdApi.GetOption::class, TdResult.Failure(TdError(400, "OPTION_NOT_FOUND")))

            repository.suggests.test {
                assertTrue(awaitItem())
            }
        }

    @Test
    fun `turning suggestions off sets the option to true`() =
        runTest {
            client.on(TdApi.SetOption::class, TdResult.Success(TdApi.Ok()))

            val result = repository.setSuggests(false)

            assertEquals(Outcome.Success(Unit), result)
            val set = client.sentOf<TdApi.SetOption>().single()
            assertEquals("disable_top_chats", set.name)
            assertTrue((set.value as TdApi.OptionValueBoolean).value)
        }
}
