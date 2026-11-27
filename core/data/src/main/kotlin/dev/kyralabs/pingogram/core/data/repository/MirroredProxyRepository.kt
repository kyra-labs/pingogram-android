package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.data.di.Accounts
import dev.kyralabs.pingogram.core.domain.model.Proxy
import dev.kyralabs.pingogram.core.domain.model.SavedProxy
import dev.kyralabs.pingogram.core.domain.repository.ProxyRepository
import javax.inject.Inject

/**
 * The active account's [ProxyRepository] ([active]), each of whose changes [mirror] then makes on every other account
 * (owner decision 2026-11-27): adding, editing, using, turning off and removing a proxy. What it reads and pings is the
 * active account's alone.
 */
class MirroredProxyRepository(
    private val active: ProxyRepository,
    private val mirror: ProxyMirror,
) : ProxyRepository by active {
    /** The account active now's proxies; a new one is made for each injection, as the active account changes. */
    @Inject
    constructor(accounts: Accounts, mirror: ProxyMirror) : this(accounts.active.proxyRepository(), mirror)

    override suspend fun add(
        proxy: Proxy,
        name: String,
        enable: Boolean,
    ): Outcome<SavedProxy> =
        active.add(proxy, name, enable).alsoMirrored {
            if (enable) {
                use(proxy, name)
            } else if (matching(proxy) == null) {
                add(proxy, name, enable = false)
            }
        }

    override suspend fun edit(
        id: Int,
        proxy: Proxy,
        name: String,
        enable: Boolean,
    ): Outcome<SavedProxy> {
        val before = active.saved()?.firstOrNull { it.id == id }?.proxy
        return active.edit(id, proxy, name, enable).alsoMirrored {
            val have = before?.let { matching(it) }
            if (have == null) add(proxy, name, enable) else edit(have.id, proxy, name, enable)
        }
    }

    override suspend fun enable(id: Int): Outcome<Unit> {
        val chosen = active.saved()?.firstOrNull { it.id == id }
        return active.enable(id).alsoMirrored { chosen?.let { use(it.proxy, it.name) } }
    }

    override suspend fun disable(): Outcome<Unit> = active.disable().alsoMirrored { disable() }

    override suspend fun remove(id: Int): Outcome<Unit> {
        val removed = active.saved()?.firstOrNull { it.id == id }?.proxy
        return active.remove(id).alsoMirrored { removed?.let { matching(it) }?.let { remove(it.id) } }
    }

    /** [this], and once it succeeded, [change] made on every other account. */
    private fun <T> Outcome<T>.alsoMirrored(change: suspend ProxyRepository.() -> Unit): Outcome<T> {
        if (this is Outcome.Success) mirror.mirror(change)
        return this
    }
}
