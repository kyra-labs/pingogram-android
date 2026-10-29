package dev.kyralabs.pingogram.background

import dev.kyralabs.pingogram.core.data.repository.TdlibAuthRepository
import dev.kyralabs.pingogram.core.testing.FakeKeepConnectedRepository
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.TimeSource

/** When "Keep connected" starts and stops its service, and when it may start again after a reboot. */
@OptIn(ExperimentalCoroutinesApi::class)
class KeepConnectedControllerTest {
    private val client = FakeTdlibClient()
    private val setting = FakeKeepConnectedRepository()
    private val foreground = MutableStateFlow(false)
    private val service = RecordingServiceControl()

    // Through the real TDLib repository on FakeTdlibClient, as the app wires it.
    private fun TestScope.started() {
        KeepConnectedController(
            setting,
            TdlibAuthRepository(client, StandardTestDispatcher(testScheduler), TimeSource.Monotonic).authStep,
            foreground,
            service,
            backgroundScope,
        ).start()
        runCurrent()
    }

    private suspend fun TestScope.loggedIn() {
        client.emitAuthorizationState(TdApi.AuthorizationStateReady())
        runCurrent()
    }

    private fun TestScope.set(
        enabled: Boolean? = null,
        inForeground: Boolean? = null,
    ) {
        enabled?.let { setting.keepConnected.value = it }
        inForeground?.let { foreground.value = it }
        runCurrent()
    }

    @Test
    fun `off by default, the service never starts and the boot receiver stays disabled`() =
        runTest {
            started()
            loggedIn()
            set(inForeground = true)

            assertEquals(0, service.starts)
            assertEquals(listOf(false), service.startsAfterBoot.distinct())
        }

    @Test
    fun `turned on in the foreground while logged in, it starts and enables the boot receiver`() =
        runTest {
            started()
            loggedIn()
            set(inForeground = true)

            set(enabled = true)

            assertEquals(1, service.starts)
            assertEquals(true, service.startsAfterBoot.last())
        }

    @Test
    fun `it waits for TDLib to report the login before starting`() =
        runTest {
            setting.keepConnected.value = true
            foreground.value = true
            started()

            assertEquals(0 to 0, service.starts to service.stops)

            loggedIn()

            assertEquals(1, service.starts)
        }

    @Test
    fun `it starts only from the foreground, and keeps running in the background`() =
        runTest {
            setting.keepConnected.value = true
            started()
            loggedIn()

            assertEquals(0, service.starts)

            set(inForeground = true)
            set(inForeground = false)

            assertEquals(1 to 0, service.starts to service.stops)
        }

    @Test
    fun `turning it off stops it and disables the boot receiver`() =
        runTest {
            setting.keepConnected.value = true
            foreground.value = true
            started()
            loggedIn()

            set(enabled = false)

            assertEquals(1, service.stops)
            assertEquals(false, service.startsAfterBoot.last())
        }

    @Test
    fun `logging out stops it, in the background too`() =
        runTest {
            setting.keepConnected.value = true
            foreground.value = true
            started()
            loggedIn()
            set(inForeground = false)

            client.emitAuthorizationState(TdApi.AuthorizationStateLoggingOut())
            runCurrent()

            assertEquals(1, service.stops)
        }

    @Test
    fun `a process started without a logged-in account stops a service the boot receiver started`() =
        runTest {
            setting.keepConnected.value = true
            started()

            client.emitAuthorizationState(TdApi.AuthorizationStateWaitPhoneNumber())
            runCurrent()

            assertEquals(0 to 1, service.starts to service.stops)
        }

    private class RecordingServiceControl : KeepConnectedServiceControl {
        var starts = 0
        var stops = 0
        val startsAfterBoot = mutableListOf<Boolean>()

        override fun start() {
            starts++
        }

        override fun stop() {
            stops++
        }

        override fun setStartsAfterBoot(enabled: Boolean) {
            startsAfterBoot += enabled
        }
    }
}
