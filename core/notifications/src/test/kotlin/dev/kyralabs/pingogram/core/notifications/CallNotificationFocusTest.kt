package dev.kyralabs.pingogram.core.notifications

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import dev.kyralabs.pingogram.core.domain.model.AppearanceSettings
import dev.kyralabs.pingogram.core.tdlib.AuthState
import dev.kyralabs.pingogram.core.testing.FakeAccountGraphs
import dev.kyralabs.pingogram.core.testing.FakeAppearanceRepository
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateUser
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.user
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * An incoming call while a Focus profile is on (board 31): a caller the profile lets ring rings as ever; any other
 * shows silently, without the ringtone's insistence or the full-screen call screen, and says Focus is on.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class CallNotificationFocusTest {
    private fun TestScope.builder(harness: NotificationsHarness): CallNotificationBuilder {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val appearance = FakeAppearanceRepository(AppearanceSettings(dynamicColor = false))
        return CallNotificationBuilder(
            context = harness.context,
            userRepository = harness.users,
            chatRepository = harness.chats,
            avatars = AvatarIcons(harness.context, harness.files, appearance, dispatcher),
            graphs = FakeAccountGraphs(),
            appearance = appearance,
            ioDispatcher = dispatcher,
            focus = harness.focus,
        )
    }

    private suspend fun TestScope.incoming(
        isContact: Boolean,
        configure: NotificationsHarness.() -> Unit = {},
    ): Notification {
        val harness = NotificationsHarness(this)
        harness.client.authState.value = AuthState.Ready
        harness.client.emit(updateUser(user(id = 42, isContact = isContact)))
        runCurrent()
        harness.configure()
        val intent =
            PendingIntent.getActivity(harness.context, 0, Intent(Intent.ACTION_VIEW), PendingIntent.FLAG_IMMUTABLE)
        return builder(harness).incoming(42, intent, intent, intent, fullScreen = true)
    }

    @Test
    fun `without Focus a call rings, insistent and full screen`() =
        runTest {
            val notification = incoming(isContact = false)

            assertTrue(notification.flags and Notification.FLAG_INSISTENT != 0)
            assertNotNull(notification.fullScreenIntent)
            assertEquals("Incoming voice call", notification.extras.getCharSequence(Notification.EXTRA_TEXT))
        }

    @Test
    fun `a caller Focus holds back shows silently and says why`() =
        runTest {
            val notification = incoming(isContact = false) { focus.callsRing = { it } }

            assertEquals(0, notification.flags and Notification.FLAG_INSISTENT)
            assertNull(notification.fullScreenIntent)
            assertEquals(
                "Incoming call · Focus is on",
                notification.extras.getCharSequence(Notification.EXTRA_TEXT),
            )
        }

    @Test
    fun `a contact rings where the profile allows calls from contacts`() =
        runTest {
            val notification = incoming(isContact = true) { focus.callsRing = { it } }

            assertTrue(notification.flags and Notification.FLAG_INSISTENT != 0)
            assertNotNull(notification.fullScreenIntent)
        }
}
