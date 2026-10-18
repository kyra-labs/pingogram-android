package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.data.mapper.toConnectionStatus
import dev.kyralabs.pingogram.core.domain.model.ConnectionStatus
import dev.kyralabs.pingogram.core.domain.repository.ConnectionRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TdlibConnectionRepository
    @Inject
    constructor(
        client: TdlibClient,
    ) : ConnectionRepository {
        override val connectionStatus: Flow<ConnectionStatus> = client.connectionState.map { it.toConnectionStatus() }
    }
