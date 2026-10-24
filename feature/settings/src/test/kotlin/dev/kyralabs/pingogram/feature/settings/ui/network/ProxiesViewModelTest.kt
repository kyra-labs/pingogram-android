package dev.kyralabs.pingogram.feature.settings.ui.network

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.data.repository.TdlibProxyRepository
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class ProxiesViewModelTest {
    private val client = FakeTdlibClient()
    private val proxies = FakeProxies(client)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // Through the real TDLib repository on FakeTdlibClient, as the app wires it.
    private fun TestScope.viewModel() = ProxiesViewModel(TdlibProxyRepository(client)).also { runCurrent() }

    private val ProxiesViewModel.content get() = uiState.value as ProxiesUiState.Content

    @Test
    fun `lists the proxies with how each answered`() =
        runTest {
            proxies.add("fast.example", enabled = true)
            proxies.add("gone.example")
            proxies.unreachable += "gone.example"

            val items = viewModel().content.proxies

            assertEquals(listOf("fast.example", "gone.example"), items.map { it.proxy.proxy.server })
            assertEquals(listOf(ProxyCheck.Available(120.milliseconds), ProxyCheck.Unavailable), items.map { it.check })
            assertTrue(viewModel().content.isProxyOn)
        }

    @Test
    fun `pings each proxy once, and again when asked`() =
        runTest {
            proxies.add("a.example")
            val viewModel = viewModel()
            assertEquals(1, client.sentOf<TdApi.PingProxy>().size)

            viewModel.onEvent(ProxiesEvent.CheckAgain)
            runCurrent()

            assertEquals(2, client.sentOf<TdApi.PingProxy>().size)
        }

    @Test
    fun `a tapped proxy is used, and the list shows it`() =
        runTest {
            proxies.add("a.example", enabled = true)
            val second = proxies.add("b.example")
            val viewModel = viewModel()

            viewModel.onEvent(ProxiesEvent.Use(second))
            runCurrent()

            assertEquals(listOf(false, true), viewModel.content.proxies.map { it.proxy.isEnabled })
            assertFalse(viewModel.content.busy)
        }

    @Test
    fun `turning proxies on uses the one used last, and off connects directly`() =
        runTest {
            proxies.add("old.example", lastUsedDate = 100)
            proxies.add("last.example", lastUsedDate = 200)
            val viewModel = viewModel()

            viewModel.onEvent(ProxiesEvent.SetProxyOn(true))
            runCurrent()
            assertEquals(
                "last.example",
                viewModel.content.proxies
                    .single { it.proxy.isEnabled }
                    .proxy.proxy.server,
            )

            viewModel.onEvent(ProxiesEvent.SetProxyOn(false))
            runCurrent()
            assertFalse(viewModel.content.isProxyOn)
        }

    @Test
    fun `turning proxies on without one opens the page to add one`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onEvent(ProxiesEvent.SetProxyOn(true))

            assertEquals(ProxiesEffect.AddProxy, viewModel.effects.first())
        }

    @Test
    fun `deleting asks first, then removes the proxy`() =
        runTest {
            val id = proxies.add("a.example", name = "Home")
            val viewModel = viewModel()

            viewModel.onEvent(ProxiesEvent.Delete(id))
            assertEquals("Home", viewModel.content.deleting?.name)
            assertTrue(client.sentOf<TdApi.RemoveProxy>().isEmpty())

            viewModel.onEvent(ProxiesEvent.ConfirmDelete)
            runCurrent()

            assertNull(viewModel.content.deleting)
            assertTrue(viewModel.content.proxies.isEmpty())
        }

    @Test
    fun `a dismissed deletion removes nothing`() =
        runTest {
            val id = proxies.add("a.example")
            val viewModel = viewModel()

            viewModel.onEvent(ProxiesEvent.Delete(id))
            viewModel.onEvent(ProxiesEvent.DismissDelete)
            runCurrent()

            assertNull(viewModel.content.deleting)
            assertEquals(1, viewModel.content.proxies.size)
        }

    @Test
    fun `a change Telegram refuses shows its error`() =
        runTest {
            val id = proxies.add("a.example")
            client.on(TdApi.EnableProxy::class, TdResult.Failure(TdError(500, "Internal")))
            val viewModel = viewModel()

            viewModel.onEvent(ProxiesEvent.Use(id))

            assertEquals(ProxiesEffect.ShowError(DomainError.Unknown(500, "Internal")), viewModel.effects.first())
            assertFalse(viewModel.content.busy)
        }

    @Test
    fun `proxies that cannot be read show the error, and try again`() =
        runTest {
            client.on(TdApi.GetProxies::class, TdResult.Failure(TdError(500, "Internal")))
            val viewModel = viewModel()
            assertTrue(viewModel.uiState.value is ProxiesUiState.Error)

            client.on(TdApi.GetProxies::class) { TdResult.Success(TdApi.AddedProxies(emptyArray())) }
            viewModel.onEvent(ProxiesEvent.Retry)
            runCurrent()

            assertTrue(viewModel.content.proxies.isEmpty())
        }
}
