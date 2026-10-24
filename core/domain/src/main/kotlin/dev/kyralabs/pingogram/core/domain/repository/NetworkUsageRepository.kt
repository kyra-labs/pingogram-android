package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.NetworkUsage

/**
 * How much data Pingogram used (Settings → Data and storage → Network usage), as TDLib counts it on this device; it
 * asks Telegram nothing.
 */
interface NetworkUsageRepository {
    /** What every network carried since the statistics were last reset. */
    suspend fun usage(): Outcome<NetworkUsage>

    /** Starts counting anew from now. */
    suspend fun reset(): Outcome<Unit>
}
