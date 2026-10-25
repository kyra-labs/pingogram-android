package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.data.di.Accounts
import dev.kyralabs.pingogram.core.domain.repository.DevicePushRepository
import dev.kyralabs.pingogram.core.domain.repository.PushRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [DevicePushRepository] over [Accounts] (`docs/adr/0031-multiple-accounts.md`): starts each account's
 * [PushRepository] as the account comes, hands a new token to every one, and a push to the account whose
 * [PushRepository.receiverId] the push names ([PushRepository.receiverIdOf], asked of the first account, as any
 * account's TDLib reads it). A push that names none of them, as before an account registered in this process, goes to
 * every account: TDLib drops one that is not its own. With one account, a push goes to it straight away.
 */
@Singleton
class DefaultDevicePushRepository
    @Inject
    constructor(
        private val accounts: Accounts,
        @ApplicationScope private val scope: CoroutineScope,
    ) : DevicePushRepository {
        private val started = AtomicBoolean(false)

        override fun start() {
            if (!started.compareAndSet(false, true)) return
            scope.launch {
                // Starting is idempotent per account, so each change of the registry starts the new ones only.
                accounts.registry.collect { accounts.all().forEach { it.component.pushRepository().start() } }
            }
        }

        override fun onNewToken(token: String) = pushRepositories().forEach { it.onNewToken(token) }

        override suspend fun processPush(
            data: Map<String, String>,
            sentTimeMillis: Long,
            notificationSound: String?,
        ): Outcome<Unit> {
            accounts.awaitLoaded()
            val all = pushRepositories()
            val targets =
                if (all.size == 1) {
                    all
                } else {
                    val receiver = all.first().receiverIdOf(data)
                    all.filter { receiver != null && it.receiverId.value == receiver }.ifEmpty { all }
                }
            val results =
                coroutineScope {
                    targets.map { async { it.processPush(data, sentTimeMillis, notificationSound) } }.awaitAll()
                }
            return results.firstOrNull { it is Outcome.Success } ?: results.first()
        }

        private fun pushRepositories(): List<PushRepository> = accounts.all().map { it.component.pushRepository() }
    }
