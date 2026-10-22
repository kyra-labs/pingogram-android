package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.runtime.snapshots.Snapshot
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.AttachmentKind
import dev.kyralabs.pingogram.core.domain.model.ChatPermissions
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.ShareResult
import dev.kyralabs.pingogram.core.domain.model.SharedContent
import dev.kyralabs.pingogram.core.domain.repository.ShareRepository
import dev.kyralabs.pingogram.core.navigation.ShareRoute
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ADA
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.BASIC_GROUP_ID
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.GROUP_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ME
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.PRIVATE_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.basicGroup
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.groupChat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.user
import dev.kyralabs.pingogram.feature.chat.ui.share.ShareClose
import dev.kyralabs.pingogram.feature.chat.ui.share.ShareEvent
import dev.kyralabs.pingogram.feature.chat.ui.share.ShareInbox
import dev.kyralabs.pingogram.feature.chat.ui.share.ShareResults
import dev.kyralabs.pingogram.feature.chat.ui.share.ShareViewModel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The share sheet's view model (`ShareRoute`, `REQUIREMENTS.md` FR-N2): the chats a share can go to, one picked opens
 * it with the share in its composer, several get it in the app's scope after an explicit send with the comment, and the
 * sheet closes at once when the share is gone (process death). Nothing is ever sent without the send.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ShareViewModelTest : ChatViewModelHarness() {
    private val inbox = ShareInbox()
    private val results = ShareResults()
    private val shares = FakeShareRepository()

    /** What [results] got, in order. */
    private val reported = mutableListOf<ShareResult>()

    private val link = SharedContent(FormattedText("https://example.com\nA page", persistentListOf()))

    /** The share sheet for [content], offered to the inbox unless [offered] is false (process death). */
    private fun TestScope.shareViewModel(
        content: SharedContent = link,
        offered: Boolean = true,
    ): ShareViewModel {
        users.setUsers(user(ME), user(ADA, firstName = "Ada"))
        val ada = chat(title = "Ada Lovelace")
        val readOnly = chat(id = READ_ONLY, title = "News", permissions = ChatPermissions(canSendBasicMessages = false))
        chats.setChats(ada, chat(id = ME, title = "Me"), groupChat, readOnly)
        chats.mainList.value = listOf(ada, groupChat, readOnly)
        groups.basicGroupMap.value = mapOf(BASIC_GROUP_ID to basicGroup(memberCount = 3))
        val id = if (offered) inbox.offer(content) else UNKNOWN_SHARE
        val viewModel =
            ShareViewModel(
                route = ShareRoute(id),
                inbox = inbox,
                chats = chats,
                search = search,
                users = users,
                groups = groups,
                shares = shares,
                dispatcher = StandardTestDispatcher(testScheduler),
                applicationScope = backgroundScope,
                results = results,
            )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.sheet.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { results.results.collect { reported += it } }
        runCurrent()
        return viewModel
    }

    private fun TestScope.send(
        viewModel: ShareViewModel,
        vararg events: ShareEvent,
    ) {
        events.forEach {
            viewModel.onEvent(it)
            runCurrent()
        }
    }

    @Test
    fun `offers Saved Messages first, then the chats the user can write in`() =
        runViewModelTest {
            val viewModel = shareViewModel()

            val sheet = viewModel.sheet.value!!
            assertEquals(listOf(ME, PRIVATE_CHAT, GROUP_CHAT), sheet.targets.map { it.chatId })
            assertEquals("https://example.com A page", sheet.text)
            assertNull(viewModel.closed.value)
        }

    @Test
    fun `files are summed up by how many and how they go`() =
        runViewModelTest {
            val photos =
                SharedContent(files = persistentListOf("content://a", "content://b"), kind = AttachmentKind.Media)

            val sheet = shareViewModel(photos).sheet.value!!

            assertEquals(2, sheet.fileCount)
            assertEquals(AttachmentKind.Media, sheet.fileKind)
            assertEquals("", sheet.text)
        }

    @Test
    fun `the one chat picked opens with the share waiting in its composer`() =
        runViewModelTest {
            val viewModel = shareViewModel()

            send(viewModel, ShareEvent.ToggleTarget(GROUP_CHAT), ShareEvent.Send)

            assertEquals(ShareClose.OpenChat(GROUP_CHAT), viewModel.closed.value)
            val delivered = CompletableDeferred<SharedContent?>()
            backgroundScope.launch { inbox.deliveredTo(GROUP_CHAT).collect { delivered.complete(it) } }
            runCurrent()
            assertEquals(link, delivered.await())
            assertTrue(shares.calls.isEmpty())
        }

    @Test
    fun `several chats get the share after the send, the comment first, even once the sheet is gone`() =
        runViewModelTest {
            val answer = CompletableDeferred<ShareResult>()
            shares.onShare = { answer.await() }
            val viewModel = shareViewModel()

            send(viewModel, ShareEvent.ToggleTarget(PRIVATE_CHAT), ShareEvent.ToggleTarget(GROUP_CHAT))
            assertTrue(shares.calls.isEmpty())
            viewModel.comment.state.edit { append("For you") }
            Snapshot.sendApplyNotifications()
            send(viewModel, ShareEvent.Send)
            assertEquals(ShareClose.Done, viewModel.closed.value)
            answer.complete(ShareResult(sent = 2, failed = 0))
            runCurrent()

            assertEquals(listOf(Triple(listOf(PRIVATE_CHAT, GROUP_CHAT), link, "For you")), shares.calls)
            assertEquals(listOf(ShareResult(sent = 2, failed = 0)), reported)
        }

    @Test
    fun `a failed share is reported with its error`() =
        runViewModelTest {
            shares.onShare = { ShareResult(sent = 1, failed = 1, error = DomainError.SendingNotAllowed) }
            val viewModel = shareViewModel()

            send(viewModel, ShareEvent.ToggleTarget(PRIVATE_CHAT), ShareEvent.ToggleTarget(GROUP_CHAT), ShareEvent.Send)

            assertEquals(listOf(ShareResult(sent = 1, failed = 1, error = DomainError.SendingNotAllowed)), reported)
        }

    @Test
    fun `sending with no chat picked, and dismissing, send nothing`() =
        runViewModelTest {
            val viewModel = shareViewModel()

            send(viewModel, ShareEvent.Send)
            assertNull(viewModel.closed.value)
            send(viewModel, ShareEvent.ToggleTarget(GROUP_CHAT), ShareEvent.ToggleTarget(GROUP_CHAT), ShareEvent.Send)
            assertNull(viewModel.closed.value)
            send(viewModel, ShareEvent.Dismiss)

            assertEquals(ShareClose.Done, viewModel.closed.value)
            assertTrue(shares.calls.isEmpty())
        }

    @Test
    fun `a query searches the chats once it stays`() =
        runViewModelTest {
            val viewModel = shareViewModel()

            send(viewModel, ShareEvent.QueryChanged("Ada"))
            advanceTimeBy(DEBOUNCE + 1)
            runCurrent()

            assertEquals(listOf("Ada"), search.chatQueries)
        }

    @Test
    fun `after process death the share is gone, and the sheet closes at once`() =
        runViewModelTest {
            val viewModel = shareViewModel(offered = false)

            assertEquals(ShareClose.Done, viewModel.closed.value)
            assertNull(viewModel.sheet.value)
        }

    @Test
    fun `a share is taken by one sheet only`() =
        runViewModelTest {
            val id = inbox.offer(link)

            assertEquals(link, inbox.take(id))
            assertNull(inbox.take(id))
        }

    /** Records each share and answers with [onShare]. */
    private class FakeShareRepository : ShareRepository {
        val calls = mutableListOf<Triple<List<Long>, SharedContent, String>>()
        var onShare: suspend () -> ShareResult = { ShareResult(sent = 0, failed = 0) }

        override suspend fun share(
            chatIds: List<Long>,
            content: SharedContent,
            comment: FormattedText,
        ): ShareResult {
            calls += Triple(chatIds, content, comment.text)
            return onShare()
        }
    }

    private companion object {
        const val UNKNOWN_SHARE = 99L
        const val READ_ONLY = 77L
        const val DEBOUNCE = 300L
    }
}
