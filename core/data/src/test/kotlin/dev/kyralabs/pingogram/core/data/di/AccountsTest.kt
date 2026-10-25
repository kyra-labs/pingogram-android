package dev.kyralabs.pingogram.core.data.di

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.data.account.AccountRegistry
import dev.kyralabs.pingogram.core.data.account.AccountRegistryFile
import dev.kyralabs.pingogram.core.data.repository.DefaultAccountGraphs
import dev.kyralabs.pingogram.core.data.repository.TdlibNetworkTypeReporter
import dev.kyralabs.pingogram.core.domain.model.AuthStep
import dev.kyralabs.pingogram.core.domain.repository.AuthRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.tdlib.AccountSlot
import dev.kyralabs.pingogram.core.tdlib.AuthState
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.lang.reflect.Proxy

class AccountsTest {
    @TempDir
    lateinit var dir: File

    private val registryFile get() = AccountRegistryFile { dir.resolve(AccountRegistryFile.FILE_NAME) }

    /** Each slot's client, login step and user, as the account's graph would have them. */
    private val clients = mutableMapOf<Int, FakeTdlibClient>()
    private val steps = mutableMapOf<Int, MutableStateFlow<AuthStep>>()
    private val users = mutableMapOf<Int, MutableStateFlow<Long?>>()
    private val loggedOut = mutableListOf<Int>()
    private val deleted = mutableListOf<Int>()

    private fun TestScope.accounts(start: Boolean = true): Accounts {
        val dispatcher = StandardTestDispatcher(testScheduler)
        clients[0] = FakeTdlibClient()
        return Accounts(
            firstClient = clients.getValue(0),
            factory =
                object : AccountFactory {
                    override fun client(
                        slot: AccountSlot,
                        kept: () -> Boolean,
                    ) = FakeTdlibClient().also { clients[slot.index] = it }

                    override fun component(
                        slot: AccountSlot,
                        client: TdlibClient,
                        scope: CoroutineScope,
                    ) = component(slot.index, client, scope)

                    override fun deleteStorage(slot: AccountSlot) {
                        deleted += slot.index
                    }
                },
            registryFile = registryFile,
            appScope = CoroutineScope(backgroundScope.coroutineContext + dispatcher),
            io = dispatcher,
        ).also {
            if (start) it.start()
            runCurrent()
        }
    }

    /** A graph that answers only what [Accounts] asks of it. */
    private fun component(
        slot: Int,
        client: TdlibClient,
        scope: CoroutineScope,
    ): AccountComponent {
        val reporter = TdlibNetworkTypeReporter(client, emptyFlow(), flowOf(false), scope)
        val step = MutableStateFlow<AuthStep>(AuthStep.WaitPhoneNumber).also { steps[slot] = it }
        val user = MutableStateFlow<Long?>(null).also { users[slot] = it }
        val auth =
            proxy<AuthRepository> { name ->
                when (name) {
                    "getAuthStep" -> step
                    "logOut" -> Outcome.Success(Unit).also { loggedOut += slot }
                    else -> null
                }
            }
        val userRepository = proxy<UserRepository> { name -> if (name == "getMyUserId") user else null }
        return proxy { name ->
            when (name) {
                "authRepository" -> auth
                "userRepository" -> userRepository
                "updateCaches" -> emptySet<Any>()
                "networkTypeReporter" -> reporter
                else -> null
            }
        }
    }

    private inline fun <reified T> proxy(crossinline answer: (String) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { self, method, args ->
            when (method.name) {
                "equals" -> self === args?.firstOrNull()
                "hashCode" -> System.identityHashCode(self)
                "toString" -> T::class.java.simpleName
                else -> answer(method.name)
            }
        } as T

    private fun TestScope.signIn(
        slot: Int,
        userId: Long,
    ) {
        users.getValue(slot).value = userId
        steps.getValue(slot).value = AuthStep.Ready
        runCurrent()
    }

    @Test
    fun `without a registry the first account is the only one, and shown`() =
        runTest {
            val accounts = accounts()

            assertEquals(AccountRegistry.First, accounts.registry.value)
            assertEquals(listOf(0), accounts.all().map { it.slot.index })
        }

    @Test
    fun `the registry read at start starts its accounts and shows the one it names`() =
        runTest {
            registryFile.write(AccountRegistry(slots = listOf(0, 2), active = 2))

            val accounts = accounts()

            assertEquals(2, accounts.registry.value.active)
            assertTrue(clients.getValue(2).isStarted)
        }

    @Test
    fun `waiting for the registry reads it when nothing has started yet`() =
        runTest {
            registryFile.write(AccountRegistry(slots = listOf(0, 1), active = 1))
            val accounts = accounts(start = false)

            accounts.awaitLoaded()

            assertEquals(AccountRegistry(listOf(0, 1), active = 1), accounts.registry.value)
        }

    @Test
    fun `the account graphs come once the registry is read, then with every account added`() =
        runTest {
            registryFile.write(AccountRegistry(slots = listOf(0, 2), active = 2))
            val accounts = accounts()
            val graphs = DefaultAccountGraphs(accounts, backgroundScope)
            val seen = mutableListOf<List<Int>>()
            backgroundScope.launch(StandardTestDispatcher(testScheduler)) {
                graphs.accounts.collect { list -> seen += list.map { it.slot } }
            }
            runCurrent()

            accounts.add()
            runCurrent()

            assertEquals(listOf(listOf(0, 2), listOf(0, 2, 1)), seen)
            assertEquals(1, graphs.activeSlot.value)
        }

    @Test
    fun `adding takes the first free slot, starts its client, shows it and saves it`() =
        runTest {
            val accounts = accounts()
            signIn(0, userId = 1)

            val slot = accounts.add()
            runCurrent()

            assertEquals(1, slot)
            assertTrue(clients.getValue(1).isStarted)
            assertEquals(AccountRegistry(listOf(0, 1), active = 1), registryFile.read())
            assertEquals(Accounts.Adding(slot = 1, previous = 0), accounts.adding.value)
        }

    @Test
    fun `an added account that signs in is no longer being added`() =
        runTest {
            val accounts = accounts()
            signIn(0, userId = 1)
            accounts.add()
            runCurrent()

            signIn(1, userId = 2)

            assertNull(accounts.adding.value)
            assertEquals(1, accounts.registry.value.active)
        }

    @Test
    fun `leaving an added account's sign-in closes it, deletes it and shows the one before`() =
        runTest {
            val accounts = accounts()
            signIn(0, userId = 1)
            accounts.add()
            runCurrent()

            accounts.cancelAdding()
            runCurrent()
            clients.getValue(1).authState.value = AuthState.Closed
            runCurrent()

            assertEquals(AccountRegistry.First, accounts.registry.value)
            assertTrue(clients.getValue(1).sent.any { it is TdApi.Close })
            assertEquals(listOf(1), deleted)
        }

    @Test
    fun `an account that logs out leaves the device, and another shows`() =
        runTest {
            val accounts = accounts()
            signIn(0, userId = 1)
            accounts.add()
            runCurrent()
            signIn(1, userId = 2)

            steps.getValue(1).value = AuthStep.LoggingOut
            runCurrent()
            clients.getValue(1).authState.value = AuthState.Closed
            runCurrent()

            assertEquals(AccountRegistry.First, accounts.registry.value)
            assertEquals(listOf(1), deleted)
        }

    @Test
    fun `the first slot logging out leaves its storage to the next account added`() =
        runTest {
            val accounts = accounts()
            signIn(0, userId = 1)
            accounts.add()
            runCurrent()
            signIn(1, userId = 2)
            accounts.switchTo(0)

            steps.getValue(0).value = AuthStep.LoggingOut
            runCurrent()

            assertEquals(AccountRegistry(listOf(1), active = 1), accounts.registry.value)
            assertTrue(deleted.isEmpty())
            assertEquals(0, accounts.add())
        }

    @Test
    fun `the last account logging out stays on the device`() =
        runTest {
            val accounts = accounts()
            signIn(0, userId = 1)

            steps.getValue(0).value = AuthStep.LoggingOut
            runCurrent()

            assertEquals(AccountRegistry.First, accounts.registry.value)
        }

    @Test
    fun `an added account of a user already on the device is logged out and removed`() =
        runTest {
            val accounts = accounts()
            signIn(0, userId = 1)
            accounts.add()
            runCurrent()

            signIn(1, userId = 1)

            assertEquals(listOf(1), loggedOut)
            assertEquals(AccountRegistry.First, accounts.registry.value)
        }

    @Test
    fun `no account is added beyond the slots`() =
        runTest {
            val accounts = accounts()
            repeat(AccountSlot.MAX_ACCOUNTS - 1) { accounts.add() }

            assertNull(accounts.add())
        }

    @Test
    fun `switching shows another account on the device and saves it, but not one that is not`() =
        runTest {
            registryFile.write(AccountRegistry(slots = listOf(0, 1), active = 0))
            val accounts = accounts()

            accounts.switchTo(1)
            accounts.switchTo(3)
            runCurrent()

            assertEquals(1, registryFile.read().active)
            assertFalse(3 in accounts.registry.value.slots)
        }

    @Test
    fun `the accounts together are signed in while one is`() {
        val signedOut = AuthStep.WaitPhoneNumber
        assertEquals(AuthStep.Ready, combinedAuthStep(listOf(signedOut, AuthStep.Ready)))
        assertEquals(AuthStep.Initializing, combinedAuthStep(listOf(AuthStep.Initializing)))
        assertEquals(signedOut, combinedAuthStep(listOf(AuthStep.Initializing, signedOut)))
    }
}
