package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.data.di.Accounts
import dev.kyralabs.pingogram.core.demo.DemoIncomingCall
import dev.kyralabs.pingogram.core.domain.repository.DemoRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** [DemoRepository] over [Accounts], which keeps the demo's graph beside the accounts. */
@Singleton
class DefaultDemoRepository
    @Inject
    constructor(
        private val accounts: Accounts,
    ) : DemoRepository {
        override val isDemo: StateFlow<Boolean> get() = accounts.isDemo

        override suspend fun enter() = accounts.enterDemo()

        override suspend fun leave() = accounts.leaveDemo()

        override suspend fun receiveCall(userId: Long) {
            accounts.demoClient()?.send(DemoIncomingCall(userId))
        }
    }
