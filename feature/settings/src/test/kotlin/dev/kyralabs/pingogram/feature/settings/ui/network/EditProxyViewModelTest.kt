package dev.kyralabs.pingogram.feature.settings.ui.network

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.data.repository.TdlibProxyRepository
import dev.kyralabs.pingogram.core.domain.model.ProxyKind
import dev.kyralabs.pingogram.core.tdlib.TdError
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class EditProxyViewModelTest {
    private val client = FakeTdlibClient()
    private val proxies = FakeProxies(client)
    private val savedStateHandle = SavedStateHandle()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(proxyId: Int? = null) =
        EditProxyViewModel(TdlibProxyRepository(client), savedStateHandle, proxyId).also { runCurrent() }

    private val EditProxyViewModel.form get() = uiState.value as EditProxyUiState.Form

    private fun EditProxyViewModel.type(change: ProxyForm.() -> ProxyForm) =
        onEvent(EditProxyEvent.Change(form.form.change()))

    @Test
    fun `a new proxy starts as an empty SOCKS5 form`() =
        runTest {
            val form = viewModel().form

            assertEquals(ProxyForm(), form.form)
            assertTrue(form.isNew)
        }

    @Test
    fun `saving checks the fields on the device first`() =
        runTest {
            val viewModel = viewModel()
            viewModel.type { copy(kind = ProxyKind.Mtproto, server = "bad server", port = "70000") }

            viewModel.onEvent(EditProxyEvent.Save)

            assertEquals(setOf(ProxyField.Server, ProxyField.Port, ProxyField.Secret), viewModel.form.errors)
            assertTrue(client.sentOf<TdApi.AddProxy>().isEmpty())
        }

    @Test
    fun `correcting a field clears its error only`() =
        runTest {
            val viewModel = viewModel()
            viewModel.onEvent(EditProxyEvent.Save)

            viewModel.type { copy(server = "proxy.example") }

            assertEquals(setOf(ProxyField.Port), viewModel.form.errors)
        }

    @Test
    fun `a new proxy is added and used, then the page closes`() =
        runTest {
            val viewModel = viewModel()
            viewModel.type {
                copy(
                    kind = ProxyKind.Http,
                    server = " proxy.example ",
                    port = "8080",
                    username = "ada",
                    password = "pw",
                    httpOnly = true,
                )
            }

            viewModel.onEvent(EditProxyEvent.Save)
            runCurrent()

            assertEquals(EditProxyEffect.Saved, viewModel.effects.first())
            val sent = client.sentOf<TdApi.AddProxy>().single()
            assertTrue(sent.enable)
            assertEquals("proxy.example" to 8080, sent.proxy.server to sent.proxy.port)
            val type = sent.proxy.type as TdApi.ProxyTypeHttp
            assertEquals(Triple("ada", "pw", true), Triple(type.username, type.password, type.httpOnly))
        }

    @Test
    fun `an edited proxy keeps whether it is in use`() =
        runTest {
            val id = proxies.add("old.example", type = TdApi.ProxyTypeMtproto("dd11"), name = "Work")
            val viewModel = viewModel(id)
            assertEquals(
                ProxyForm(ProxyKind.Mtproto, "old.example", "1080", secret = "dd11", name = "Work"),
                viewModel.form.form,
            )
            assertFalse(viewModel.form.isNew)

            viewModel.type { copy(server = "new.example") }
            viewModel.onEvent(EditProxyEvent.Save)
            runCurrent()

            val sent = client.sentOf<TdApi.EditProxy>().single()
            assertEquals(Triple(id, "new.example", false), Triple(sent.proxyId, sent.proxy.server, sent.enable))
            assertEquals("Work", sent.comment)
        }

    @Test
    fun `a proxy that is gone shows not found`() =
        runTest {
            assertEquals(EditProxyUiState.Error(DomainError.NotFound), viewModel(proxyId = 42).uiState.value)
        }

    @Test
    fun `check pings the proxy as typed`() =
        runTest {
            val viewModel = viewModel()
            viewModel.type { copy(server = "proxy.example", port = "1080") }

            viewModel.onEvent(EditProxyEvent.Check)
            runCurrent()
            assertEquals(ProxyCheck.Available(120.milliseconds), viewModel.form.check)

            viewModel.type { copy(port = "1081") }
            assertEquals(null, viewModel.form.check)
        }

    @Test
    fun `a proxy TDLib refuses keeps the form and shows why`() =
        runTest {
            proxies.refuse = TdError(400, "Wrong secret")
            val viewModel = viewModel()
            viewModel.type { copy(kind = ProxyKind.Mtproto, server = "proxy.example", port = "443", secret = "zz") }

            viewModel.onEvent(EditProxyEvent.Save)
            runCurrent()

            assertEquals(EditProxyEffect.ShowError(DomainError.InputRejected), viewModel.effects.first())
            assertFalse(viewModel.form.saving)
        }

    @Test
    fun `what was typed survives process death, but not the password`() =
        runTest {
            viewModel().type {
                copy(
                    server = "proxy.example",
                    port = "1080",
                    username = "ada",
                    password = "secret",
                    name = "Home",
                )
            }

            val restored = viewModel().form.form

            assertEquals(ProxyForm(server = "proxy.example", port = "1080", username = "ada", name = "Home"), restored)
            assertFalse("secret" in savedStateHandle.keys().map { savedStateHandle.get<Any>(it) })
        }
}
