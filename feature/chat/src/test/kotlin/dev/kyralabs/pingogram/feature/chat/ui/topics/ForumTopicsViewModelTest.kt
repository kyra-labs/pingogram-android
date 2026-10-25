package dev.kyralabs.pingogram.feature.chat.ui.topics

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.MemberPermissions
import dev.kyralabs.pingogram.core.domain.model.MemberRole
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.TopicColor
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ADA
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.FORUM_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ME
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.NOW
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.forumChat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.forumTopic
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.user
import dev.kyralabs.pingogram.feature.chat.ui.ChatSubtitle
import dev.kyralabs.pingogram.feature.chat.ui.FakeChatRepository
import dev.kyralabs.pingogram.feature.chat.ui.FakeForumTopicRepository
import dev.kyralabs.pingogram.feature.chat.ui.FakeTimePatterns
import dev.kyralabs.pingogram.feature.chat.ui.FakeUserRepository
import dev.kyralabs.pingogram.feature.chat.ui.FakeWallClock
import dev.kyralabs.pingogram.feature.chat.ui.search.FakeGroupInfoRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Locale

/**
 * The topic list of [ForumTopicsViewModel] (`REQUIREMENTS.md` FR-C9): the header, the rows, the pages as the end comes
 * near, and the errors of the first and the next page; managing the topics: what the user's rights offer, the new
 * topic flow, renaming, the toggles, deleting, reordering the pinned topics, the refusals and the saved state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ForumTopicsViewModelTest {
    private val chats = FakeChatRepository()
    private val topics = FakeForumTopicRepository()
    private val users = FakeUserRepository().apply { myUserId.value = ME }
    private val groups = FakeGroupInfoRepository()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()): ForumTopicsViewModel {
        val viewModel =
            ForumTopicsViewModel(
                chatId = FORUM_CHAT,
                chatRepository = chats,
                repository = topics,
                groupInfoRepository = groups,
                userRepository = users,
                savedStateHandle = savedStateHandle,
                clock = FakeWallClock(NOW),
                timePatterns = FakeTimePatterns(),
                dispatcher = StandardTestDispatcher(testScheduler),
                locale = { Locale.US },
            )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        runCurrent()
        return viewModel
    }

    private val ForumTopicsViewModel.content get() = uiState.value.content

    private val ForumTopicsViewModel.rows get() = (content as TopicsContent.Content).topics

    @Test
    fun `loads the first page after the first frame, and shows the forum's topics`() =
        runTest {
            chats.setChats(forumChat)
            users.setUsers(user(ADA, firstName = "Ada", lastName = "Lovelace"))
            val last = message(9, chatId = FORUM_CHAT, sender = MessageSender.User(ADA))
            topics.onLoadMore = {
                topics.setTopics(
                    FORUM_CHAT,
                    forumTopic(1, "General", isGeneral = true),
                    forumTopic(7, "Help", lastMessage = last, unreadCount = 2, color = TopicColor.Green),
                )
                Outcome.Success(Unit)
            }

            val viewModel = viewModel()

            assertEquals(listOf(FORUM_CHAT), topics.loadRequests)
            val help = viewModel.rows[1]
            assertEquals(
                listOf<Any?>("Help", 2, TopicIcon(TopicColor.Green, isGeneral = false)),
                listOf(help.name, help.unreadCount, help.icon),
            )
            assertEquals(UiText.Plain("Ada Lovelace").bidiIsolated(), help.sender)
            assertEquals(UiText.Plain("Message 9"), help.preview)
            assertEquals("9:41 AM", help.time)
            assertNull(viewModel.rows[0].preview)
            assertEquals(
                UiText.Plain("Compose Developers"),
                viewModel.uiState.value.header
                    ?.title,
            )
            assertEquals(
                ChatSubtitle.Status(UiText.plural(R.plurals.topics_subtitle, 2, 2)),
                viewModel.uiState.value.header
                    ?.subtitle,
            )
        }

    @Test
    fun `the user's own last message reads as theirs, the forum's own as nobody's`() =
        runTest {
            chats.setChats(forumChat)
            topics.setTopics(
                FORUM_CHAT,
                forumTopic(1, lastMessage = ChatFixtures.outgoing(3, chatId = FORUM_CHAT)),
                forumTopic(2, lastMessage = message(4, chatId = FORUM_CHAT, sender = MessageSender.Chat(FORUM_CHAT))),
            )

            val viewModel = viewModel()

            assertEquals(
                UiText.resource(dev.kyralabs.pingogram.core.ui.R.string.preview_sender_you),
                viewModel.rows[0].sender,
            )
            assertNull(viewModel.rows[1].sender)
        }

    @Test
    fun `shows the loading state until the first page is there`() =
        runTest {
            chats.setChats(forumChat)
            val page = CompletableDeferred<Outcome<Unit>>()
            topics.onLoadMore = { page.await() }

            val viewModel = viewModel()

            assertEquals(TopicsContent.Loading, viewModel.content)
            page.complete(Outcome.Success(Unit))
        }

    @Test
    fun `a forum without topics is empty`() =
        runTest {
            chats.setChats(forumChat)
            topics.setTopics(FORUM_CHAT)

            assertEquals(TopicsContent.Empty, viewModel().content)
        }

    @Test
    fun `a failed first page shows the error, and a retry loads it`() =
        runTest {
            chats.setChats(forumChat)
            topics.onLoadMore = { Outcome.Failure(DomainError.FloodWait(30)) }
            val viewModel = viewModel()
            assertEquals(TopicsContent.Error(DomainError.FloodWait(30)), viewModel.content)

            topics.onLoadMore = {
                topics.setTopics(FORUM_CHAT, forumTopic(3))
                Outcome.Success(Unit)
            }
            viewModel.onEvent(ForumTopicsEvent.Retry)
            runCurrent()

            assertEquals(listOf(3), viewModel.rows.map { it.id })
        }

    @Test
    fun `the end coming near loads the next pages until every topic is loaded`() =
        runTest {
            chats.setChats(forumChat)
            topics.setTopics(FORUM_CHAT, forumTopic(1), reachedEnd = false)
            var pages = 0
            topics.onLoadMore = {
                pages++
                topics.setTopics(FORUM_CHAT, *(1..pages).map { forumTopic(it) }.toTypedArray(), reachedEnd = pages >= 3)
                Outcome.Success(Unit)
            }
            val viewModel = viewModel()

            viewModel.onEvent(ForumTopicsEvent.NearEndChanged(true))
            runCurrent()

            assertEquals(3, pages)
            assertEquals(true, (viewModel.content as TopicsContent.Content).reachedEnd)
        }

    @Test
    fun `a failed next page shows below the topics`() =
        runTest {
            chats.setChats(forumChat)
            topics.setTopics(FORUM_CHAT, forumTopic(1), reachedEnd = false)
            val viewModel = viewModel()
            topics.onLoadMore = { Outcome.Failure(DomainError.Network) }

            viewModel.onEvent(ForumTopicsEvent.NearEndChanged(true))
            runCurrent()

            assertEquals(DomainError.Network, (viewModel.content as TopicsContent.Content).moreError)
        }

    private fun TestScope.managedForum(
        role: MemberRole = topicAdmin(),
        permissions: MemberPermissions = MemberPermissions(),
    ) {
        chats.setChats(forumChat)
        groups.info.value = Outcome.Success(forumInfo(role, permissions))
        topics.setTopics(
            FORUM_CHAT,
            forumTopic(1, "General", isGeneral = true, isHidden = true),
            forumTopic(5, "Rules", isPinned = true),
            forumTopic(3, "News", isPinned = true),
            forumTopic(7, "Help", isOutgoing = true),
            forumTopic(8, "Chat"),
        )
    }

    private val ForumTopicsViewModel.management get() = uiState.value.management

    @Test
    fun `a member without rights gets no menus but their own topic's, no "New topic", and no hidden General`() =
        runTest {
            managedForum(role = MemberRole.Member)

            val viewModel = viewModel()

            assertFalse(viewModel.uiState.value.canCreate)
            assertEquals(listOf(5, 3, 7, 8), viewModel.rows.map { it.id })
            assertEquals(listOf(7), viewModel.rows.filter { it.menu != null }.map { it.id })
            assertFalse((viewModel.content as TopicsContent.Content).canReorder)
        }

    @Test
    fun `before the rights are known nothing is offered`() =
        runTest {
            managedForum()
            groups.info.value = null

            val viewModel = viewModel()

            assertFalse(viewModel.uiState.value.canCreate)
            assertTrue(viewModel.rows.all { it.menu == null })
        }

    @Test
    fun `an administrator who manages topics gets every menu, the hidden General and the pinned topics' order`() =
        runTest {
            managedForum()

            val viewModel = viewModel()

            assertTrue(viewModel.uiState.value.canCreate)
            assertEquals(listOf(1, 5, 3, 7, 8), viewModel.rows.map { it.id })
            val general = viewModel.rows[0]
            assertTrue(general.isHidden)
            assertEquals(TopicActions(canEdit = true, canHideGeneral = true), general.menu?.actions)
            assertEquals(
                TopicActions(canEdit = true, canClose = true, canPin = true, canDelete = true),
                viewModel.rows[1].menu?.actions,
            )
            assertTrue((viewModel.content as TopicsContent.Content).canReorder)
        }

    @Test
    fun `a member who may create topics manages only their own`() =
        runTest {
            managedForum(role = MemberRole.Member, permissions = MemberPermissions(canCreateTopics = true))

            val viewModel = viewModel()

            assertTrue(viewModel.uiState.value.canCreate)
            assertEquals(listOf(7), viewModel.rows.filter { it.menu != null }.map { it.id })
            assertEquals(
                TopicActions(canEdit = true, canClose = true),
                viewModel.rows
                    .first { it.id == 7 }
                    .menu
                    ?.actions,
            )
        }

    @Test
    fun `"New topic" creates the topic with the name and color picked, and opens it`() =
        runTest {
            managedForum()
            val viewModel = viewModel()

            viewModel.effects.test {
                viewModel.onTopicEvent(TopicEvent.NewTopic)
                runCurrent()
                assertEquals(
                    TopicEditorUiState(topicId = null, name = "", color = TopicColor.Blue),
                    viewModel.management.editor,
                )
                assertFalse(viewModel.management.editor!!.canSave)
                viewModel.onTopicEvent(TopicEvent.NameChanged("Plans"))
                viewModel.onTopicEvent(TopicEvent.ColorPicked(TopicColor.Rose))
                viewModel.onTopicEvent(TopicEvent.Save)
                runCurrent()

                assertEquals(ForumTopicsEffect.OpenTopic(99), awaitItem())
                assertEquals(listOf("create Plans Rose"), topics.requests)
                assertNull(viewModel.management.editor)
            }
        }

    @Test
    fun `a refused name stays in the editor with its error, and a name too long cannot be saved`() =
        runTest {
            managedForum()
            topics.onManage = { Outcome.Failure(DomainError.TitleInvalid) }
            val viewModel = viewModel()

            viewModel.onTopicEvent(TopicEvent.NewTopic)
            viewModel.onTopicEvent(TopicEvent.NameChanged("Plans"))
            viewModel.onTopicEvent(TopicEvent.Save)
            runCurrent()

            val editor = viewModel.management.editor!!
            assertEquals(DomainError.TitleInvalid, editor.error)
            assertFalse(editor.isSaving)
            viewModel.onTopicEvent(TopicEvent.NameChanged("A".repeat(129)))
            runCurrent()
            assertNull(viewModel.management.editor!!.error)
            assertTrue(viewModel.management.editor!!.isTooLong)
            viewModel.onTopicEvent(TopicEvent.Save)
            runCurrent()
            assertEquals(1, topics.requests.size)
        }

    @Test
    fun `a flood wait, the network or a logged-out session keep the editor open with the error`() =
        runTest {
            managedForum()
            val viewModel = viewModel()
            listOf(DomainError.FloodWait(30), DomainError.Network, DomainError.Unauthorized).forEach { error ->
                topics.onManage = { Outcome.Failure(error) }

                viewModel.onTopicEvent(TopicEvent.NewTopic)
                viewModel.onTopicEvent(TopicEvent.NameChanged("Plans"))
                viewModel.onTopicEvent(TopicEvent.Save)
                runCurrent()

                assertEquals(error, viewModel.management.editor?.error)
            }
        }

    @Test
    fun `"Edit name" renames the topic`() =
        runTest {
            managedForum()
            val viewModel = viewModel()

            viewModel.onTopicEvent(TopicEvent.Edit(7))
            runCurrent()
            assertEquals(
                TopicEditorUiState(topicId = 7, name = "Help", color = TopicColor.Blue),
                viewModel.management.editor,
            )
            viewModel.onTopicEvent(TopicEvent.NameChanged("Help desk"))
            viewModel.onTopicEvent(TopicEvent.Save)
            runCurrent()

            assertEquals(listOf("rename 7 Help desk"), topics.requests)
            assertNull(viewModel.management.editor)
        }

    @Test
    fun `the menu's toggles go to Telegram, and a refusal is a snackbar`() =
        runTest {
            managedForum()
            val viewModel = viewModel()

            viewModel.effects.test {
                viewModel.onTopicEvent(TopicEvent.SetClosed(7, isClosed = true))
                viewModel.onTopicEvent(TopicEvent.SetPinned(8, isPinned = true))
                viewModel.onTopicEvent(TopicEvent.SetGeneralHidden(isHidden = false))
                runCurrent()
                assertEquals(listOf("close 7 true", "pin 8 true", "hide general false"), topics.requests)

                topics.onManage = { Outcome.Failure(DomainError.AdminRightsRequired) }
                viewModel.onTopicEvent(TopicEvent.SetClosed(8, isClosed = true))
                runCurrent()
                assertEquals(ForumTopicsEffect.Failed(DomainError.AdminRightsRequired), awaitItem())
            }
        }

    @Test
    fun `deleting asks first, then deletes the topic and says so`() =
        runTest {
            managedForum()
            val viewModel = viewModel()

            viewModel.effects.test {
                viewModel.onTopicEvent(TopicEvent.Delete(7))
                runCurrent()
                assertEquals(TopicDeleteUiState(7, "Help"), viewModel.management.deleting)
                assertTrue(topics.requests.isEmpty())

                viewModel.onTopicEvent(TopicEvent.ConfirmDelete)
                runCurrent()

                assertEquals(ForumTopicsEffect.TopicDeleted, awaitItem())
                assertEquals(listOf("delete 7"), topics.requests)
                assertNull(viewModel.management.deleting)
            }
        }

    @Test
    fun `a refused delete closes the question with a snackbar, and dismissing it deletes nothing`() =
        runTest {
            managedForum()
            topics.onManage = { Outcome.Failure(DomainError.Network) }
            val viewModel = viewModel()

            viewModel.effects.test {
                viewModel.onTopicEvent(TopicEvent.Delete(7))
                viewModel.onTopicEvent(TopicEvent.ConfirmDelete)
                runCurrent()
                assertEquals(ForumTopicsEffect.Failed(DomainError.Network), awaitItem())

                viewModel.onTopicEvent(TopicEvent.Delete(8))
                viewModel.onTopicEvent(TopicEvent.DismissDelete)
                runCurrent()
                assertNull(viewModel.management.deleting)
                assertEquals(listOf("delete 7"), topics.requests)
            }
        }

    @Test
    fun `dragged pinned topics show in their new order at once, and Telegram's refusal takes it back`() =
        runTest {
            managedForum()
            val answer = CompletableDeferred<Outcome<Unit>>()
            topics.onManage = { answer.await() }
            val viewModel = viewModel()

            viewModel.effects.test {
                viewModel.onEvent(ForumTopicsEvent.Reorder(listOf(3, 5)))
                runCurrent()
                assertEquals(listOf(1, 3, 5, 7, 8), viewModel.rows.map { it.id })
                assertEquals(listOf("reorder [3, 5]"), topics.requests)

                answer.complete(Outcome.Failure(DomainError.AdminRightsRequired))
                runCurrent()

                assertEquals(ForumTopicsEffect.Failed(DomainError.AdminRightsRequired), awaitItem())
                assertEquals(listOf(1, 5, 3, 7, 8), viewModel.rows.map { it.id })
            }
        }

    @Test
    fun `screen readers move a pinned topic up or down, and not past the ends`() =
        runTest {
            managedForum()
            val viewModel = viewModel()

            viewModel.onEvent(ForumTopicsEvent.Move(3, up = true))
            runCurrent()
            viewModel.onEvent(ForumTopicsEvent.Move(5, up = true))
            runCurrent()

            assertEquals(listOf("reorder [3, 5]"), topics.requests)
        }

    @Test
    fun `the editor and the question survive process death`() =
        runTest {
            managedForum()
            val savedState = SavedStateHandle()
            val first = viewModel(savedState)
            first.onTopicEvent(TopicEvent.NewTopic)
            first.onTopicEvent(TopicEvent.NameChanged("Plans"))
            first.onTopicEvent(TopicEvent.ColorPicked(TopicColor.Violet))
            first.onTopicEvent(TopicEvent.Delete(8))

            val restored = viewModel(savedState)

            assertEquals(
                TopicEditorUiState(topicId = null, name = "Plans", color = TopicColor.Violet),
                restored.management.editor,
            )
            assertEquals(TopicDeleteUiState(8, "Chat"), restored.management.deleting)
        }
}
