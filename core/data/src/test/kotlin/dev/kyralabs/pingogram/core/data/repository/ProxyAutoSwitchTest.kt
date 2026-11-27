package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.data.repository.ProxyMirrorTest.Companion.MTPROTO
import dev.kyralabs.pingogram.core.data.repository.ProxyMirrorTest.Companion.SOCKS
import dev.kyralabs.pingogram.core.domain.model.ConnectionStatus
import dev.kyralabs.pingogram.core.domain.model.Proxy
import dev.kyralabs.pingogram.core.domain.model.ProxyType
import dev.kyralabs.pingogram.core.domain.model.SavedProxy
import dev.kyralabs.pingogram.core.domain.repository.AccountGraph
import dev.kyralabs.pingogram.core.domain.repository.AccountRepositories
import dev.kyralabs.pingogram.core.domain.repository.ConnectionRepository
import dev.kyralabs.pingogram.core.testing.FakeAccountGraphs
import dev.kyralabs.pingogram.core.testing.FakeProxyAutoSwitchRepository
import dev.kyralabs.pingogram.core.testing.FakeProxyRepository
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import java.lang.reflect.Proxy as ReflectProxy

/**
 * The switch to a working proxy (FR-X27): after 15 seconds of connecting through the proxy in use, the quickest other
 * proxy that answers, only while the setting is on, never no proxy at all.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProxyAutoSwitchTest {
    private val status = MutableStateFlow(ConnectionStatus.ConnectingToProxy)
    private val settings = FakeProxyAutoSwitchRepository(enabled = true)
    private val slow = Proxy("slow.example.org", 1080, ProxyType.Socks5())
    private val proxies =
        FakeProxyRepository(
            listOf(saved(1, MTPROTO, inUse = true), saved(2, SOCKS), saved(3, slow)),
        ).apply {
            pings[SOCKS] = 40.milliseconds
            pings[slow] = 900.milliseconds
        }
    private val graphs = FakeAccountGraphs(persistentListOf(account()))

    private fun saved(
        id: Int,
        proxy: Proxy,
        inUse: Boolean = false,
    ) = SavedProxy(id, proxy, isEnabled = inUse, lastUsedDate = 0, name = "")

    private fun TestScope.started(): Pair<ProxyAutoSwitch, List<SavedProxy>> {
        val autoSwitch = ProxyAutoSwitch(graphs, settings, { proxies }, backgroundScope)
        val switched = mutableListOf<SavedProxy>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { autoSwitch.switched.toList(switched) }
        autoSwitch.start()
        runCurrent()
        return autoSwitch to switched
    }

    @Test
    fun `after 15 seconds of connecting through its proxy, the quickest that answers is used and said`() =
        runTest {
            val (_, switched) = started()

            advanceTimeBy(14.seconds)
            runCurrent()
            assertEquals(MTPROTO, proxies.inUse)

            advanceTimeBy(2.seconds)
            runCurrent()
            assertEquals(SOCKS, proxies.inUse)
            assertEquals(listOf(SOCKS), switched.map { it.proxy })
        }

    @Test
    fun `nothing switches while the setting is off, or once the account has connected`() =
        runTest {
            settings.enabled.value = false
            started()
            advanceTimeBy(20.seconds)
            runCurrent()
            assertEquals(MTPROTO, proxies.inUse)

            settings.enabled.value = true
            runCurrent()
            advanceTimeBy(10.seconds)
            status.value = ConnectionStatus.Ready
            advanceTimeBy(20.seconds)
            runCurrent()
            assertEquals(MTPROTO, proxies.inUse)
        }

    @Test
    fun `with no other proxy answering, the one in use stays and the proxy is never turned off`() =
        runTest {
            proxies.pings.clear()
            val (_, switched) = started()

            advanceTimeBy(46.seconds)
            runCurrent()

            assertEquals(MTPROTO, proxies.inUse)
            assertTrue(switched.isEmpty())
        }

    @Test
    fun `without a proxy in use there is nothing to switch`() =
        runTest {
            proxies.saved.value = proxies.saved.value.map { it.copy(isEnabled = false) }
            val (autoSwitch, switched) = started()

            autoSwitch.switchOnce()

            assertEquals(null, proxies.inUse)
            assertTrue(switched.isEmpty())
        }

    /** The account in slot 0, connecting as [status] says. */
    private fun account(): AccountGraph {
        val connection =
            object : ConnectionRepository {
                override val connectionStatus = status
            }
        return AccountGraph(
            0,
            ReflectProxy.newProxyInstance(
                AccountRepositories::class.java.classLoader,
                arrayOf(AccountRepositories::class.java),
            ) { self, method, args ->
                when (method.name) {
                    "connectionRepository" -> connection
                    "proxyRepository" -> proxies
                    "equals" -> self === args?.firstOrNull()
                    "hashCode" -> System.identityHashCode(self)
                    "toString" -> "AccountRepositories(0)"
                    else -> error("Not used: ${method.name}")
                }
            } as AccountRepositories,
        )
    }
}
