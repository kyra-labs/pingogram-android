package dev.kyralabs.pingogram.core.data.updates

import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * [TdlibUpdateRouter]: one collector of the client's updates, each update only to those that asked for its type, in
 * TDLib's order across types, none lost to a slow collector, only those after subscribing, a cancelled collector
 * leaving at once, and the session's end.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TdlibUpdateRouterTest {
    private val client = FakeTdlibClient()

    private fun TestScope.router() = client.updateRouter(backgroundScope)

    private fun user(id: Long) = TdApi.UpdateUserStatus(id, TdApi.UserStatusEmpty())

    private fun chat(id: Long) = TdApi.UpdateChatTitle(id, "Chat $id")

    private val other = TdApi.UpdateOption("version", TdApi.OptionValueString("1"))

    private val user = TdApi.UpdateUserStatus::class
    private val title = TdApi.UpdateChatTitle::class

    /** The ids of [updates], in order. */
    private fun ids(updates: List<TdApi.Update>) =
        updates.map {
            when (it) {
                is TdApi.UpdateUserStatus -> "user ${it.userId}"
                is TdApi.UpdateChatTitle -> "chat ${it.chatId}"
                else -> it.javaClass.simpleName
            }
        }

    @Test
    fun `one router per client, which alone collects its updates`() =
        runTest {
            val router = router()
            router.onUpdates(setOf(user)) {}
            backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { router.updates(setOf(title)).collect {} }

            assertSame(router, client.updateRouter(backgroundScope))
            assertEquals(1, client.updateSubscribers.value)
        }

    @Test
    fun `handlers get only their types, in TDLib's order across them`() =
        runTest {
            val router = router()
            val both = mutableListOf<TdApi.Update>()
            val users = mutableListOf<TdApi.Update>()
            router.onUpdates(setOf(user, title)) { both += it }
            router.onUpdates(setOf(user)) { users += it }

            listOf(user(1), other, chat(2), user(3)).forEach { client.emit(it) }

            assertEquals(listOf("user 1", "chat 2", "user 3"), ids(both))
            assertEquals(listOf("user 1", "user 3"), ids(users))
        }

    @Test
    fun `a flow collector gets only its types, in order, and none is lost to its slowness`() =
        runTest {
            val router = router()
            val received = mutableListOf<TdApi.Update>()
            val count = 1_000
            val slow =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler), start = CoroutineStart.UNDISPATCHED) {
                    router.updates(setOf(user, title)).take(count).collect {
                        delay(1)
                        received += it
                    }
                }

            (1..count).forEach { client.emit(if (it % 2 == 0) chat(it.toLong()) else user(it.toLong())) }
            client.emit(other)
            slow.join()

            assertEquals((1..count).map { if (it % 2 == 0) "chat $it" else "user $it" }, ids(received))
        }

    @Test
    fun `a late subscriber gets only the updates after it subscribed`() =
        runTest {
            val router = router()
            client.emit(user(1))
            val late = mutableListOf<TdApi.Update>()

            router.onUpdates(setOf(user)) { late += it }
            client.emit(user(2))

            assertEquals(listOf("user 2"), ids(late))
        }

    @Test
    fun `a collector cancelled midway leaves, and the others go on`() =
        runTest {
            val router = router()
            val first = mutableListOf<TdApi.Update>()
            val second = mutableListOf<TdApi.Update>()
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val cancelled =
                backgroundScope.launch(dispatcher, start = CoroutineStart.UNDISPATCHED) {
                    router.updates(setOf(user)).collect { first += it }
                }
            backgroundScope.launch(dispatcher, start = CoroutineStart.UNDISPATCHED) {
                router.updates(setOf(user)).collect { second += it }
            }

            client.emit(user(1))
            cancelled.cancel()
            client.emit(user(2))
            runCurrent()

            assertEquals(listOf("user 1"), ids(first))
            assertEquals(listOf("user 1", "user 2"), ids(second))
        }

    @Test
    fun `the session's end is signalled as TDLib logs out and closes`() =
        runTest {
            val router = router()
            var ends = 0
            router.onSessionEnd { ends++ }

            client.emitAuthorizationState(TdApi.AuthorizationStateReady())
            client.emitAuthorizationState(TdApi.AuthorizationStateLoggingOut())
            client.emitAuthorizationState(TdApi.AuthorizationStateClosed())

            assertEquals(2, ends)
        }

    @Test
    fun `a handler that throws is dropped and reported, and the others go on`() =
        runTest {
            val errors = mutableListOf<Throwable>()
            val scope =
                CoroutineScope(
                    SupervisorJob() + UnconfinedTestDispatcher(testScheduler) +
                        CoroutineExceptionHandler { _, error -> errors += error },
                )
            val router = client.updateRouter(scope)
            val received = mutableListOf<TdApi.Update>()
            router.onUpdates(setOf(user)) { error("broken") }
            router.onUpdates(setOf(user)) { received += it }

            client.emit(user(1))
            client.emit(user(2))

            assertEquals(listOf("user 1", "user 2"), ids(received))
            assertEquals(listOf("broken"), errors.map { it.message })
            scope.cancel()
        }

    @Test
    fun `a handler disposed of gets no more updates`() =
        runTest {
            val router = router()
            val received = mutableListOf<TdApi.Update>()
            val handle = router.onUpdates(setOf(user)) { received += it }

            client.emit(user(1))
            handle.dispose()
            client.emit(user(2))

            assertEquals(listOf("user 1"), ids(received))
            assertEquals(1, client.updateSubscribers.value)
        }

    @Test
    fun `a flow's first emission comes once subscribed, before the updates after it`() =
        runTest {
            val router = router()
            val received = mutableListOf<String>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler), start = CoroutineStart.UNDISPATCHED) {
                router
                    .updates(setOf(user)) {
                        // Subscribed already: what TDLib sends meanwhile waits for the collector.
                        assertEquals(1, client.updateSubscribers.value)
                        client.emit(user(1))
                        received += "subscribed"
                    }.collect { received += ids(listOf(it)) }
            }
            client.emit(user(2))
            runCurrent()

            assertEquals(listOf("subscribed", "user 1", "user 2"), received)
        }
}
