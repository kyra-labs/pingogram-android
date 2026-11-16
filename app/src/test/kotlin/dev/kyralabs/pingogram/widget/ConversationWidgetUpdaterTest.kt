package dev.kyralabs.pingogram.widget

import android.app.Application
import dev.kyralabs.pingogram.core.data.repository.OpenChats
import dev.kyralabs.pingogram.core.data.repository.SecretChatLinkPreviews
import dev.kyralabs.pingogram.core.data.repository.TdlibAuthRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibChatRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibFileRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibGroupRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibMessageRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibTransferRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibUserRepository
import dev.kyralabs.pingogram.core.domain.model.AppearanceSettings
import dev.kyralabs.pingogram.core.notifications.AvatarIcons
import dev.kyralabs.pingogram.core.tdlib.AuthState
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeAppearanceRepository
import dev.kyralabs.pingogram.core.testing.FakeChatLockRepository
import dev.kyralabs.pingogram.core.testing.FakeLinkPreviewSettingsRepository
import dev.kyralabs.pingogram.core.testing.FakePasscodeRepository
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.chat
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.message
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.messageText
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.messages
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateNewChat
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateUser
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.user
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
 * The Conversation widget's content ([ConversationWidgetUpdater]): the real repositories on a [FakeTdlibClient], the
 * placed widgets faked. It follows a widget's chat only while the widget is placed, and keeps it off the home screen
 * when signed out, locked, or a locked chat.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ConversationWidgetUpdaterTest {
    /** The placed widgets, each the chat it keeps. */
    private class FakeWidgets(
        vararg keys: ConversationKey,
    ) : ConversationWidgets {
        val placed = keys.toMutableSet()
        var updates = 0
        var asked = 0

        override suspend fun keys() = placed.toSet().also { asked++ }

        override suspend fun updateAll() {
            updates++
        }
    }

    private val client = FakeTdlibClient()
    private val accounts = MutableStateFlow<Map<Int, WidgetAccount>>(emptyMap())

    private fun TestScope.account(lockedChats: Set<Long>): WidgetAccount {
        val context = RuntimeEnvironment.getApplication()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val files = TdlibFileRepository(client, TdlibTransferRepository(client, backgroundScope), backgroundScope)
        return WidgetAccount(
            auth = TdlibAuthRepository(client, dispatcher),
            chats = TdlibChatRepository(client, backgroundScope, dispatcher, OpenChats()),
            users = TdlibUserRepository(client, backgroundScope),
            groups = TdlibGroupRepository(client, backgroundScope),
            chatLock = FakeChatLockRepository(locked = lockedChats),
            avatars =
                AvatarIcons(
                    context,
                    files,
                    FakeAppearanceRepository(AppearanceSettings(dynamicColor = false)),
                    dispatcher,
                ),
            messages =
                TdlibMessageRepository(
                    client,
                    backgroundScope,
                    SecretChatLinkPreviews(client, FakeLinkPreviewSettingsRepository()),
                ),
        )
    }

    private fun TestScope.updater(
        widgets: FakeWidgets,
        authState: AuthState = AuthState.Ready,
        passcode: String? = null,
        lockedChats: Set<Long> = emptySet(),
    ): ConversationWidgetUpdater {
        client.authState.value = authState
        accounts.value = mapOf(0 to account(lockedChats))
        return ConversationWidgetUpdater(
            context = RuntimeEnvironment.getApplication(),
            passcodeRepository = FakePasscodeRepository(passcode = passcode),
            widgets = widgets,
            accounts = accounts,
            scope = backgroundScope,
        ).also {
            it.start()
            runCurrent()
        }
    }

    /** Alice's chat, as TDLib announces it; asked for before it is announced, TDLib answers with it too. */
    private suspend fun chat() {
        client.emit(TdApi.UpdateOption("my_id", TdApi.OptionValueInteger(ME)))
        client.emit(updateUser(user(id = ME, firstName = "Me")))
        client.emit(updateUser(user(id = ALICE, firstName = "Alice")))
        client.emit(updateNewChat(alice()))
    }

    private fun alice() =
        chat(id = ALICE, type = TdApi.ChatTypePrivate(ALICE), title = "Alice").apply { unreadCount = 2 }

    private fun history() {
        client.on(TdApi.GetChat::class, TdResult.Success(alice()))
        client.on(
            TdApi.GetChatHistory::class,
            TdResult.Success(
                messages(
                    message(id = 3, chatId = ALICE, sender = TdApi.MessageSenderUser(ALICE), content = text("Third")),
                    message(
                        id = 2,
                        chatId = ALICE,
                        sender = TdApi.MessageSenderUser(ME),
                        content = text("Second"),
                        isOutgoing = true,
                    ),
                    message(id = 1, chatId = ALICE, sender = TdApi.MessageSenderUser(ALICE), content = text("First")),
                ),
            ),
        )
    }

    private fun text(text: String) = messageText(text)

    private fun TestScope.settle() {
        advanceTimeBy(RecentChatsWidgetUpdater.START_UPDATE_DELAY + 1.seconds)
        runCurrent()
    }

    @Test
    fun `shows the chat's newest messages, oldest first, and redraws the widget`() =
        runTest {
            history()
            val widgets = FakeWidgets(KEY)
            val updater = updater(widgets)
            chat()
            runCurrent()
            settle()

            val content = updater.currentContent(KEY) as ConversationContent.Chat
            assertEquals("Alice", content.title)
            assertEquals(2, content.unreadCount)
            assertEquals(
                listOf("Alice" to "First", "You" to "Second", "Alice" to "Third"),
                content.messages.map { it.sender to it.text },
            )
            assertTrue(content.canReply)
            assertNotNull(content.avatar)
            assertTrue(widgets.updates > 0)
        }

    @Test
    fun `asks to sign in first while signed out`() =
        runTest {
            val updater = updater(FakeWidgets(KEY), authState = AuthState.WaitPhoneNumber)
            settle()

            assertEquals(ConversationContent.SignedOut, updater.currentContent(KEY))
        }

    @Test
    fun `a passcode or a locked chat keeps the chat off the home screen`() =
        runTest {
            history()
            val passcode = updater(FakeWidgets(KEY), passcode = "1234")
            chat()
            runCurrent()
            settle()

            assertEquals(ConversationContent.Locked, passcode.currentContent(KEY))
        }

    @Test
    fun `a locked chat is shown as locked`() =
        runTest {
            history()
            val updater = updater(FakeWidgets(KEY), lockedChats = setOf(ALICE))
            chat()
            runCurrent()
            settle()

            assertEquals(ConversationContent.Locked, updater.currentContent(KEY))
        }

    @Test
    fun `a failed load says so, and trying again shows the messages`() =
        runTest {
            client.on(TdApi.GetChat::class, TdResult.Success(alice()))
            client.on(TdApi.GetChatHistory::class, TdResult.Failure(TdError(500, "Internal")))
            val updater = updater(FakeWidgets(KEY))
            chat()
            runCurrent()
            settle()
            assertEquals(ConversationContent.Failed, updater.currentContent(KEY))

            history()
            updater.retry()
            runCurrent()
            settle()

            assertTrue(updater.currentContent(KEY) is ConversationContent.Chat)
        }

    @Test
    fun `without a widget it follows nothing and draws nothing`() =
        runTest {
            history()
            val widgets = FakeWidgets()
            val updater = updater(widgets)
            chat()
            runCurrent()
            settle()

            assertEquals(ConversationContent.Loading, updater.currentContent(KEY))
            assertEquals(0, widgets.updates)
            assertTrue(client.sentOf<TdApi.GetChatHistory>().isEmpty())
        }

    @Test
    fun `asks the launcher nothing and loads nothing while the app starts`() =
        runTest {
            history()
            val widgets = FakeWidgets(KEY)
            val updater = updater(widgets)
            chat()
            advanceTimeBy(RecentChatsWidgetUpdater.START_UPDATE_DELAY - 1.seconds)
            runCurrent()

            assertEquals(0, widgets.asked)
            assertTrue(client.sentOf<TdApi.GetChatHistory>().isEmpty())
            assertEquals(ConversationContent.Loading, updater.currentContent(KEY))

            settle()

            assertEquals(1, widgets.asked)
            assertTrue(updater.currentContent(KEY) is ConversationContent.Chat)
        }

    @Test
    fun `a widget that asks while the app starts gets its content at once`() =
        runTest {
            history()
            val widgets = FakeWidgets(KEY)
            val updater = updater(widgets)
            chat()
            updater.onWidgetShown(KEY)
            advanceTimeBy(2.seconds)
            runCurrent()

            assertEquals(0, widgets.asked)
            assertTrue(updater.currentContent(KEY) is ConversationContent.Chat)
            assertEquals(0, widgets.updates)
        }

    private companion object {
        const val ME = 1L
        const val ALICE = 2L
        val KEY = ConversationKey(slot = 0, chatId = ALICE)
    }
}
