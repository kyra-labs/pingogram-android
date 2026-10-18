package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UpdateStoreTest {
    private val client = FakeTdlibClient()

    /** A store of the option names TDLib reports, in order. */
    private fun TestScope.store() =
        UpdateStore(client, backgroundScope, persistentListOf<String>()) { names: PersistentList<String>, update ->
            if (update is TdApi.UpdateOption) names.adding(update.name) else names
        }

    private fun option(name: String) = TdApi.UpdateOption(name, TdApi.OptionValueEmpty())

    @Test
    fun `starts empty`() =
        runTest {
            assertEquals(emptyList<String>(), store().state.value)
        }

    @Test
    fun `is subscribed to the updates when the constructor returns`() =
        runTest {
            val store = store()

            // No suspension in between: FakeTdlibClient, like TDLib, drops an update that has no subscriber yet.
            client.emit(option("version"))

            assertEquals(listOf("version"), store.state.value)
        }

    @Test
    fun `applies every update, in order`() =
        runTest {
            val store = store()

            listOf("a", "b", "c").forEach { client.emit(option(it)) }

            assertEquals(listOf("a", "b", "c"), store.state.value)
        }

    @Test
    fun `resets to the empty state when TDLib closes`() =
        runTest {
            val store = store()
            client.emit(option("a"))

            client.emitAuthorizationState(TdApiFixtures.closed())

            assertEquals(emptyList<String>(), store.state.value)
        }

    @Test
    fun `applies the updates of the next TDLib instance after a reset`() =
        runTest {
            val store = store()
            client.emit(option("a"))
            client.emitAuthorizationState(TdApiFixtures.closed())

            client.emit(option("b"))

            assertEquals(listOf("b"), store.state.value)
        }

    @Test
    fun `keeps the state through logging out until TDLib closed`() =
        runTest {
            val store = store()
            client.emit(option("a"))

            client.emitAuthorizationState(TdApiFixtures.loggingOut())
            client.emitAuthorizationState(TdApiFixtures.closing())

            assertEquals(listOf("a"), store.state.value)
        }

    @Test
    fun `update changes the state between updates`() =
        runTest {
            val store = store()
            client.emit(option("a"))

            store.update { names -> names.adding("answer") }
            client.emit(option("b"))

            assertEquals(listOf("a", "answer", "b"), store.state.value)
        }
}
