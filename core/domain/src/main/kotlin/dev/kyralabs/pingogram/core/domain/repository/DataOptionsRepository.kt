package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * Data and storage's options that Telegram's library keeps for the account (`docs/audit/feature-gaps-octogram.md`
 * P3): whether to connect over IPv6 where a data center offers both, and deleting every cloud draft.
 */
interface DataOptionsRepository {
    /** Whether connections prefer IPv6, then every change; false (Telegram's default) where it cannot be read. */
    val prefersIpv6: Flow<Boolean>

    /** Prefers IPv6 ([prefers]) or IPv4 for the next connections. */
    suspend fun setPrefersIpv6(prefers: Boolean): Outcome<Unit>

    /** Deletes the draft of every chat on all the user's devices, but secret chats', which stay on this device. */
    suspend fun deleteCloudDrafts(): Outcome<Unit>
}
