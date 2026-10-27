package dev.kyralabs.pingogram.account

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.ShortcutManagerCompat
import dev.kyralabs.pingogram.core.data.account.AccountRegistry
import dev.kyralabs.pingogram.core.notifications.NotificationIntents
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Which account an intent opens in (`docs/adr/0031-multiple-accounts.md`): the shown one's intents are handed on,
 * another account's switch to it first and open the app again with the intent.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AccountRestartTest {
    private val context = RuntimeEnvironment.getApplication()

    private class FakeSwitching(
        slots: List<Int>,
        active: Int,
    ) : AccountSwitching {
        override val registry = MutableStateFlow(AccountRegistry(slots, active))

        override suspend fun awaitLoaded() = Unit

        override fun switchTo(slot: Int) {
            registry.value = registry.value.copy(active = slot)
        }
    }

    private class Recorded {
        val handled = mutableListOf<Intent?>()
        val restarts = mutableListOf<Intent?>()
    }

    private fun TestScope.restart(
        accounts: FakeSwitching,
        recorded: Recorded,
    ) = AccountRestart(backgroundScope, accounts, recorded.handled::add, recorded.restarts::add)

    private fun chatIntent(slot: Int) = checkNotNull(NotificationIntents.openChat(context, chatId = 100, slot = slot))

    @Test
    fun `an intent of no account in particular is handed on at once`() =
        runTest {
            val recorded = Recorded()
            val link = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/durov"))

            restart(FakeSwitching(listOf(0, 1), active = 1), recorded).start(link)

            assertEquals(listOf(link), recorded.handled)
        }

    @Test
    fun `a notification of the shown account opens its chat`() =
        runTest {
            val recorded = Recorded()
            val intent = chatIntent(slot = 1)

            restart(FakeSwitching(listOf(0, 1), active = 1), recorded).start(intent)
            runCurrent()

            assertEquals(listOf(intent), recorded.handled)
            assertTrue(recorded.restarts.isEmpty())
        }

    @Test
    fun `a notification of another account switches to it and opens the app again with it`() =
        runTest {
            val recorded = Recorded()
            val accounts = FakeSwitching(listOf(0, 1), active = 0)

            restart(accounts, recorded).start(chatIntent(slot = 1))
            runCurrent()

            assertEquals(1, accounts.registry.value.active)
            assertTrue(recorded.handled.isEmpty())
            val next = checkNotNull(recorded.restarts.single())
            assertEquals(100L, NotificationIntents.chatIdOf(next))
            assertEquals(1, NotificationIntents.accountOf(next))
            assertEquals(AccountRestart.RESTART_FLAGS, next.flags and AccountRestart.RESTART_FLAGS)
        }

    @Test
    fun `a notification that reaches the running app switches too`() =
        runTest {
            val recorded = Recorded()
            val accounts = FakeSwitching(listOf(0, 1), active = 1)
            val restart = restart(accounts, recorded).apply { start(null) }
            runCurrent()

            restart.onNewIntent(chatIntent(slot = 0))
            runCurrent()

            assertEquals(0, accounts.registry.value.active)
            assertEquals(0, NotificationIntents.accountOf(recorded.restarts.single()))
        }

    @Test
    fun `an intent of an account that left the device opens nothing`() =
        runTest {
            val recorded = Recorded()
            val accounts = FakeSwitching(listOf(0), active = 0)

            restart(accounts, recorded).start(chatIntent(slot = 2))
            runCurrent()

            assertEquals(0, accounts.registry.value.active)
            assertTrue(recorded.handled.isEmpty())
            assertTrue(recorded.restarts.isEmpty())
        }

    @Test
    fun `switching from the account switcher opens the app again at its start`() =
        runTest {
            val recorded = Recorded()
            val accounts = FakeSwitching(listOf(0, 1), active = 0)
            restart(accounts, recorded).start(null)
            runCurrent()

            accounts.switchTo(1)
            runCurrent()

            assertNull(recorded.restarts.single())
        }

    @Test
    fun `a share to another account's Direct Share target belongs to that account, with its read grant`() =
        runTest {
            val share =
                Intent(Intent.ACTION_SEND)
                    .putExtra(Intent.EXTRA_TEXT, "Hello")
                    .putExtra(ShortcutManagerCompat.EXTRA_SHORTCUT_ID, NotificationIntents.shortcutId(100, slot = 1))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val recorded = Recorded()

            assertEquals(1, accountOf(share))
            restart(FakeSwitching(listOf(0, 1), active = 0), recorded).start(share)
            runCurrent()

            val next = checkNotNull(recorded.restarts.single())
            assertTrue(next.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertEquals("Hello", next.getStringExtra(Intent.EXTRA_TEXT))
        }

    @Test
    fun `a share from the share sheet belongs to no account in particular`() {
        assertNull(accountOf(Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, "Hello")))
    }
}
