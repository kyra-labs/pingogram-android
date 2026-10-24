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

class TdlibContactJoinedRepositoryTest {
    private val client = FakeTdlibClient()
    private val repository = TdlibContactJoinedRepository(client)

    @Test
    fun `reads the option, then follows its updates`() =
        runTest {
            client.on(TdApi.GetOption::class, TdResult.Success<TdApi.OptionValue>(TdApi.OptionValueBoolean(true)))

            repository.notifies.test {
                assertFalse(awaitItem())

                client.emit(TdApi.UpdateOption(OPTION, TdApi.OptionValueBoolean(false)))
                assertTrue(awaitItem())

                client.emit(TdApi.UpdateOption("other_option", TdApi.OptionValueBoolean(true)))
                expectNoEvents()
            }
        }

    @Test
    fun `an unset or unreadable option means notices come`() =
        runTest {
            client.on(TdApi.GetOption::class, TdResult.Success<TdApi.OptionValue>(TdApi.OptionValueEmpty()))
            repository.notifies.test { assertTrue(awaitItem()) }

            client.on(TdApi.GetOption::class, TdResult.Failure(TdError(400, "Bad option")))
            repository.notifies.test { assertTrue(awaitItem()) }
        }

    @Test
    fun `turning notices off sets the option`() =
        runTest {
            client.on(TdApi.SetOption::class, TdResult.Success(TdApi.Ok()))

            assertEquals(Outcome.Success(Unit), repository.setNotifies(false))

            val sent = client.sentOf<TdApi.SetOption>().single()
            assertEquals(OPTION, sent.name)
            assertTrue((sent.value as TdApi.OptionValueBoolean).value)
        }

    private companion object {
        const val OPTION = "disable_contact_registered_notifications"
    }
}
