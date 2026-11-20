package dev.kyralabs.pingogram.core.data.updates

import android.content.Context
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
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
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

    /**
     * The account's scope; [Dispatchers.Unconfined] runs what its repositories start at once. Their failures on the
     * fake's empty answers do not matter here.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined + CoroutineExceptionHandler { _, _ -> })

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `an account's graph subscribes to its client's updates once, through the router`() {
        repositories()

        assertEquals(1, client.updateSubscribers.value)
    }

    @Test
    fun `collecting the repositories' flows adds no subscriber`() {
        val flows =
            repositories().flatMap { repository ->
                repository.javaClass.methods
                    .filter { it.parameterCount == 0 && Flow::class.java.isAssignableFrom(it.returnType) }
                    .map { it.invoke(repository) as Flow<*> }
            }

        flows.forEach { flow -> scope.launch { flow.collect {} } }

        assertTrue(flows.size > MIN_FLOWS)
        assertEquals(1, client.updateSubscribers.value)
    }

    /** Every cache, made as the app makes them before TDLib starts, then every repository of the account's graph. */
    private fun repositories(): List<Any> {
        val component = DaggerAccountComponent.factory().create(AccountSlot.First, client, scope, dependencies())
        val caches = component.updateCaches()
        component.networkTypeReporter()
        val repositories =
            AccountRepositories::class.java.methods
                .filter { it.parameterCount == 0 }
                .map { it.invoke(component) }
        return caches.toList() + repositories
    }

    /** The app's parts an account's graph takes: real where Robolectric has them, fakes, or empty stand-ins. */
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

    /**
     * A part of the app with nothing in it: a flow that never emits, false, zero, nothing. What its repository does with
     * it does not matter here, only how it subscribes to the updates.
     */
    private fun stub(type: Class<*>): Any = proxy(type, ::nothingOf)

    private fun nothingOf(type: Class<*>): Any? =
        when {
            Flow::class.java.isAssignableFrom(type) -> MutableSharedFlow<Any>()
            type == java.lang.Boolean.TYPE -> false
            type == Integer.TYPE -> 0
            type == java.lang.Long.TYPE -> 0L
            type == String::class.java -> ""
            type.isInterface -> stub(type)
            else -> null
        }

    private companion object {
        /** Fewer flows than this means the reflection above found the wrong methods. */
        const val MIN_FLOWS = 20

        val FAKES =
            listOf(
                FakeAppLockRepository(),
                FakeKeywordAlertsRepository(),
                FakeLinkPreviewSettingsRepository(),
                FakePasscodeRepository(),
            )
    }
}
