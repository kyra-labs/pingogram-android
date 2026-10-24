package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Proxy
import dev.kyralabs.pingogram.core.domain.model.SavedProxy
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration

/**
 * The proxies Pingogram reaches Telegram through (`docs/adr/0027-proxies.md`, Settings → Data and storage → Proxy).
 * TDLib keeps them in its database, credentials included, and connects through the enabled one; Pingogram stores none
 * itself. A proxy the server refuses to take (a bad address, port or secret) fails with `DomainError.InputRejected`.
 */
interface ProxyRepository {
    /**
     * The proxies the user added, in TDLib's order, read when collected and again after every change made here;
     * TDLib announces none of its own.
     */
    val proxies: Flow<Outcome<ImmutableList<SavedProxy>>>

    /** Adds [proxy] with the user's [name] (empty for none), connecting through it now if [enable]. */
    suspend fun add(
        proxy: Proxy,
        name: String,
        enable: Boolean,
    ): Outcome<SavedProxy>

    /** Changes proxy [id] to [proxy] and [name], connecting through it now if [enable]. */
    suspend fun edit(
        id: Int,
        proxy: Proxy,
        name: String,
        enable: Boolean,
    ): Outcome<SavedProxy>

    /** Connects through proxy [id] from now on, and through no other. */
    suspend fun enable(id: Int): Outcome<Unit>

    /** Connects to Telegram directly from now on. */
    suspend fun disable(): Outcome<Unit>

    /** Removes proxy [id]; TDLib connects directly if it was the one in use. */
    suspend fun remove(id: Int): Outcome<Unit>

    /**
     * How long Telegram takes to answer through [proxy] (`pingProxy`), which connects to the proxy and sends one
     * request through it; fails with `DomainError.Network` when it does not get through.
     */
    suspend fun ping(proxy: Proxy): Outcome<Duration>
}
