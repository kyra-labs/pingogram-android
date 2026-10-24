package dev.kyralabs.pingogram.feature.settings.ui.network

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.data.repository.TdlibProxyRepository
import dev.kyralabs.pingogram.core.domain.model.Proxy
import dev.kyralabs.pingogram.core.domain.model.ProxyType
import dev.kyralabs.pingogram.core.navigation.ProxyLinkRoute
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class ProxyLinkViewModelTest {
    private val client = FakeTdlibClient()
    private val proxies = FakeProxies(client)
    private val proxy = Proxy("proxy.example", 443, ProxyType.Mtproto("dd00"))

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel() = ProxyLinkViewModel(TdlibProxyRepository(client), proxy).also { runCurrent() }

    @Test
    fun `shows the proxy and pings it, changing nothing`() =
        runTest {
            val state = viewModel().uiState.value

            assertEquals(proxy, state.proxy)
            assertEquals(ProxyCheck.Available(120.milliseconds), state.check)
            assertFalse(state.inUse)
            assertTrue(client.sentOf<TdApi.AddProxy>().isEmpty())
        }

    @Test
    fun `connect adds the proxy and uses it`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onEvent(ProxyLinkEvent.Connect)
            runCurrent()

            assertTrue(viewModel.uiState.value.connected)
            assertTrue(client.sentOf<TdApi.AddProxy>().single().enable)
        }

    @Test
    fun `a proxy the user has already is used again rather than added twice`() =
        runTest {
            val id = proxies.add("proxy.example", 443, TdApi.ProxyTypeMtproto("dd00"))
            val viewModel = viewModel()

            viewModel.onEvent(ProxyLinkEvent.Connect)
            runCurrent()

            assertEquals(id, client.sentOf<TdApi.EnableProxy>().single().proxyId)
            assertTrue(client.sentOf<TdApi.AddProxy>().isEmpty())
        }

    @Test
    fun `says when the proxy is in use already`() =
        runTest {
            proxies.add("proxy.example", 443, TdApi.ProxyTypeMtproto("dd00"), enabled = true)

            assertTrue(viewModel().uiState.value.inUse)
        }

    @Test
    fun `a proxy TDLib refuses shows why`() =
        runTest {
            proxies.refuse = TdError(400, "Wrong secret")
            val viewModel = viewModel()

            viewModel.onEvent(ProxyLinkEvent.Connect)
            runCurrent()

            assertEquals(DomainError.InputRejected, viewModel.uiState.value.error)
            assertFalse(viewModel.uiState.value.connected)
        }

    @Test
    fun `a link's route carries every protocol`() {
        assertEquals(
            Proxy("h.example", 8080, ProxyType.Http("u", "p", httpOnly = true)),
            ProxyLinkRoute("h.example", 8080, "Http", "u", "p", httpOnly = true).toProxy(),
        )
        assertEquals(
            Proxy("s.example", 1080, ProxyType.Socks5()),
            ProxyLinkRoute("s.example", 1080, "Socks5").toProxy(),
        )
        assertFalse("h.example" in ProxyLinkRoute("h.example", 8080, "Http", password = "p").toString())
    }
}
