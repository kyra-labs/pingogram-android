package dev.kyralabs.pingogram.feature.chats.ui

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.model.UnreadChatCount
import dev.kyralabs.pingogram.core.domain.repository.LocalChatNamesRepository
import dev.kyralabs.pingogram.core.domain.repository.UnansweredDismissalRepository
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.chat
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/** The Chats tab's header while the names on this phone and Unanswered's dismissals are still being read. */
class ChatListHeaderWaitTest : ChatListViewModelHarness() {
    override val localChatNames =
        object : LocalChatNamesRepository {
            override val names: Flow<ImmutableMap<Long, String>> = flow { awaitCancellation() }
        }

    override val dismissals =
        object : UnansweredDismissalRepository {
            override val dismissed: Flow<ImmutableMap<Long, Long>> = flow { awaitCancellation() }

            override suspend fun dismiss(
                chatId: Long,
                messageId: Long,
            ): Outcome<Unit> = Outcome.Success(Unit)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `the header counts show without waiting for the names or the dismissals, Unanswered not offered yet`() =
        runTest {
            chats.setChats(ChatListType.Main, chat(id = 2, unreadCount = 3))
            chats.unreadCounts.value =
                mapOf(
                    ChatListType.Main to
                        UnreadChatCount(
                            totalCount = 1,
                            unreadCount = 1,
                            unreadUnmutedCount = 1,
                            markedAsUnreadCount = 0,
                            markedAsUnreadUnmutedCount = 0,
                        ),
                )
            val viewModel = createViewModel()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.summary.collect {} }
            runCurrent()

            assertEquals(1, viewModel.summary.value.unreadChats)
            assertFalse(viewModel.summary.value.hasUnanswered)
        }
}
