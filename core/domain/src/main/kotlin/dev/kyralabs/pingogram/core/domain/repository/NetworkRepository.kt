package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.domain.model.NetworkStatus
import kotlinx.coroutines.flow.StateFlow

/** The network the device uses for its data, and whether Data Saver holds it back (`docs/PERFORMANCE.md` §3). */
interface NetworkRepository {
    /** The network now, then every change. */
    val networkStatus: StateFlow<NetworkStatus>
}
