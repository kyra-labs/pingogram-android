package dev.kyralabs.pingogram.feature.chats.ui

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ArchiveRowStyle
import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.model.UnreadChatCount
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.chat
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.folder
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * The archive row's styles and the lists' "Mark all as read" in [ChatListViewModel] (FR-L3, FR-L9): full, compact or
 * hidden as the device keeps it, hiding with undo, the archive's settings, and reading a folder or the main list.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatListArchiveRowTest : ChatListViewModelHarness() {
    // The archive row's style

    @Test
    fun `the row is full until the user changes it`() =
        runTest {
            val viewModel = withArchive()

            assertEquals(false, viewModel.state.archive?.isCompact)
            assertNull(viewModel.state.hiddenArchive)
        }

    @Test
    fun `a compact row shows as one line`() =
        runTest {
            archiveRow.style.value = ArchiveRowStyle.Compact

            assertEquals(ARCHIVE.copy(isCompact = true), withArchive().state.archive)
        }

    @Test
    fun `a hidden row leaves the archive to the top bar, on the main list only`() =
        runTest {
            archiveRow.style.value = ArchiveRowStyle.Hidden
            chats.folderList.value = persistentListOf(folder(1))
            val viewModel = withArchive()

            assertNull(viewModel.state.archive)
            assertEquals(ARCHIVE, viewModel.state.hiddenArchive)

            send(viewModel, ChatListEvent.SelectFolder(1))

            assertNull(viewModel.state.hiddenArchive)
        }

    @Test
    fun `an empty archive has no row and no button, whatever the style`() =
        runTest {
            archiveRow.style.value = ArchiveRowStyle.Hidden
            chats.setChats(ChatListType.Main, chat(id = 2))
            val viewModel = viewModel()

            assertNull(viewModel.state.archive)
            assertNull(viewModel.state.hiddenArchive)
        }

    @Test
    fun `the style changes from the row are saved and shown`() =
        runTest {
            val viewModel = withArchive()

            send(viewModel, ChatListEvent.SetArchiveRowStyle(ArchiveRowStyle.Compact))

            assertEquals(listOf(ArchiveRowStyle.Compact), archiveRow.saved)
            assertEquals(true, viewModel.state.archive?.isCompact)
        }

    @ParameterizedTest
    @MethodSource("shownStyles")
    fun `hiding the row offers to bring back the style it had`(shown: ArchiveRowStyle) =
        runTest {
            archiveRow.style.value = shown
            val viewModel = withArchive()
            val effects = effectsOf(viewModel)

            send(viewModel, ChatListEvent.SetArchiveRowStyle(ArchiveRowStyle.Hidden))

            assertEquals(listOf(ChatListEffect.ArchiveRowHidden(previous = shown)), effects)
            assertNull(viewModel.state.archive)

            send(viewModel, ChatListEvent.SetArchiveRowStyle(shown))

            assertEquals(shown == ArchiveRowStyle.Compact, viewModel.state.archive?.isCompact)
        }

    @Test
    fun `a style that cannot be saved is a snackbar, and the row stays`() =
        runTest {
            val error = DomainError.Unknown(code = 0, message = "Disk full")
            archiveRow.onSetStyle = { Outcome.Failure(error) }
            val viewModel = withArchive()
            val effects = effectsOf(viewModel)

            send(viewModel, ChatListEvent.SetArchiveRowStyle(ArchiveRowStyle.Hidden))

            assertEquals(listOf(ChatListEffect.ShowError(error)), effects)
            assertEquals(ARCHIVE, viewModel.state.archive)
        }

    @Test
    fun `the archive's top bar opens its settings`() =
        runTest {
            val viewModel = viewModel(kind = ChatListKind.Archive)
            val effects = effectsOf(viewModel)

            send(viewModel, ChatListEvent.OpenArchiveSettings)

            assertEquals(listOf(ChatListEffect.OpenArchiveSettings), effects)
        }

    // Mark all as read

    @Test
    fun `a tab offers to mark its chats as read while any is unread, muted ones included`() =
        runTest {
            chats.folderList.value = persistentListOf(folder(1, "Work"), folder(2, "Family"))
            chats.unreadCounts.value =
                mapOf(
                    // Two unread chats, both muted: no badge, but something to read.
                    ChatListType.Main to UnreadChatCount(10, 2, 0, 0, 0),
                    ChatListType.Folder(1) to UnreadChatCount(5, 3, 2, 0, 0),
                )

            val tabs = viewModel().state.folderTabs

            assertEquals(listOf(true, true, false), tabs.map { it.hasUnread })
            assertEquals(listOf(0, 2, 0), tabs.map { it.unreadCount })
        }

    @Test
    fun `marking all as read reads the folder, or the main list for All chats`() =
        runTest {
            val viewModel = viewModel()

            send(viewModel, ChatListEvent.MarkAllAsRead(folderId = 3), ChatListEvent.MarkAllAsRead(folderId = null))

            assertEquals(listOf(ChatListType.Folder(3), ChatListType.Main), archiveSettings.readLists)
        }

    @ParameterizedTest
    @MethodSource("errors")
    fun `marking all as read that fails is a snackbar`(error: DomainError) =
        runTest {
            archiveSettings.onReadList = { Outcome.Failure(error) }
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, ChatListEvent.MarkAllAsRead(folderId = null))

            assertEquals(listOf(ChatListEffect.ShowError(error)), effects)
        }

    @Test
    fun `the archive has no tabs to read and no archive row`() =
        runTest {
            archiveRow.style.value = ArchiveRowStyle.Compact
            chats.unreadCounts.value = mapOf(ChatListType.Archive to UnreadChatCount(5, 2, 0, 0, 0))

            val viewModel = viewModel(kind = ChatListKind.Archive)
            runCurrent()

            assertNull(viewModel.state.archive)
            assertNull(viewModel.state.hiddenArchive)
            assertTrue(viewModel.state.folderTabs.isEmpty())
            assertTrue(archiveRow.saved.isEmpty())
        }

    /** The main list with chats, and an archive of five chats, two of them unread. */
    private fun TestScope.withArchive(): ChatListViewModel {
        chats.unreadCounts.value = mapOf(ChatListType.Archive to UnreadChatCount(5, 2, 0, 0, 0))
        chats.setChats(ChatListType.Main, chat(id = 2), chat(id = 3))
        return viewModel()
    }

    private companion object {
        val ARCHIVE = ArchiveEntry(5, persistentListOf(), unreadCount = 2)

        @JvmStatic
        fun shownStyles() = listOf(ArchiveRowStyle.Full, ArchiveRowStyle.Compact)

        @JvmStatic
        fun errors() = listOf(DomainError.FloodWait(4), DomainError.Network, DomainError.Unauthorized)
    }
}
