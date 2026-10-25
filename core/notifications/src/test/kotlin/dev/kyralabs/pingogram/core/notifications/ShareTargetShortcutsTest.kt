package dev.kyralabs.pingogram.core.notifications

import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import dev.kyralabs.pingogram.core.domain.model.AppearanceSettings
import dev.kyralabs.pingogram.core.tdlib.AuthState
import dev.kyralabs.pingogram.core.testing.FakeAppearanceRepository
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.chat
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.chatPosition
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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.time.Duration.Companion.seconds

/**
 * The Direct Share targets ([ShareTargetShortcuts], `REQUIREMENTS.md` FR-N2): the most recent chats the user can write
 * in, as conversation shortcuts in the share target's category, in the chat list's order, once it settled.
 */
@RunWith(RobolectricTestRunner::class)
class ShareTargetShortcutsTest {
    private fun TestScope.started(authState: AuthState = AuthState.Ready): NotificationsHarness {
        val harness = NotificationsHarness(this)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val avatars =
            AvatarIcons(
                harness.context,
                harness.files,
                FakeAppearanceRepository(AppearanceSettings(dynamicColor = false)),
                dispatcher,
            )
        harness.client.authState.value = authState
        ShareTargetShortcuts(
            authRepository = harness.auth,
            chatRepository = harness.chats,
            userRepository = harness.users,
            groupRepository = harness.groups,
            avatars = avatars,
            shortcuts = ConversationShortcuts(harness.context, dispatcher),
            scope = backgroundScope,
        ).start()
        runCurrent()
        return harness
    }

    /** Private chats 1…[count] with users of the same ids, the first the most recent; [readOnly] ones are read only. */
    private suspend fun NotificationsHarness.chats(
        count: Int,
        readOnly: Set<Long> = emptySet(),
    ) {
        for (id in 1L..count) {
            client.emit(updateUser(user(id = id, firstName = "User $id")))
            val permissions = TdApi.ChatPermissions().apply { canSendBasicMessages = id !in readOnly }
            client.emit(
                updateNewChat(
                    chat(
                        id = id,
                        type = TdApi.ChatTypePrivate(id),
                        title = "User $id",
                        positions = arrayOf(chatPosition(order = 1_000 - id)),
                        permissions = permissions,
                    ),
                ),
            )
        }
    }

    private fun TestScope.settle() {
        advanceTimeBy(ShareTargetShortcuts.PUBLISH_DELAY + 1.seconds)
        runCurrent()
    }

    @Test
    fun `the most recent chats the user can write in become share targets, in their order`() =
        runTest {
            val harness = started()
            harness.chats(count = 10, readOnly = setOf(3))
            runCurrent()
            settle()

            val shortcuts = ShortcutManagerCompat.getDynamicShortcuts(harness.context).sortedBy { it.rank }
            assertEquals(
                listOf(1L, 2L, 4L, 5L, 6L, 7L, 8L, 9L).map(NotificationIntents::shortcutId),
                shortcuts.map { it.id },
            )
            assertTrue(shortcuts.all { NotificationIntents.SHARE_TARGET_CATEGORY in it.categories.orEmpty() })
            assertEquals("User 1", shortcuts.first().shortLabel)
        }

    @Test
    fun `a chat that falls out of the most recent stops being a share target`() =
        runTest {
            val harness = started()
            harness.chats(count = 9)
            runCurrent()
            settle()
            val before = ShortcutManagerCompat.getDynamicShortcuts(harness.context).map { it.id }
            assertTrue(NotificationIntents.shortcutId(8) in before)

            // Chat 10 becomes the most recent: chat 8, now ninth, drops out.
            harness.client.emit(updateUser(user(id = 10, firstName = "User 10")))
            val newest = arrayOf(chatPosition(order = 2_000))
            harness.client.emit(
                updateNewChat(chat(id = 10, type = TdApi.ChatTypePrivate(10), title = "User 10", positions = newest)),
            )
            runCurrent()
            settle()

            val ids = ShortcutManagerCompat.getDynamicShortcuts(harness.context).map { it.id }
            assertTrue(NotificationIntents.shortcutId(10) in ids)
            assertTrue(NotificationIntents.shortcutId(8) !in ids)
        }

    @Test
    fun `the next process leaves the targets Android has as they are, and pushes those that changed`() =
        runTest {
            started().run {
                chats(count = 2)
                runCurrent()
            }
            settle()
            val context = RuntimeEnvironment.getApplication()
            // Marks what the earlier process pushed: a target pushed again loses the mark.
            ShortcutManagerCompat.getDynamicShortcuts(context).forEach { shortcut ->
                val marked = ShortcutInfoCompat.Builder(shortcut).setShortLabel("Marked").build()
                ShortcutManagerCompat.pushDynamicShortcut(context, marked)
            }

            // The next process: chat 2 was renamed while no process ran.
            started().run {
                chats(count = 1)
                client.emit(updateUser(user(id = 2, firstName = "User 2")))
                val second = arrayOf(chatPosition(order = 998))
                val renamed = chat(id = 2, type = TdApi.ChatTypePrivate(2), title = "Grace", positions = second)
                client.emit(updateNewChat(renamed))
                runCurrent()
            }
            settle()

            val labels =
                ShortcutManagerCompat.getDynamicShortcuts(context).associate { it.id to it.shortLabel.toString() }
            assertEquals("Marked", labels[NotificationIntents.shortcutId(1)])
            assertEquals("Grace", labels[NotificationIntents.shortcutId(2)])
        }

    @Test
    fun `a target of an earlier process that is no longer recent is removed`() =
        runTest {
            started().run {
                chats(count = 9)
                runCurrent()
            }
            settle()

            // The next process: chat 10 became the most recent while no process ran, so chat 8 is ninth.
            started().run {
                chats(count = 9)
                client.emit(updateUser(user(id = 10, firstName = "User 10")))
                val first = arrayOf(chatPosition(order = 2_000))
                val newest = chat(id = 10, type = TdApi.ChatTypePrivate(10), title = "User 10", positions = first)
                client.emit(updateNewChat(newest))
                runCurrent()
            }
            settle()

            val ids = ShortcutManagerCompat.getDynamicShortcuts(RuntimeEnvironment.getApplication()).map { it.id }
            assertTrue(NotificationIntents.shortcutId(10) in ids)
            assertTrue(NotificationIntents.shortcutId(8) !in ids)
        }

    @Test
    fun `signed out, nothing is published`() =
        runTest {
            val harness = started(AuthState.WaitPhoneNumber)
            harness.chats(count = 3)
            runCurrent()
            settle()

            assertTrue(ShortcutManagerCompat.getDynamicShortcuts(harness.context).isEmpty())
        }
}
