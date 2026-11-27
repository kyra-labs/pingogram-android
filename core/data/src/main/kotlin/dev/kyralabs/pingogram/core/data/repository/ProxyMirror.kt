package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Proxy
import dev.kyralabs.pingogram.core.domain.model.SavedProxy
import dev.kyralabs.pingogram.core.domain.repository.AccountGraphs
import dev.kyralabs.pingogram.core.domain.repository.ProxyRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps every account's proxies in step with the active account's (`docs/adr/0027-proxies.md`, owner decision
 * 2026-11-27): TDLib keeps proxies per account, so each account's client would otherwise reach Telegram its own way.
 * A proxy is the same on two accounts when its server (in any letter case), port, protocol and the protocol's own
 * values (an MTProto secret, a SOCKS5 or HTTP login) are ([sameAs]); TDLib's ids differ between accounts.
 *
 * - **Catching up** ([start], [catchUp]): each account, as the app starts and as one is added, gets the proxies it
 *   lacks and the proxy in use, if any, of the active account (of one already caught up, for a new one). Nothing is
 *   removed: an account may hold proxies of its own. A new account so starts with the current list and the same
 *   proxy, before it signs in.
 * - **Mirroring** ([mirror], which the active account's repository calls): each change made on the active account is
 *   made on every other account too, "no proxy" included, one after the other in the app's scope.
 *
 * What another account fails to take is left as it is: the change stands on the active account.
 */
@Singleton
class ProxyMirror
    @Inject
    constructor(
        private val graphs: AccountGraphs,
        @ApplicationScope private val scope: CoroutineScope,
    ) {
        private val lock = Mutex()
        private val caughtUp = mutableSetOf<Int>()

        fun start() {
            scope.launch { graphs.accounts.collect { catchUp() } }
        }

        /** Makes [change] on every account but the active one, after the changes made before it. */
        fun mirror(change: suspend ProxyRepository.() -> Unit) {
            scope.launch {
                lock.withLock { others().forEach { it.change() } }
            }
        }

        /**
         * Gives each account not caught up yet the proxies it lacks and the proxy in use of an account that is: the
         * active one at first, else the active one if it is caught up, else any, as a new account is usually made
         * active before it signs in.
         */
        internal suspend fun catchUp() =
            lock.withLock {
                val accounts = graphs.accounts.first()
                caughtUp.retainAll(accounts.map { it.slot }.toSet())
                val active = graphs.activeSlot.value
                val source = if (caughtUp.isEmpty() || active in caughtUp) active else caughtUp.first()
                val wanted = graphs.repositories(source)?.proxyRepository()?.saved()
                if (wanted != null) {
                    caughtUp += source
                    accounts
                        .filter { caughtUp.add(it.slot) }
                        .forEach { it.repositories.proxyRepository().takeUp(wanted) }
                }
            }

        private suspend fun others(): List<ProxyRepository> {
            val active = graphs.activeSlot.value
            return graphs.accounts
                .first()
                .filter { it.slot != active }
                .map { it.repositories.proxyRepository() }
        }

        private suspend fun ProxyRepository.takeUp(wanted: List<SavedProxy>) {
            val have = saved().orEmpty()
            wanted
                .filter { proxy -> have.none { it.proxy.sameAs(proxy.proxy) } }
                .forEach { add(it.proxy, it.name, enable = false) }
            wanted.firstOrNull { it.isEnabled }?.let { use(it.proxy, it.name) }
        }
    }

/** This account's proxies; null when TDLib could not list them. */
internal suspend fun ProxyRepository.saved(): List<SavedProxy>? = (proxies.first() as? Outcome.Success)?.value

/** This account's proxy that is [proxy] ([sameAs]); null when it has none. */
internal suspend fun ProxyRepository.matching(proxy: Proxy): SavedProxy? =
    saved()?.firstOrNull { it.proxy.sameAs(proxy) }

/** Connects through [proxy], added first with [name] where this account lacks it. */
internal suspend fun ProxyRepository.use(
    proxy: Proxy,
    name: String,
) {
    val have = matching(proxy)
    when {
        have == null -> add(proxy, name, enable = true)
        !have.isEnabled -> enable(have.id)
    }
}

/**
 * Whether [this] and [other] are the same proxy on two accounts: the same server in any letter case, port, protocol
 * and protocol values, so two proxies on one server and port that differ only in their secret or login are two.
 */
internal fun Proxy.sameAs(other: Proxy): Boolean =
    server.equals(other.server, ignoreCase = true) && port == other.port && type == other.type
