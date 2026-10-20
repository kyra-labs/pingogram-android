package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.AutoDownloadNetwork
import dev.kyralabs.pingogram.core.domain.model.AutoDownloadRules
import dev.kyralabs.pingogram.core.domain.model.AutoDownloadSettings
import kotlinx.coroutines.flow.Flow

/**
 * The automatic download settings (Settings → Data and storage, `docs/PERFORMANCE.md` §7): what conversations download
 * by themselves on each kind of network. Kept on this device across logouts, like the Appearance settings.
 */
interface AutoDownloadRepository {
    /** The settings, then every change. Never fails: settings that cannot be read count as the defaults. */
    val settings: Flow<AutoDownloadSettings>

    /** Saves the [rules] of [network]; fails with `DomainError.Unknown` if they cannot be written (a full disk). */
    suspend fun setRules(
        network: AutoDownloadNetwork,
        rules: AutoDownloadRules,
    ): Outcome<Unit>
}

/**
 * The automatic download rules in effect: the settings' rules for the network the device uses now
 * ([AutoDownloadSettings.rulesOn]), for conversations to follow.
 */
interface AutoDownloadPolicy {
    /** The rules in effect now, then every change, as the network or the settings change. */
    val rules: Flow<AutoDownloadRules>
}
