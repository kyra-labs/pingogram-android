package dev.kyralabs.pingogram.core.data.di

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.IO
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.data.account.AccountRegistry
import dev.kyralabs.pingogram.core.data.account.AccountRegistryFile
import dev.kyralabs.pingogram.core.domain.model.AuthStep
import dev.kyralabs.pingogram.core.tdlib.AccountSlot
import dev.kyralabs.pingogram.core.tdlib.AccountStorage
import dev.kyralabs.pingogram.core.tdlib.AuthState
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import dev.kyralabs.pingogram.core.tdlib.TdlibClientFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * The accounts on the device and the one the screens show (`docs/adr/0031-multiple-accounts.md`). Each account has
 * its graph ([AccountComponent]), its TDLib client and a scope its repositories run in, all kept while it is on the
 * device; every account's client runs, so each gets its updates. The first account's slot is always there, with the
 * app's [TdlibClient]; the others' clients come from [TdlibClientFactory].
 *
 * Which accounts there are is in a small file ([AccountRegistryFile]), read on the IO dispatcher as the app starts
 * ([start]). Something that asks for [active] before then reads it itself, on its own thread: a millisecond's read,
 * where the activity comes 30–40 ms after the application's start, so in practice only tests do (Hilt's test
 * application never calls [start]).
 *
 * One function per change of the registry and the parts each needs, hence `TooManyFunctions`.
 */
@Suppress("TooManyFunctions")
@Singleton
class Accounts internal constructor(
    private val firstClient: TdlibClient,
    private val factory: AccountFactory,
    private val registryFile: AccountRegistryFile,
    private val appScope: CoroutineScope,
    private val io: CoroutineDispatcher,
) {
    @Inject
    constructor(
        firstClient: TdlibClient,
        factory: DefaultAccountFactory,
        @ApplicationContext context: Context,
        @ApplicationScope appScope: CoroutineScope,
        @Dispatcher(IO) io: CoroutineDispatcher,
    ) : this(
        firstClient = firstClient,
        factory = factory,
        registryFile = AccountRegistryFile { context.noBackupFilesDir.resolve(AccountRegistryFile.FILE_NAME) },
        appScope = appScope,
        io = io,
    )

    /** One account on the device: its slot, client, graph, and the scope its repositories run in. */
    class Account internal constructor(
        val slot: AccountSlot,
        val client: TdlibClient,
        val component: AccountComponent,
        internal val scope: CoroutineScope,
    )

    /** The account being added while it signs in, and the one shown before it, which shows again if it is left. */
    data class Adding(
        val slot: Int,
        val previous: Int,
    )

    // Changed only under this object's lock.
    private val bySlot = mutableMapOf<AccountSlot, Account>()
    private val loaded = CompletableDeferred<Unit>()
    private var registryRead = false

    private val mutableRegistry = MutableStateFlow(AccountRegistry.First)

    /** The slots on the device in the order they were added, and the active one; the first account's until read. */
    val registry: StateFlow<AccountRegistry> = mutableRegistry.asStateFlow()

    private val mutableAdding = MutableStateFlow<Adding?>(null)
    val adding: StateFlow<Adding?> = mutableAdding.asStateFlow()

    /** The active account's graph. */
    val active: AccountComponent
        get() {
            if (!loaded.isCompleted) readRegistry()
            return account(AccountSlot(mutableRegistry.value.active)).component
        }

    /**
     * Makes the first account's graph, whose caches subscribe to its client's updates before the application starts
     * that client, then reads which other accounts there are and starts them, in the background.
     */
    fun start() {
        account(AccountSlot.First).component.updateCaches()
        appScope.launch(io) { readRegistry() }
    }

    /** Reads which accounts there are, once, and starts them; whichever of [start] and [active] comes first. */
    private fun readRegistry() {
        synchronized(this) {
            if (registryRead) return
            registryRead = true
            val registry = registryFile.read()
            mutableRegistry.value = registry
            registry.slots.forEach { account(AccountSlot(it)) }
        }
        loaded.complete(Unit)
    }

    /** The first slot's account, which is always there, signed in or not; its client is the app's [TdlibClient]. */
    val firstAccount: Account get() = account(AccountSlot.First)

    /** Suspends until the registry is read, so [registry] holds the device's accounts. */
    suspend fun awaitLoaded() = loaded.await()

    /** Every account on the device, in [registry]'s order. */
    fun all(): List<Account> = synchronized(this) { mutableRegistry.value.slots.map { account(AccountSlot(it)) } }

    /** Shows [slot]'s account, from the next screens on, and keeps it shown at the next start. */
    fun switchTo(slot: Int) {
        synchronized(this) {
            val registry = mutableRegistry.value
            if (slot == registry.active || slot !in registry.slots) return
            save(registry.copy(active = slot))
        }
    }

    /** Adds an account in a free slot, which signs in and is shown; returns its slot, or null when none is free. */
    fun add(): Int? =
        synchronized(this) {
            val registry = mutableRegistry.value
            val slot = (0 until AccountSlot.MAX_ACCOUNTS).firstOrNull { it !in registry.slots } ?: return null
            mutableAdding.value = Adding(slot, previous = registry.active)
            save(AccountRegistry(registry.slots + slot, active = slot))
            account(AccountSlot(slot))
            slot
        }

    /** Leaves the sign-in of the account being added: it goes, and the one shown before it shows again. */
    fun cancelAdding() {
        val adding = mutableAdding.value ?: return
        mutableAdding.value = null
        val account = synchronized(this) { bySlot[AccountSlot(adding.slot)] }
        remove(adding.slot, showInstead = adding.previous)
        // A slot that never signed in has nothing to log out of: closing its TDLib instance ends it.
        if (account != null && adding.slot != 0) appScope.launch { account.client.send(TdApi.Close()) }
    }

    /**
     * [slot]'s account logged out, or was refused as another slot has its user. While another account is on the
     * device, it goes from the registry, and the one at [showInstead] (or the first other) shows if it was shown; its
     * client then closes for good and its storage is deleted. The first slot stays, empty, for an account added later,
     * as its client is the app's. The last account stays, on the login steps.
     */
    fun remove(
        slot: Int,
        showInstead: Int? = null,
    ) {
        synchronized(this) {
            val registry = mutableRegistry.value
            val others = registry.slots - slot
            if (slot !in registry.slots || others.isEmpty()) return
            val active =
                if (registry.active != slot) registry.active else showInstead?.takeIf { it in others } ?: others.first()
            if (mutableAdding.value?.slot == slot) mutableAdding.value = null
            save(AccountRegistry(others, active))
        }
        if (slot != 0) appScope.launch { closeAndDelete(AccountSlot(slot)) }
    }

    private suspend fun closeAndDelete(slot: AccountSlot) {
        val account = synchronized(this) { bySlot[slot] } ?: return
        account.client.authState.first { it == AuthState.Closed }
        synchronized(this) { bySlot.remove(slot) }
        account.scope.cancel()
        withContext(io) { factory.deleteStorage(slot) }
    }

    /** Whether [slot] is still on the device, which decides whether its client starts again after closing. */
    private fun kept(slot: AccountSlot) = slot.index in mutableRegistry.value.slots

    private fun account(slot: AccountSlot): Account = synchronized(this) { bySlot[slot] ?: startAccount(slot) }

    /**
     * Makes [slot]'s graph and, for the accounts after the first, its caches, then starts their client. The first
     * account's caches come from [start], before the application starts its client; Hilt's tests, which make the
     * first account's graph without [start], make only the repositories they inject.
     */
    private fun startAccount(slot: AccountSlot): Account {
        val client = if (slot == AccountSlot.First) firstClient else factory.client(slot) { kept(slot) }
        val scope = CoroutineScope(appScope.coroutineContext + SupervisorJob(appScope.coroutineContext.job))
        val component = factory.component(slot, client, scope)
        val account = Account(slot, client, component, scope)
        bySlot[slot] = account
        scope.launch { followSignIn(account) }
        if (slot != AccountSlot.First) {
            component.updateCaches()
            client.start()
            // The application starts the first account's with the other starters, off the main thread.
            component.networkTypeReporter().start()
        }
        return account
    }

    /**
     * Watches [account]'s login: signed in, it is no longer being added, unless another slot has its user, which it
     * is then logged out of; logged out after being signed in, it goes from the device. `collectLatest`: the login
     * steps come from TDLib's updates, which waiting inside the collector (for the user, below) would hold up.
     */
    private suspend fun followSignIn(account: Account) {
        val slot = account.slot.index
        var signedIn = false
        account.component.authRepository().authStep.collectLatest { step ->
            when {
                step == AuthStep.Ready && !signedIn -> {
                    signedIn = true
                    if (isSignedInElsewhere(account)) {
                        remove(slot, showInstead = mutableAdding.value?.previous)
                        account.component.authRepository().logOut()
                    } else if (mutableAdding.value?.slot == slot) {
                        mutableAdding.value = null
                    }
                }

                signedIn && (step == AuthStep.LoggingOut || step == AuthStep.Closed) -> {
                    signedIn = false
                    remove(slot)
                }
            }
        }
    }

    private suspend fun isSignedInElsewhere(account: Account): Boolean {
        if (mutableRegistry.value.slots.size < 2) return false
        val user =
            account.component
                .userRepository()
                .myUserId
                .first { it != null }
        return all().any { other ->
            other.slot != account.slot && other.component
                .userRepository()
                .myUserId.value == user
        }
    }

    /** The device's accounts' login steps together ([combinedAuthStep]), then every change. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun combinedAuthSteps(): Flow<AuthStep> =
        registry.flatMapLatest {
            combine(all().map { account -> account.component.authRepository().authStep }) { steps ->
                combinedAuthStep(steps.toList())
            }
        }

    /** Logs every account on the device out; the first failure, if any. */
    suspend fun logOutAll(): Outcome<Unit> =
        all()
            .map { it.component.authRepository().logOut() }
            .firstOrNull { it is Outcome.Failure } ?: Outcome.Success(Unit)

    private fun save(registry: AccountRegistry) {
        mutableRegistry.value = registry
        appScope.launch(io) { registryFile.write(registry) }
    }
}

/** The login step of all [steps] together, for what belongs to the app: signed in while one account is. */
fun combinedAuthStep(steps: List<AuthStep>): AuthStep =
    when {
        steps.any { it == AuthStep.Ready } -> AuthStep.Ready
        steps.all { it == AuthStep.Initializing } -> AuthStep.Initializing
        else -> steps.first { it != AuthStep.Initializing && it != AuthStep.Ready }
    }

/** What [Accounts] makes for each account, and deletes once it leaves the device. */
interface AccountFactory {
    fun client(
        slot: AccountSlot,
        kept: () -> Boolean,
    ): TdlibClient

    fun component(
        slot: AccountSlot,
        client: TdlibClient,
        scope: CoroutineScope,
    ): AccountComponent

    /** Blocking: off the main thread. */
    fun deleteStorage(slot: AccountSlot)
}

/** The accounts' clients from [TdlibClientFactory], their graphs from Dagger, their storage from [AccountStorage]. */
class DefaultAccountFactory
    @Inject
    constructor(
        private val clients: TdlibClientFactory,
        private val storage: AccountStorage,
        private val dependencies: Provider<AccountDependencies>,
    ) : AccountFactory {
        override fun client(
            slot: AccountSlot,
            kept: () -> Boolean,
        ) = clients.create(slot, kept)

        override fun component(
            slot: AccountSlot,
            client: TdlibClient,
            scope: CoroutineScope,
        ): AccountComponent = DaggerAccountComponent.factory().create(slot, client, scope, dependencies.get())

        override fun deleteStorage(slot: AccountSlot) = storage.delete(slot)
    }
