package dev.kyralabs.pingogram.widget

import android.app.Application
import dev.kyralabs.pingogram.core.data.repository.OpenChats
import dev.kyralabs.pingogram.core.data.repository.TdlibAuthRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibChatRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibFileRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibTransferRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibUserRepository
import dev.kyralabs.pingogram.core.domain.model.AppearanceSettings
import dev.kyralabs.pingogram.core.notifications.AvatarIcons
import dev.kyralabs.pingogram.core.tdlib.AuthState
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeAppearanceRepository
import dev.kyralabs.pingogram.core.testing.FakePasscodeRepository
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.chat
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.chatPosition
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.message
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.messageText
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateNewChat
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateUser
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.user
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.seconds

/**
 * The home screen widget's content ([RecentChatsWidgetUpdater]): the real repositories on a [FakeTdlibClient], the
 * placed widgets faked. It follows the chats only while a widget is placed, and keeps them off the home screen when
 * signed out or locked.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RecentChatsWidgetUpdaterTest {
    private class FakeWidgets(
        var present: Boolean,
    ) : RecentChatsWidgets {
        var updates = 0

        override suspend fun hasWidgets() = present

        override suspend fun updateAll() {
            updates++
        }
    }

    private val client = FakeTdlibClient()

    private fun TestScope.updater(
        widgets: FakeWidgets,
        authState: AuthState = AuthState.Ready,
        passcode: String? = null,
    ): RecentChatsWidgetUpdater {
        val context = RuntimeEnvironment.getApplication()
        val dispatcher = StandardTestDispatcher(testScheduler)
        client.authState.value = authState
        client.on(TdApi.LoadChats::class, TdResult.Success(TdApi.Ok()))
        val files = TdlibFileRepository(client, TdlibTransferRepository(client, backgroundScope), backgroundScope)
        return RecentChatsWidgetUpdater(
            context = context,
            authRepository = TdlibAuthRepository(client, dispatcher),
            passcodeRepository = FakePasscodeRepository(passcode = passcode),
            chatRepository = TdlibChatRepository(client, backgroundScope, dispatcher, OpenChats()),
            userRepository = TdlibUserRepository(client, backgroundScope),
            avatars =
                AvatarIcons(
                    context,
                    files,
                    FakeAppearanceRepository(AppearanceSettings(dynamicColor = false)),
                    dispatcher,
                ),
            widgets = widgets,
            scope = backgroundScope,
        ).also {
            it.start()
            runCurrent()
        }
    }

    private suspend fun chats() {
        client.emit(TdApi.UpdateOption("my_id", TdApi.OptionValueInteger(ME)))
        client.emit(updateUser(user(id = ME, firstName = "Me")))
        client.emit(updateUser(user(id = ALICE, firstName = "Alice")))
        client.emit(
            updateNewChat(
                chat(
                    id = ALICE,
                    type = TdApi.ChatTypePrivate(ALICE),
                    title = "Alice",
                    positions = arrayOf(chatPosition(order = 3_000)),
                    lastMessage = message(chatId = ALICE, sender = TdApi.MessageSenderUser(ALICE), content = hi),
                ).apply { unreadCount = 3 },
            ),
        )
        client.emit(
            updateNewChat(
                chat(
                    id = GROUP,
                    type = TdApi.ChatTypeBasicGroup(1),
                    title = "Climbing",
                    positions = arrayOf(chatPosition(order = 2_000)),
                    lastMessage = message(chatId = GROUP, sender = TdApi.MessageSenderUser(ALICE), content = hi),
                ),
            ),
        )
        client.emit(
            updateNewChat(
                chat(
                    id = SAVED,
                    type = TdApi.ChatTypePrivate(ME),
                    title = "Me",
                    positions = arrayOf(chatPosition(order = 1_000)),
                ),
            ),
        )
        client.emit(
            updateNewChat(
                chat(
                    id = SECRET,
                    type = TdApi.ChatTypeSecret(1, ALICE),
                    title = "Alice",
                    positions = arrayOf(chatPosition(order = 500)),
                    lastMessage = message(chatId = SECRET, sender = TdApi.MessageSenderUser(ALICE), content = hi),
                ),
            ),
        )
    }

    private fun TestScope.settle() {
        advanceTimeBy(RecentChatsWidgetUpdater.UPDATE_DELAY + 1.seconds)
        runCurrent()
    }

    @Test
    fun `shows the most recent chats with their avatars once a widget is placed`() =
        runTest {
            val widgets = FakeWidgets(present = true)
            val updater = updater(widgets)
            chats()
            runCurrent()
            settle()

            val content = updater.content.value as RecentChatsContent.Chats
            assertEquals(listOf("Alice", "Climbing", "Saved Messages", "Alice"), content.chats.map { it.title })
            // The sender's name is isolated: its direction never spills into the message (docs/DESIGN_SYSTEM.md §9).
            assertEquals(listOf("Hi", "\u2068Alice\u2069: Hi", "", "Secret chat"), content.chats.map { it.preview })
            assertEquals(3, content.chats.first().unreadCount)
            content.chats.forEach { assertNotNull(it.avatar) }
            assertTrue(widgets.updates > 0)
        }

    @Test
    fun `loads the first chats itself when TDLib has fewer`() =
        runTest {
            updater(FakeWidgets(present = true))
            settle()

            val load = client.sentOf<TdApi.LoadChats>().single()
            assertEquals(RecentChatsWidgetUpdater.MAX_CHATS, load.limit)
        }

    @Test
    fun `without a widget it follows nothing and draws nothing`() =
        runTest {
            val widgets = FakeWidgets(present = false)
            val updater = updater(widgets)
            chats()
            runCurrent()
            settle()

            assertEquals(RecentChatsContent.Loading, updater.content.value)
            assertEquals(0, widgets.updates)
            assertTrue(client.sentOf<TdApi.LoadChats>().isEmpty())
        }

    @Test
    fun `a widget placed later gets the chats, and goes back to nothing when the last one is removed`() =
        runTest {
            val widgets = FakeWidgets(present = false)
            val updater = updater(widgets)
            chats()
            runCurrent()

            updater.onWidgetsChanged(present = true)
            runCurrent()
            settle()
            assertTrue(updater.content.value is RecentChatsContent.Chats)

            updater.onWidgetsChanged(present = false)
            runCurrent()
            assertEquals(RecentChatsContent.Loading, updater.content.value)
        }

    @Test
    fun `signed out, it invites to sign in`() =
        runTest {
            val updater = updater(FakeWidgets(present = true), authState = AuthState.WaitPhoneNumber)
            settle()

            assertEquals(RecentChatsContent.SignedOut, updater.content.value)
        }

    @Test
    fun `with a passcode, no chat reaches the home screen`() =
        runTest {
            val updater = updater(FakeWidgets(present = true), passcode = "1234")
            chats()
            runCurrent()
            settle()

            assertEquals(RecentChatsContent.Locked, updater.content.value)
            assertTrue(client.sentOf<TdApi.LoadChats>().isEmpty())
        }

    private companion object {
        const val ME = 1L
        const val ALICE = 2L
        const val GROUP = -10L
        const val SAVED = ME
        const val SECRET = -20L
        val hi = messageText("Hi")
    }
}
