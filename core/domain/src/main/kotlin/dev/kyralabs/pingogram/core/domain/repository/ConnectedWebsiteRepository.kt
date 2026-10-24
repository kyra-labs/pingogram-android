package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ConnectedWebsite
import kotlinx.collections.immutable.ImmutableList

/**
 * The websites logged in with the user's Telegram account (Privacy and security), which the user may log out. Nothing
 * is cached: each call asks Telegram through TDLib, which announces no changes to them.
 */
interface ConnectedWebsiteRepository {
    /** Every website logged in with the account, in Telegram's order. */
    suspend fun websites(): Outcome<ImmutableList<ConnectedWebsite>>

    /** Logs website [websiteId] out of the account. */
    suspend fun disconnect(websiteId: Long): Outcome<Unit>

    /** Logs every website out of the account. */
    suspend fun disconnectAll(): Outcome<Unit>
}
