package dev.kyralabs.pingogram.feature.chat.ui.topics

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.MemberPermissions
import dev.kyralabs.pingogram.core.domain.model.MemberRole
import dev.kyralabs.pingogram.core.domain.model.TopicColor
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.FORUM_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.forumTopic
import dev.kyralabs.pingogram.feature.chat.ui.FakeForumTopicRepository
import dev.kyralabs.pingogram.feature.chat.ui.search.FakeGroupInfoRepository
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The top bar's actions of a topic's conversation ([TopicActionsViewModel], `REQUIREMENTS.md` FR-C9): the topic's
 * menu for the user's rights, its toggles, renaming, deleting and leaving, the refusals and the saved state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TopicActionsViewModelTest {
    private val topics = FakeForumTopicRepository()
    private val groups = FakeGroupInfoRepository()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(
        topicId: Int = 7,
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ): TopicActionsViewModel {
        val viewModel =
            TopicActionsViewModel(
                chatId = FORUM_CHAT,
                topicId = topicId,
                repository = topics,
                groupInfoRepository = groups,
                savedStateHandle = savedStateHandle,
                dispatcher = StandardTestDispatcher(testScheduler),
            )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        runCurrent()
        return viewModel
    }

    private fun forum(role: MemberRole = topicAdmin()) {
        groups.info.value = Outcome.Success(forumInfo(role))
        topics.setTopics(
            FORUM_CHAT,
            forumTopic(1, "General", isGeneral = true),
            forumTopic(7, "Help", isClosed = true, color = TopicColor.Green),
        )
    }

    @Test
    fun `an administrator gets the topic's menu, as the topic is now`() =
        runTest {
            forum()

            val menu = viewModel().uiState.value.menu

            assertEquals(
                TopicMenuState(
                    topicId = 7,
                    name = "Help",
                    isGeneral = false,
                    isClosed = true,
                    isPinned = false,
                    isHidden = false,
                    actions = TopicActions(canEdit = true, canClose = true, canPin = true, canDelete = true),
                ),
                menu,
            )
        }

    @Test
    fun `a member without rights, or a topic not there yet, has no button`() =
        runTest {
            forum(MemberRole.Member)
            assertNull(viewModel().uiState.value.menu)

            groups.info.value = Outcome.Success(forumInfo(topicAdmin()))
            assertNull(viewModel(topicId = 42).uiState.value.menu)
        }

    @Test
    fun `rights that cannot be read offer nothing`() =
        runTest {
            forum()
            groups.info.value = Outcome.Failure(DomainError.Network)

            assertNull(viewModel().uiState.value.menu)
        }

    @Test
    fun `reopening and renaming go to Telegram`() =
        runTest {
            forum()
            val viewModel = viewModel()

            viewModel.onEvent(TopicEvent.SetClosed(7, isClosed = false))
            viewModel.onEvent(TopicEvent.Edit(7))
            viewModel.onEvent(TopicEvent.NameChanged("Support"))
            viewModel.onEvent(TopicEvent.Save)
            runCurrent()

            assertEquals(listOf("close 7 false", "rename 7 Support"), topics.requests)
            assertNull(viewModel.uiState.value.management.editor)
        }

    @Test
    fun `a deleted topic leaves the conversation`() =
        runTest {
            forum(MemberRole.Owner(isMember = true))
            val viewModel = viewModel()

            viewModel.effects.test {
                viewModel.onEvent(TopicEvent.Delete(7))
                runCurrent()
                assertEquals(TopicDeleteUiState(7, "Help"), viewModel.uiState.value.management.deleting)
                viewModel.onEvent(TopicEvent.ConfirmDelete)
                runCurrent()

                assertEquals(TopicEffect.Deleted(7), awaitItem())
            }
        }

    @Test
    fun `refusals are effects for a snackbar`() =
        runTest {
            forum()
            val viewModel = viewModel()

            viewModel.effects.test {
                listOf(DomainError.FloodWait(5), DomainError.Network, DomainError.Unauthorized).forEach { error ->
                    topics.onManage = { Outcome.Failure(error) }
                    viewModel.onEvent(TopicEvent.SetPinned(7, isPinned = true))
                    runCurrent()
                    assertEquals(TopicEffect.Failed(error), awaitItem())
                }
            }
        }

    @Test
    fun `the editor survives process death`() =
        runTest {
            forum()
            val savedState = SavedStateHandle()
            val first = viewModel(savedStateHandle = savedState)
            first.onEvent(TopicEvent.Edit(7))
            first.onEvent(TopicEvent.NameChanged("Support"))

            val restored = viewModel(savedStateHandle = savedState)

            assertEquals(
                TopicEditorUiState(topicId = 7, name = "Support", color = TopicColor.Green),
                restored.uiState.value.management.editor,
            )
        }

    @Test
    fun `members who may create topics manage their own topic only`() =
        runTest {
            groups.info.value = Outcome.Success(forumInfo(MemberRole.Member, MemberPermissions(canCreateTopics = true)))
            topics.setTopics(FORUM_CHAT, forumTopic(7, "Help", isOutgoing = true), forumTopic(8, "Chat"))

            assertEquals(
                TopicActions(canEdit = true, canClose = true),
                viewModel(7)
                    .uiState.value.menu
                    ?.actions,
            )
            assertNull(viewModel(8).uiState.value.menu)
        }
}
