package dev.kyralabs.pingogram.core.data.repository

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Proxy
import dev.kyralabs.pingogram.core.domain.model.ProxyType
import dev.kyralabs.pingogram.core.domain.model.SavedProxy
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds

class TdlibProxyRepositoryTest {
    private val client = FakeTdlibClient()
    private val repository = TdlibProxyRepository(client)

    /** What TDLib keeps; GetProxies answers with it. */
    private var saved = arrayOf<TdApi.AddedProxy>()

    private fun answerProxies() = client.on(TdApi.GetProxies::class) { TdResult.Success(TdApi.AddedProxies(saved)) }

    @Test
    fun `lists the proxies of every protocol`() =
        runTest {
            saved =
                arrayOf(
                    TdApi.AddedProxy(
                        1,
                        0,
                        false,
                        "",
                        TdApi.Proxy("socks.example", 1080, TdApi.ProxyTypeSocks5("a", "b")),
                    ),
                    TdApi.AddedProxy(
                        2,
                        100,
                        true,
                        "Work",
                        TdApi.Proxy("http.example", 8080, TdApi.ProxyTypeHttp("", "", true)),
                    ),
                    TdApi.AddedProxy(3, 0, false, "", TdApi.Proxy("mt.example", 443, TdApi.ProxyTypeMtproto("dd00"))),
                )
            answerProxies()

            assertEquals(
                Outcome.Success(
                    persistentListOf(
                        SavedProxy(1, Proxy("socks.example", 1080, ProxyType.Socks5("a", "b")), false, 0, ""),
                        SavedProxy(2, Proxy("http.example", 8080, ProxyType.Http(httpOnly = true)), true, 100, "Work"),
                        SavedProxy(3, Proxy("mt.example", 443, ProxyType.Mtproto("dd00")), false, 0, ""),
                    ),
                ),
                repository.proxies.first(),
            )
        }

    @Test
    fun `reads the proxies again after a change`() =
        runTest {
            answerProxies()
            client.on(TdApi.AddProxy::class) { request ->
                val added = TdApi.AddedProxy(7, 0, request.enable, request.comment, request.proxy)
                saved += added
                TdResult.Success(added)
            }
            client.on(TdApi.DisableProxy::class) {
                saved =
                    saved.map { TdApi.AddedProxy(it.id, it.lastUsedDate, false, it.comment, it.proxy) }.toTypedArray()
                TdResult.Success(TdApi.Ok())
            }

            repository.proxies.test {
                assertEquals(Outcome.Success(persistentListOf<SavedProxy>()), awaitItem())

                val proxy = Proxy("socks.example", 1080, ProxyType.Socks5())
                assertEquals(
                    Outcome.Success(SavedProxy(7, proxy, true, 0, "Home")),
                    repository.add(proxy, "Home", true),
                )
                assertEquals(Outcome.Success(persistentListOf(SavedProxy(7, proxy, true, 0, "Home"))), awaitItem())

                assertEquals(Outcome.Success(Unit), repository.disable())
                assertEquals(Outcome.Success(persistentListOf(SavedProxy(7, proxy, false, 0, "Home"))), awaitItem())
            }
        }

    @Test
    fun `edits, enables and removes a proxy by its id`() =
        runTest {
            val proxy = Proxy("socks.example", 1081, ProxyType.Socks5("user", "pass"))
            client.on(
                TdApi.EditProxy::class,
            ) { TdResult.Success(TdApi.AddedProxy(it.proxyId, 0, it.enable, it.comment, it.proxy)) }
            client.on(TdApi.EnableProxy::class, TdResult.Success(TdApi.Ok()))
            client.on(TdApi.RemoveProxy::class, TdResult.Success(TdApi.Ok()))

            assertEquals(Outcome.Success(SavedProxy(4, proxy, false, 0, "")), repository.edit(4, proxy, "", false))
            assertEquals(Outcome.Success(Unit), repository.enable(4))
            assertEquals(Outcome.Success(Unit), repository.remove(4))
            val edited = client.sentOf<TdApi.EditProxy>().single()
            assertEquals(4 to 1081, edited.proxyId to edited.proxy.port)
            assertEquals("user", (edited.proxy.type as TdApi.ProxyTypeSocks5).username)
            assertEquals(4, client.sentOf<TdApi.EnableProxy>().single().proxyId)
            assertEquals(4, client.sentOf<TdApi.RemoveProxy>().single().proxyId)
        }

    @Test
    fun `a proxy TDLib refuses is rejected input`() =
        runTest {
            client.on(TdApi.AddProxy::class, TdResult.Failure(TdError(400, "Wrong port number")))

            assertEquals(
                Outcome.Failure(DomainError.InputRejected),
                repository.add(Proxy("socks.example", 0, ProxyType.Socks5()), "", false),
            )
        }

    @Test
    fun `pings a proxy, and a proxy that does not answer is a network error`() =
        runTest {
            client.on(TdApi.PingProxy::class, TdResult.Success(TdApi.Seconds(0.125)))
            val proxy = Proxy("socks.example", 1080, ProxyType.Socks5())

            assertEquals(Outcome.Success(125.milliseconds), repository.ping(proxy))

            client.on(TdApi.PingProxy::class, TdResult.Failure(TdError(400, "Connection refused")))
            assertEquals(Outcome.Failure(DomainError.Network), repository.ping(proxy))
        }

    @Test
    fun `logs neither the address nor the credentials`() {
        val text =
            SavedProxy(
                1,
                Proxy("socks.example", 1080, ProxyType.Socks5("user", "pass")),
                true,
                0,
                "Home",
            ).toString()

        listOf("socks.example", "1080", "user", "pass", "Home").forEach { assertFalse(it in text, it) }
    }
}
