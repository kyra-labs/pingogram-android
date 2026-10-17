package dev.kyralabs.pingogram.core.testing

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.tdlib.AuthState
import dev.kyralabs.pingogram.core.tdlib.ConnectionState
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FakeTdlibClientTest {
    private val client = FakeTdlibClient()

    @Test
    fun `send answers with the response registered for the function class`() =
        runTest {
            val ok = TdApi.Ok()
            client.on(TdApi.SetTdlibParameters::class, TdResult.Success(ok))

            val result = client.send(TdApi.SetTdlibParameters())

            assertTrue((result as TdResult.Success).value === ok)
        }

    @Test
    fun `a handler receives the function that was sent`() =
        runTest {
            client.on(TdApi.GetOption::class) { TdResult.Success(TdApi.OptionValueString(it.name)) }

            val result = client.send(TdApi.GetOption("version"))

            assertEquals("version", ((result as TdResult.Success).value as TdApi.OptionValueString).value)
        }

    @Test
    fun `a later registration replaces an earlier one`() =
        runTest {
            client.on(TdApi.Close::class, TdResult.Success(TdApi.Ok()))
            client.on(TdApi.Close::class, TdResult.Failure(TdError(500, "Closed")))

            assertEquals(TdResult.Failure(TdError(500, "Closed")), client.send(TdApi.Close()))
        }

    @Test
    fun `send without a registered response fails, naming the function`() =
        runTest {
            val result = client.send(TdApi.GetMe())

            assertEquals(
                TdResult.Failure(TdError(FakeTdlibClient.NO_RESPONSE, "FakeTdlibClient has no response for GetMe")),
                result,
            )
        }

    @Test
    fun `every sent function is recorded in order`() =
        runTest {
            val first = TdApi.GetMe()
            val second = TdApi.Close()

            client.send(first)
            client.send(second)

            assertTrue(client.sent.size == 2 && client.sent[0] === first && client.sent[1] === second)
        }

    @Test
    fun `sentOf filters the recorded functions by type`() =
        runTest {
            client.send(TdApi.GetMe())
            client.send(TdApi.Close())

            assertEquals(1, client.sentOf<TdApi.Close>().size)
        }

    @Test
    fun `emitted updates reach subscribers in order`() =
        runTest {
            val updates = List(3) { TdApi.UpdateOption("option$it", TdApi.OptionValueEmpty()) }

            client.updates.test {
                updates.forEach { client.emit(it) }

                assertTrue(
                    List(updates.size) { awaitItem() }.zip(updates).all { (received, sent) -> received === sent },
                )
            }
        }

    @Test
    fun `emitAuthorizationState sets authState before subscribers get the update`() =
        runTest {
            client.updates.test {
                client.emitAuthorizationState(TdApiFixtures.waitPhoneNumber())
                awaitItem()

                assertEquals(AuthState.WaitPhoneNumber, client.authState.value)
            }
        }

    @Test
    fun `emitConnectionState sets connectionState and emits the update`() =
        runTest {
            client.updates.test {
                client.emitConnectionState(TdApi.ConnectionStateReady())

                assertTrue(awaitItem() is TdApi.UpdateConnectionState)
                assertEquals(ConnectionState.Ready, client.connectionState.value)
            }
        }

    @Test
    fun `start marks the client as started`() {
        client.start()

        assertTrue(client.isStarted)
    }
}
