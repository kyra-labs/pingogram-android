package dev.kyralabs.pingogram.core.tdlib

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

// runCurrent() is still marked experimental.
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultTdlibClientTest {
    private val native = FakeTdlibNative()
    private val parametersProvider = FakeParametersProvider()
    private val log = RecordingTdlibLog()

    private fun TestScope.client(
        logSettings: TdlibLogSettings = TdlibLogSettings(verbosity = 1, file = null),
        updateBufferCapacity: Int = DefaultTdlibClient.UPDATE_BUFFER_CAPACITY,
    ) = DefaultTdlibClient(
        native = native,
        parametersProvider = parametersProvider,
        logSettings = { logSettings },
        log = log,
        scope = backgroundScope,
        updateBufferCapacity = updateBufferCapacity,
    )

    /** A started client whose TDLib instance exists. */
    private fun TestScope.startedClient(updateBufferCapacity: Int = DefaultTdlibClient.UPDATE_BUFFER_CAPACITY) =
        client(updateBufferCapacity = updateBufferCapacity).also {
            it.start()
            runCurrent()
        }

    private val tdlib get() = native.clients.last()

    @Test
    fun `start sets the log verbosity before it creates the TDLib client`() =
        runTest {
            client(logSettings = TdlibLogSettings(verbosity = 1, file = null)).start()
            runCurrent()

            assertEquals(listOf("SetLogVerbosityLevel(1)", "createClient"), native.events)
        }

    @Test
    fun `debug log settings also send TDLib's log to a file`(
        @TempDir dir: File,
    ) = runTest {
        val logFile = dir.resolve("logs/tdlib.log")

        client(logSettings = TdlibLogSettings(verbosity = 3, file = logFile)).start()
        runCurrent()

        assertEquals(listOf("SetLogVerbosityLevel(3)", "SetLogStream(${logFile.path})", "createClient"), native.events)
    }

    @Test
    fun `start is idempotent`() =
        runTest {
            val client = startedClient()

            client.start()
            runCurrent()

            assertEquals(1, native.clients.size)
        }

    @Test
    fun `send returns TDLib's answer`() =
        runTest {
            val client = startedClient()
            val answer = TdApi.OptionValueString("1.8.68")

            val result = async { client.send(TdApi.GetOption("version")) }
            runCurrent()
            tdlib.requests.single().respond(answer)

            assertTrue((result.await() as TdResult.Success).value === answer)
        }

    @Test
    fun `send maps a TDLib error to a failure with its code and message`() =
        runTest {
            val client = startedClient()

            val result = async { client.send(TdApi.GetOption("version")) }
            runCurrent()
            tdlib.requests.single().respond(TdApiFixtures.error(400, "OPTION_NOT_FOUND"))

            assertEquals(TdResult.Failure(TdError(400, "OPTION_NOT_FOUND")), result.await())
        }

    @Test
    fun `send starts the client when nobody did`() =
        runTest {
            val client = client()

            backgroundScope.launch { client.send(TdApi.GetOption("version")) }
            runCurrent()

            assertEquals(1, tdlib.requests.size)
        }

    @Test
    fun `a cancelled send drops TDLib's late answer`() =
        runTest {
            val client = startedClient()
            val cancelled = launch { client.send(TdApi.GetOption("version")) }
            runCurrent()

            cancelled.cancel()
            runCurrent()
            tdlib.requests.single().respond(TdApi.OptionValueString("late"))
            val next = async { client.send(TdApi.GetOption("version")) }
            runCurrent()
            tdlib.requests.last().respond(TdApi.OptionValueString("1.8.68"))

            assertTrue(cancelled.isCancelled && next.await() is TdResult.Success)
        }

    @Test
    fun `updates are emitted in TDLib's order`() =
        runTest {
            val client = startedClient()
            val sent = List(5) { TdApi.UpdateOption("option$it", TdApi.OptionValueEmpty()) }

            client.updates.test {
                sent.forEach(tdlib::update)

                assertTrue(List(sent.size) { awaitItem() }.zip(sent).all { (received, update) -> received === update })
            }
        }

    @Test
    fun `slow collectors make the client wait instead of dropping updates`() =
        runTest {
            val client = startedClient(updateBufferCapacity = 2)
            val gate = CompletableDeferred<Unit>()
            val received = mutableListOf<TdApi.Update>()
            backgroundScope.launch {
                client.updates.collect {
                    gate.await()
                    received += it
                }
            }
            runCurrent()
            val sent = List(10) { TdApi.UpdateOption("option$it", TdApi.OptionValueEmpty()) }

            sent.forEach(tdlib::update)
            runCurrent()
            gate.complete(Unit)
            runCurrent()

            val inOrder = received.size == sent.size && received.zip(sent).all { (a, b) -> a === b }
            assertTrue(inOrder && log.messages.any { "buffer is full" in it })
        }

    @Test
    fun `authState mirrors TDLib's authorization state`() =
        runTest {
            val client = startedClient()

            tdlib.update(TdApiFixtures.authorizationStateUpdate(TdApiFixtures.waitPhoneNumber()))
            runCurrent()

            assertEquals(AuthState.WaitPhoneNumber, client.authState.value)
        }

    @Test
    fun `authState already reflects an authorization update when it is emitted`() =
        runTest {
            val client = startedClient()

            client.updates.test {
                tdlib.update(TdApiFixtures.authorizationStateUpdate(TdApiFixtures.ready()))
                awaitItem()

                assertEquals(AuthState.Ready, client.authState.value)
            }
        }

    @Test
    fun `connectionState mirrors TDLib's connection state`() =
        runTest {
            val client = startedClient()

            tdlib.update(TdApiFixtures.connectionStateUpdate(TdApi.ConnectionStateUpdating()))
            runCurrent()

            assertEquals(ConnectionState.Updating, client.connectionState.value)
        }

    @Test
    fun `WaitTdlibParameters sends the provider's parameters`() =
        runTest {
            startedClient()

            tdlib.update(TdApiFixtures.authorizationStateUpdate(TdApiFixtures.waitTdlibParameters()))
            runCurrent()

            assertTrue(tdlib.requests.single().function === parametersProvider.parameters)
        }

    @Test
    fun `the parameters are prepared at start, before TDLib asks for them, and sent once ready`() =
        runTest {
            startedClient()
            val builtBeforeTdlibAsked = parametersProvider.built

            tdlib.update(TdApiFixtures.authorizationStateUpdate(TdApiFixtures.waitTdlibParameters()))
            runCurrent()

            assertEquals(1 to 1, builtBeforeTdlibAsked to parametersProvider.built)
        }

    @Test
    fun `a retry with a new database key builds its parameters anew`() =
        runTest {
            startedClient()
            tdlib.update(TdApiFixtures.authorizationStateUpdate(TdApiFixtures.waitTdlibParameters()))
            runCurrent()

            tdlib.requests[0].respond(TdApiFixtures.error(401, "Wrong database encryption key"))
            runCurrent()

            assertEquals(2, parametersProvider.built)
        }

    @Test
    fun `a rejected database key discards the database and sends the parameters again, once`() =
        runTest {
            startedClient()
            tdlib.update(TdApiFixtures.authorizationStateUpdate(TdApiFixtures.waitTdlibParameters()))
            runCurrent()

            tdlib.requests[0].respond(TdApiFixtures.error(401, "Wrong database encryption key"))
            runCurrent()
            tdlib.requests[1].respond(TdApiFixtures.error(401, "Wrong database encryption key"))
            runCurrent()

            assertEquals(1 to 2, parametersProvider.discarded to tdlib.requests.size)
        }

    @Test
    fun `other SetTdlibParameters errors are logged, not retried`() =
        runTest {
            startedClient()
            tdlib.update(TdApiFixtures.authorizationStateUpdate(TdApiFixtures.waitTdlibParameters()))
            runCurrent()

            tdlib.requests.single().respond(TdApiFixtures.error(400, "Valid api_id must be provided"))
            runCurrent()

            assertTrue(tdlib.requests.size == 1 && log.messages.any { "SetTdlibParameters failed: 400" in it })
        }

    @Test
    fun `Closed starts a new TDLib client, which is asked for parameters again`() =
        runTest {
            startedClient()
            tdlib.update(TdApiFixtures.authorizationStateUpdate(TdApiFixtures.closed()))
            runCurrent()

            tdlib.update(TdApiFixtures.authorizationStateUpdate(TdApiFixtures.waitTdlibParameters()))
            runCurrent()

            assertTrue(
                native.clients.size == 2 &&
                    native.clients[1]
                        .requests
                        .single()
                        .function is TdApi.SetTdlibParameters,
            )
        }

    @Test
    fun `Closed resets the connection state and reports Closed`() =
        runTest {
            val client = startedClient()
            tdlib.update(TdApiFixtures.connectionStateUpdate(TdApi.ConnectionStateReady()))

            tdlib.update(TdApiFixtures.authorizationStateUpdate(TdApiFixtures.closed()))
            runCurrent()

            assertEquals(
                AuthState.Closed to ConnectionState.Connecting,
                client.authState.value to client.connectionState.value,
            )
        }

    @Test
    fun `requests after Closed go to the new TDLib client`() =
        runTest {
            val client = startedClient()
            val closedClient = tdlib
            closedClient.update(TdApiFixtures.authorizationStateUpdate(TdApiFixtures.closed()))
            runCurrent()

            backgroundScope.launch { client.send(TdApi.GetOption("version")) }
            runCurrent()

            assertTrue(closedClient.requests.isEmpty() && native.clients[1].requests.size == 1)
        }

    private class FakeTdlibNative : TdlibNative {
        val events = mutableListOf<String>()
        val clients = mutableListOf<FakeNativeClient>()

        override fun <T : TdApi.Object> execute(function: TdApi.Function<T>): TdResult<T> {
            events +=
                when (function) {
                    is TdApi.SetLogVerbosityLevel -> "SetLogVerbosityLevel(${function.newVerbosityLevel})"
                    is TdApi.SetLogStream -> "SetLogStream(${(function.logStream as TdApi.LogStreamFile).path})"
                    else -> function.javaClass.simpleName
                }
            // Both functions return Ok.
            @Suppress("UNCHECKED_CAST")
            return TdResult.Success(TdApi.Ok() as T)
        }

        override fun createClient(
            onUpdate: (TdApi.Object) -> Unit,
            onException: (Throwable) -> Unit,
        ): NativeClient {
            events += "createClient"
            return FakeNativeClient(onUpdate).also { clients += it }
        }
    }

    /** One TDLib instance: requests wait until the test answers them; updates are pushed with [update]. */
    private class FakeNativeClient(
        private val onUpdate: (TdApi.Object) -> Unit,
    ) : NativeClient {
        val requests = mutableListOf<Request>()

        override fun send(
            function: TdApi.Function<*>,
            onResult: (TdApi.Object) -> Unit,
        ) {
            requests += Request(function, onResult)
        }

        fun update(update: TdApi.Update) = onUpdate(update)

        class Request(
            val function: TdApi.Function<*>,
            val respond: (TdApi.Object) -> Unit,
        )
    }

    private class FakeParametersProvider : TdlibParametersProvider {
        val parameters = TdApi.SetTdlibParameters()
        var discarded = 0
        var built = 0

        override suspend fun parameters(): TdApi.SetTdlibParameters {
            built++
            return parameters
        }

        override suspend fun discardDatabase() {
            discarded++
        }
    }
}
