package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.domain.model.Proxy
import dev.kyralabs.pingogram.core.domain.model.ProxyType
import dev.kyralabs.pingogram.core.domain.model.SavedProxy
import dev.kyralabs.pingogram.core.testing.FakeAccountGraphs
import dev.kyralabs.pingogram.core.testing.FakeProxyRepository
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Every account's proxies in step with the active account's (owner decision 2026-11-27): the same proxy found by its
 * server, port, protocol and protocol values, the first sync adding without removing, and a new account given the list.
 */
class ProxyMirrorTest {
    private val first = FakeProxyRepository()
    private val second = FakeProxyRepository(firstId = 100)
    private val graphs =
        FakeAccountGraphs(persistentListOf(proxyAccount(0, first), proxyAccount(1, second)), activeSlot = 0)

    private fun TestScope.mirror() = ProxyMirror(graphs, backgroundScope)

    private fun saved(
        id: Int,
        proxy: Proxy,
        inUse: Boolean = false,
    ) = SavedProxy(id, proxy, isEnabled = inUse, lastUsedDate = 0, name = "")

    private fun FakeProxyRepository.proxies() = saved.value.map { it.proxy }

    @Test
    fun `two proxies on one server and port with different secrets are two, the server's letter case aside`() {
        assertFalse(MTPROTO.sameAs(MTPROTO.copy(type = ProxyType.Mtproto("ee00"))))
        assertFalse(SOCKS.sameAs(SOCKS.copy(type = ProxyType.Socks5("ada", "other"))))
        assertTrue(MTPROTO.sameAs(MTPROTO.copy(server = "PROXY.EXAMPLE.ORG")))
    }

    @Test
    fun `the first sync adds what an account lacks, removes nothing and uses the active account's proxy`() =
        runTest {
            first.saved.value = listOf(saved(1, MTPROTO, inUse = true), saved(2, SOCKS))
            val own = Proxy("own.example.org", 1080, ProxyType.Socks5())
            val otherSecret = MTPROTO.copy(type = ProxyType.Mtproto("ee00"))
            second.saved.value = listOf(saved(100, own, inUse = true), saved(101, SOCKS), saved(102, otherSecret))

            mirror().catchUp()

            assertEquals(listOf(own, SOCKS, otherSecret, MTPROTO), second.proxies())
            assertEquals(MTPROTO, second.inUse)
            assertEquals(MTPROTO, first.inUse)
        }

    @Test
    fun `without a proxy in use on the active account, another account keeps its own`() =
        runTest {
            first.saved.value = listOf(saved(1, MTPROTO))
            val own = Proxy("own.example.org", 1080, ProxyType.Socks5())
            second.saved.value = listOf(saved(100, own, inUse = true))

            mirror().catchUp()

            assertEquals(own, second.inUse)
            assertEquals(listOf(own, MTPROTO), second.proxies())
        }

    @Test
    fun `a new account, made active as it signs in, starts with the list and the proxy in use`() =
        runTest {
            first.saved.value = listOf(saved(1, MTPROTO, inUse = true), saved(2, SOCKS))
            val mirror = mirror()
            mirror.catchUp()
            val added = FakeProxyRepository(firstId = 200)

            graphs.accountList.value =
                persistentListOf(proxyAccount(0, first), proxyAccount(1, second), proxyAccount(2, added))
            graphs.active.value = 2
            mirror.catchUp()

            assertEquals(listOf(MTPROTO, SOCKS), added.proxies())
            assertEquals(MTPROTO, added.inUse)
        }

    internal companion object {
        val MTPROTO = Proxy("proxy.example.org", 443, ProxyType.Mtproto("dd00"))
        val SOCKS = Proxy("proxy.example.org", 443, ProxyType.Socks5("ada", "secret"))
    }
}
