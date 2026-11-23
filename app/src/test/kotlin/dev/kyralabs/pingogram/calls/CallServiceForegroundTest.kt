package dev.kyralabs.pingogram.calls

import android.content.Intent
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import dev.kyralabs.pingogram.core.tdlib.AuthState
import dev.kyralabs.pingogram.core.tdlib.ConnectionState
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import dev.kyralabs.pingogram.core.tdlib.di.TdlibModule
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.feature.calls.service.CallService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * [CallService] enters the foreground the moment it starts, before it looks the call up, and only then may stop:
 * Android 12+ ends an app whose service, started for the foreground, stops or waits first.
 */
@HiltAndroidTest
@UninstallModules(TdlibModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class CallServiceForegroundTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @BindValue
    @JvmField
    val tdlibClient: TdlibClient = FakeTdlibClient(authState = AuthState.Ready, connectionState = ConnectionState.Ready)

    private val context get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    private fun start() =
        Robolectric
            .buildService(CallService::class.java, Intent(context, CallService::class.java))
            .create()
            .startCommand(0, 1)
            .get()

    @Test
    fun `with the call already over, it enters the foreground before it stops`() {
        val shadow = shadowOf(start())

        assertEquals(CALL_NOTIFICATION_ID, shadow.lastForegroundNotificationId)
        assertTrue(shadow.isForegroundStopped)
        assertTrue(shadow.isStoppedBySelf)
    }

    private companion object {
        /** `CallNotifications.ID`, the call's notification and the service's. */
        const val CALL_NOTIFICATION_ID = 0x5ca11
    }
}
