package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.data.di.Accounts
import dev.kyralabs.pingogram.core.domain.model.AuthStep
import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.model.DeviceAccount
import dev.kyralabs.pingogram.core.domain.model.DeviceAccountLimits
import dev.kyralabs.pingogram.core.domain.repository.DeviceAccountsRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [DeviceAccountsRepository] over [Accounts]: each signed-in account's user and unread chats from its own graph
 * (`docs/adr/0031-multiple-accounts.md`).
 */
@Singleton
class DefaultDeviceAccountsRepository
    @Inject
    constructor(
        private val devices: Accounts,
    ) : DeviceAccountsRepository {
        override val isAvailable: Boolean = IS_AVAILABLE

        @OptIn(ExperimentalCoroutinesApi::class)
        override val accounts: Flow<ImmutableList<DeviceAccount>> =
            devices.registry
                .flatMapLatest { registry ->
                    val all = devices.all()
                    if (all.isEmpty()) return@flatMapLatest flowOf(persistentListOf())
                    combine(all.map { account -> account.summary(isActive = account.slot.index == registry.active) }) {
                        it.filterNotNull().toImmutableList()
                    }
                }.distinctUntilChanged()

        override val isAdding: Flow<Boolean> =
            combine(devices.adding, accounts) { adding, signedIn ->
                adding != null && signedIn.any { it.slot != adding.slot }
            }.distinctUntilChanged()

        override fun switchTo(slot: Int) = devices.switchTo(slot)

        override fun addAccount() {
            devices.add()
        }

        override fun cancelAdding() = devices.cancelAdding()

        /** The account's row, or null while it is not signed in. */
        private fun Accounts.Account.summary(isActive: Boolean): Flow<DeviceAccount?> {
            val users = component.userRepository()
            val me =
                combine(users.myUserId, users.users) { id, all -> id?.let(all::get) }
            val unread = component.chatRepository().unreadChatCount(ChatListType.Main).map { it.unreadCount }
            val signedIn = component.authRepository().authStep.map { it == AuthStep.Ready }
            return combine(signedIn, me, unread) { ready, user, unreadChats ->
                if (ready) DeviceAccount(slot.index, user, unreadChats, isActive) else null
            }
        }

        override val canAddAccount: Flow<Boolean> =
            combine(accounts, devices.adding) { signedIn, adding ->
                adding == null && signedIn.size < DeviceAccountLimits.of(signedIn)
            }.distinctUntilChanged()

        internal companion object {
            /**
             * Off until every part of the app follows each account (stage 4 of ADR 0031: notifications, calls, push);
             * before that, a second account's notifications would show as the first's.
             */
            const val IS_AVAILABLE = false
        }
    }
