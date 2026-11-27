package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Proxy
import dev.kyralabs.pingogram.core.domain.model.SavedProxy
import dev.kyralabs.pingogram.core.domain.repository.ProxyRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.time.Duration

/**
 * [ProxyRepository] in memory, as TDLib keeps one account's proxies: ids from [firstId], at most one in use. Pings
 * answer [pings] (by proxy), else fail as an unreachable proxy does.
 */
class FakeProxyRepository(
    saved: List<SavedProxy> = emptyList(),
    private val firstId: Int = 1,
) : ProxyRepository {
    /** The proxies, in the order they were added. */
    val saved = MutableStateFlow(saved)

    val pings = mutableMapOf<Proxy, Duration>()

    override val proxies: Flow<Outcome<ImmutableList<SavedProxy>>> =
        this.saved.map { Outcome.Success(it.toImmutableList()) }

    /** The proxy in use; null for none. */
    val inUse: Proxy? get() = saved.value.firstOrNull { it.isEnabled }?.proxy

    override suspend fun add(
        proxy: Proxy,
        name: String,
        enable: Boolean,
    ): Outcome<SavedProxy> {
        val id = maxOf(firstId, (saved.value.maxOfOrNull { it.id } ?: 0) + 1)
        val added = SavedProxy(id, proxy, isEnabled = enable, lastUsedDate = 0, name = name)
        saved.value = saved.value.map { if (enable) it.copy(isEnabled = false) else it } + added
        return Outcome.Success(added)
    }

    override suspend fun edit(
        id: Int,
        proxy: Proxy,
        name: String,
        enable: Boolean,
    ): Outcome<SavedProxy> {
        val edited = SavedProxy(id, proxy, isEnabled = enable, lastUsedDate = 0, name = name)
        if (saved.value.none { it.id == id }) return Outcome.Failure(DomainError.NotFound)
        saved.value =
            saved.value.map {
                if (it.id == id) {
                    edited
                } else if (enable) {
                    it.copy(isEnabled = false)
                } else {
                    it
                }
            }
        return Outcome.Success(edited)
    }

    override suspend fun enable(id: Int): Outcome<Unit> {
        saved.value = saved.value.map { it.copy(isEnabled = it.id == id) }
        return Outcome.Success(Unit)
    }

    override suspend fun disable(): Outcome<Unit> {
        saved.value = saved.value.map { it.copy(isEnabled = false) }
        return Outcome.Success(Unit)
    }

    override suspend fun remove(id: Int): Outcome<Unit> {
        saved.value = saved.value.filterNot { it.id == id }
        return Outcome.Success(Unit)
    }

    override suspend fun ping(proxy: Proxy): Outcome<Duration> =
        pings[proxy]?.let { Outcome.Success(it) } ?: Outcome.Failure(DomainError.Network)
}
