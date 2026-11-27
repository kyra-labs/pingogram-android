package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.data.repository.ProxyMirrorTest.Companion.MTPROTO
import dev.kyralabs.pingogram.core.data.repository.ProxyMirrorTest.Companion.SOCKS
import dev.kyralabs.pingogram.core.testing.FakeAccountGraphs
import dev.kyralabs.pingogram.core.testing.FakeProxyRepository
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The active account's proxies with each change made on every other account too (owner decision 2026-11-27): adding,
 * editing, using, turning off and removing, and nothing where the active account refused it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MirroredProxyRepositoryTest {
    private val first = FakeProxyRepository()
    private val second = FakeProxyRepository(firstId = 100)
    private val graphs = FakeAccountGraphs(persistentListOf(proxyAccount(0, first), proxyAccount(1, second)))

    private fun TestScope.mirror() = ProxyMirror(graphs, backgroundScope)

    private fun FakeProxyRepository.proxies() = saved.value.map { it.proxy }

    @Test
    fun `each change on the active account is made on the others, no proxy included`() =
        runTest {
            val proxies = MirroredProxyRepository(first, mirror())

            proxies.add(MTPROTO, "Home", enable = true)
            runCurrent()
            assertEquals(MTPROTO, second.inUse)
            assertEquals(
                "Home",
                second.saved.value
                    .single()
                    .name,
            )

            val moved = MTPROTO.copy(port = 8443)
            proxies.edit(
                first.saved.value
                    .single()
                    .id,
                moved,
                "Home",
                enable = true,
            )
            runCurrent()
            assertEquals(listOf(moved), second.proxies())

            proxies.disable()
            runCurrent()
            assertNull(second.inUse)

            proxies.enable(
                first.saved.value
                    .single()
                    .id,
            )
            runCurrent()
            assertEquals(moved, second.inUse)

            proxies.add(SOCKS, "", enable = false)
            proxies.remove(
                first.saved.value
                    .first()
                    .id,
            )
            runCurrent()
            assertEquals(listOf(SOCKS), second.proxies())
            assertNull(second.inUse)
        }

    @Test
    fun `a change the active account refuses is not made elsewhere`() =
        runTest {
            val proxies = MirroredProxyRepository(first, mirror())

            proxies.edit(id = 42, MTPROTO, "", enable = true)
            runCurrent()

            assertTrue(second.saved.value.isEmpty())
        }
}
