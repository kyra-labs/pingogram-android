package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Transfers
import kotlinx.coroutines.flow.StateFlow

/**
 * The uploads and downloads the user started, which continue while the app is in the background (`REQUIREMENTS.md`
 * FR-N4, `docs/ARCHITECTURE.md` Files and media): the files of the messages being sent from this device, and the
 * downloads started with `DownloadPriority.Requested`. Downloads TDLib or the app start by themselves (what is on
 * screen, automatic downloads) are not among them.
 */
interface TransferRepository {
    /** What is on its way now, then every change; [Transfers.None] after logging out. */
    val transfers: StateFlow<Transfers>

    /**
     * Cancels every transfer [transfers] counts: the downloads stop (what was downloaded stays) and the messages being
     * sent are deleted, which is how TDLib cancels their uploads. Fails with the first request that fails, after trying
     * them all.
     */
    suspend fun cancelAll(): Outcome<Unit>
}
