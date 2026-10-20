package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * "Keep connected" (`foss` only, `PushDelivery.TdlibConnection`): whether a foreground service keeps Pingogram running
 * in the background, so TDLib's connection stays open and new messages notify at once, at the cost of battery
 * (`docs/adr/0003-keep-connected-foreground-service.md`). Off by default; kept on this device across logouts, like the
 * Appearance settings.
 */
interface KeepConnectedRepository {
    /** Whether it is on, then every change. Never fails: a setting that cannot be read counts as off. */
    val keepConnected: Flow<Boolean>

    /** Saves whether it is on; fails with `DomainError.Unknown` if it cannot be written (a full disk). */
    suspend fun setKeepConnected(enabled: Boolean): Outcome<Unit>
}
