package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.domain.model.ConnectionStatus
import kotlinx.coroutines.flow.Flow

interface ConnectionRepository {
    /** The current status first, then every change. */
    val connectionStatus: Flow<ConnectionStatus>
}
