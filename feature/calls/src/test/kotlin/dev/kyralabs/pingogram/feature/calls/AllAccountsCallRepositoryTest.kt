package dev.kyralabs.pingogram.feature.calls

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Call
import dev.kyralabs.pingogram.core.domain.model.CallSignalingData
import dev.kyralabs.pingogram.core.domain.model.CallState
import dev.kyralabs.pingogram.core.domain.repository.AccountGraph
import dev.kyralabs.pingogram.core.domain.repository.AccountRepositories
import dev.kyralabs.pingogram.core.testing.FakeAccountGraphs
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import kotlin.time.Duration

/** Every account's calls as one repository (`docs/adr/0031-multiple-accounts.md`), each id carrying its account. */
class AllAccountsCallRepositoryTest {
    private val first = FakeCallRepository()
    private val second = FakeCallRepository()
    private val graphs =
        FakeAccountGraphs(
            accounts = persistentListOf(AccountGraph(0, repositories(first)), AccountGraph(1, repositories(second))),
            activeSlot = 1,
        )
    private val calls = AllAccountsCallRepository(graphs)

    /** An account's repositories that answer only its calls. */
    private fun repositories(calls: FakeCallRepository): AccountRepositories =
        Proxy.newProxyInstance(
            AccountRepositories::class.java.classLoader,
            arrayOf(AccountRepositories::class.java),
        ) { self, method, args ->
            when (method.name) {
                "callRepository" -> calls
                "equals" -> self === args?.firstOrNull()
                "hashCode" -> System.identityHashCode(self)
                "toString" -> "AccountRepositories"
                else -> error("Not used: ${method.name}")
            }
        } as AccountRepositories

    private fun ringing(id: Int) =
        Call(id, userId = 42, isOutgoing = false, isVideo = false, state = CallState.Pending(true, true))

    @Test
    fun `every account's calls come together, another account's ids carrying its slot`() =
        runTest {
            first.update(ringing(1))
            second.update(ringing(1))

            val all = calls.calls.first()

            assertEquals(setOf(1, CallIds.of(1, 1)), all.keys)
            assertEquals(1, CallIds.slotOf(all.keys.max()))
            assertEquals(CallIds.of(1, 1), all.getValue(CallIds.of(1, 1)).id)
        }

    @Test
    fun `actions go to the call's own account, with TDLib's id`() =
        runTest {
            calls.acceptCall(CallIds.of(1, 5), ENGINE_PROTOCOL)
            calls.discardCall(3, isDisconnected = false, Duration.ZERO, isVideo = false, connectionId = 0)
            calls.sendSignalingData(CallIds.of(1, 5), byteArrayOf(1))

            assertEquals(listOf(5 to ENGINE_PROTOCOL), second.accepted)
            assertEquals(listOf(3), first.discarded.map { it.callId })
            assertEquals(listOf(5), second.sent.map { it.first })
            assertTrue(first.accepted.isEmpty())
        }

    @Test
    fun `a new call goes out from the shown account`() =
        runTest {
            val started = calls.startCall(42, isVideo = false, protocol = ENGINE_PROTOCOL)

            assertEquals(Outcome.Success(CallIds.of(1, FakeCallRepository.STARTED_CALL_ID)), started)
            assertEquals(listOf(42L to ENGINE_PROTOCOL), second.started)
            assertTrue(first.started.isEmpty())
        }

    @Test
    fun `the other side's data names the call with its account`() =
        runTest {
            val received = mutableListOf<Int>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                calls.signalingData.collect { received += it.callId }
            }
            runCurrent()

            second.signaling.emit(CallSignalingData(5, byteArrayOf(1)))
            first.signaling.emit(CallSignalingData(5, byteArrayOf(1)))
            runCurrent()

            assertEquals(listOf(CallIds.of(1, 5), 5), received)
        }

    @Test
    fun `a call of an account that left the device fails`() =
        runTest {
            graphs.accountList.value = persistentListOf(AccountGraph(0, repositories(first)))

            assertTrue(calls.acceptCall(CallIds.of(1, 5), ENGINE_PROTOCOL) is Outcome.Failure)
        }

    @Test
    fun `the first account's ids are TDLib's`() {
        assertEquals(7, CallIds.of(0, 7))
        assertEquals(0, CallIds.slotOf(7))
        assertEquals(7, CallIds.tdlibId(CallIds.of(3, 7)))
    }
}
