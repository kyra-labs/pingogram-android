package dev.kyralabs.pingogram.core.data.updates

import android.content.Context
import org.robolectric.RuntimeEnvironment
import dev.kyralabs.pingogram.core.data.attachment.UploadFiles
import dev.kyralabs.pingogram.core.data.di.AccountDependencies
import dev.kyralabs.pingogram.core.data.di.DaggerAccountComponent
import dev.kyralabs.pingogram.core.data.repository.AndroidNetworkRepository
import dev.kyralabs.pingogram.core.domain.repository.AccountRepositories
import dev.kyralabs.pingogram.core.tdlib.AccountSlot
import dev.kyralabs.pingogram.core.testing.FakeAppLockRepository
import dev.kyralabs.pingogram.core.testing.FakeKeywordAlertsRepository
import dev.kyralabs.pingogram.core.testing.FakeLinkPreviewSettingsRepository
import dev.kyralabs.pingogram.core.testing.FakePasscodeRepository
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.lang.reflect.Proxy
import java.util.Optional

/**
 * The guard of `docs/PERFORMANCE.md` §3, "Updates fan-out": an account's whole graph, as the app makes it (every cache
 * before TDLib starts, then every repository), takes its client's updates through one subscriber, the client's
 * [TdlibUpdateRouter]. A repository that collects `TdlibClient.updates` itself adds one and fails this.
 */
@RunWith(RobolectricTestRunner::class)
class TdlibUpdatesSubscriptionTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    private val client = FakeTdlibClient()

    /** The account's scope; [Dispatchers.Unconfined] runs what its repositories start at once. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `an account's graph subscribes to its client's updates once, through the router`() {
        val component = DaggerAccountComponent.factory().create(AccountSlot.First, client, scope, dependencies())

        component.updateCaches()
        component.networkTypeReporter()
        val repositories = AccountRepositories::class.java.methods.filter { it.parameterCount == 0 }
        repositories.forEach { it.invoke(component) }

        assertEquals(1, client.updateSubscribers.value)
    }

    /** The app's parts an account's graph takes: real where Robolectric has them, fakes, or stubs never called. */
    private fun dependencies(): AccountDependencies =
        proxy(AccountDependencies::class.java) { type ->
            when (type) {
                Context::class.java -> context
                CoroutineDispatcher::class.java -> Dispatchers.Unconfined
                UploadFiles::class.java -> UploadFiles(context)
                AndroidNetworkRepository::class.java -> AndroidNetworkRepository(context)
                Optional::class.java -> Optional.empty<Any>()
                else -> FAKES.firstOrNull(type::isInstance) ?: stub(type)
            }
        } as AccountDependencies

    /** An instance of interface [type] that answers each call with [answer] of the call's return type. */
    private fun proxy(
        type: Class<*>,
        answer: (Class<*>) -> Any?,
    ): Any =
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { self, method, args ->
            when (method.name) {
                "equals" -> self === args?.firstOrNull()
                "hashCode" -> System.identityHashCode(self)
                "toString" -> type.simpleName
                else -> answer(method.returnType)
            }
        }

    /** A part no repository uses while it is made; a call to it fails the test. */
    private fun stub(type: Class<*>): Any = proxy(type) { error("${type.simpleName} called while the graph is made") }

    private companion object {
        val FAKES =
            listOf(
                FakeAppLockRepository(),
                FakeKeywordAlertsRepository(),
                FakeLinkPreviewSettingsRepository(),
                FakePasscodeRepository(),
            )
    }
}
