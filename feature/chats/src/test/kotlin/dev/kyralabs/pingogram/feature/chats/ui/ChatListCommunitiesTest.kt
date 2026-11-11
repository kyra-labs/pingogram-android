package dev.kyralabs.pingogram.feature.chats.ui

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.chat
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.folder
import dev.kyralabs.pingogram.feature.chats.ui.preview.ChatListPreviewData
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** A community's chats shown as one entry in the main list ([ChatListCommunities], the owner's board 19b). */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatListCommunitiesTest : ChatListViewModelHarness() {
    @Test
    fun `a collapsed community's chats become one entry where its newest chat was`() =
        runTest {
            chats.setChats(
                ChatListType.Main,
                chat(id = 2),
                chat(id = 3, unreadCount = 4),
                chat(id = 4),
                chat(id = 5, unreadCount = 1),
            )
            communities.add(COMMUNITY, "Kyra Labs", chatIds = listOf(3, 5, 99))

            val viewModel = viewModel()

            assertEquals(listOf(2L, 4L), viewModel.ids())
            val entry = viewModel.communities().single()
            assertEquals(listOf(3L, 5L), entry.chats.map { it.id })
            assertEquals(1, entry.before)
            assertEquals(5, entry.unreadCount)
            assertEquals("Kyra Labs", entry.name)
            assertFalse(entry.isExpanded)
        }

    @Test
    fun `a community that does not collapse its chats leaves them in their places`() =
        runTest {
            chats.setChats(ChatListType.Main, chat(id = 2), chat(id = 3))
            communities.add(COMMUNITY, "Kyra Labs", chatIds = listOf(3), collapsed = false)

            val viewModel = viewModel()

            assertEquals(listOf(2L, 3L), viewModel.ids())
            assertTrue(viewModel.communities().isEmpty())
        }

    @Test
    fun `pinned chats stay pinned rather than in their community`() =
        runTest {
            val main = listOf(ChatListType.Main)
            chats.setChats(ChatListType.Main, chat(id = 2, pinnedIn = main), chat(id = 3), chat(id = 4))
            communities.add(COMMUNITY, "Kyra Labs", chatIds = listOf(2, 4))

            val viewModel = viewModel()

            assertEquals(listOf(2L, 3L), viewModel.ids())
            assertEquals(1, (viewModel.state.content as ChatListContent.Content).pinnedCount)
            assertEquals(
                listOf(4L),
                viewModel
                    .communities()
                    .single()
                    .chats
                    .map { it.id },
            )
        }

    @Test
    fun `a community without a chat in the list has no entry`() =
        runTest {
            chats.setChats(ChatListType.Main, chat(id = 2))
            communities.add(COMMUNITY, "Kyra Labs", chatIds = listOf(9))

            assertTrue(viewModel().communities().isEmpty())
        }

    @Test
    fun `a muted community's entry says so`() =
        runTest {
            chats.setChats(ChatListType.Main, chat(id = 2))
            communities.add(COMMUNITY, "Kyra Labs", chatIds = listOf(2), muted = true)

            assertTrue(viewModel().communities().single().isMuted)
        }

    @Test
    fun `folders and the archive show the chats on their own`() =
        runTest {
            val folder = ChatListType.Folder(1)
            chats.folderList.value = persistentListOf(folder(1))
            chats.setChats(folder, chat(id = 2, lists = listOf(folder)), chat(id = 3, lists = listOf(folder)))
            chats.setChats(ChatListType.Archive, chat(id = 4, lists = listOf(ChatListType.Archive)))
            communities.add(COMMUNITY, "Kyra Labs", chatIds = listOf(2, 4))
            val viewModel = viewModel()

            send(viewModel, ChatListEvent.SelectFolder(1))

            assertEquals(listOf(2L, 3L), viewModel.ids())
            assertTrue(viewModel.communities().isEmpty())
            val archive = viewModel(handle = SavedStateHandle(), kind = ChatListKind.Archive)
            assertEquals(listOf(4L), archive.ids())
        }

    @Test
    fun `tapping an entry expands it, and the choice survives process death`() =
        runTest {
            chats.setChats(ChatListType.Main, chat(id = 2))
            communities.add(COMMUNITY, "Kyra Labs", chatIds = listOf(2))
            val viewModel = viewModel()

            send(viewModel, ChatListEvent.ToggleCommunity(COMMUNITY))

            assertTrue(viewModel.communities().single().isExpanded)
            val restored =
                viewModel(handle = SavedStateHandle(savedState.keys().associateWith { savedState.get<Any>(it) }))
            assertTrue(restored.communities().single().isExpanded)
            send(viewModel, ChatListEvent.ToggleCommunity(COMMUNITY))
            assertFalse(viewModel.communities().single().isExpanded)
        }

    @Test
    fun `an expanded community's chats can be selected`() =
        runTest {
            chats.setChats(ChatListType.Main, chat(id = 2), chat(id = 3))
            communities.add(COMMUNITY, "Kyra Labs", chatIds = listOf(3))
            val viewModel = viewModel()

            send(viewModel, ChatListEvent.ToggleCommunity(COMMUNITY), ChatListEvent.ChatLongClicked(3))

            assertEquals(setOf(3L), viewModel.state.selection?.chatIds)
        }

    @Test
    fun `showing the chats separately turns the collapsing off`() =
        runTest {
            chats.setChats(ChatListType.Main, chat(id = 2), chat(id = 3))
            communities.add(COMMUNITY, "Kyra Labs", chatIds = listOf(3))
            val viewModel = viewModel()

            send(viewModel, ChatListEvent.ShowCommunityChatsSeparately(COMMUNITY))
            runCurrent()

            assertEquals(listOf(COMMUNITY to false), communities.collapsedCalls)
            assertEquals(listOf(2L, 3L), viewModel.ids())
            assertTrue(viewModel.communities().isEmpty())
        }

    @Test
    fun `a refused change is a snackbar`() =
        runTest {
            chats.setChats(ChatListType.Main, chat(id = 2))
            communities.add(COMMUNITY, "Kyra Labs", chatIds = listOf(2))
            communities.collapseError = DomainError.Network
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, ChatListEvent.ShowCommunityChatsSeparately(COMMUNITY))
            runCurrent()

            assertEquals(listOf(ChatListEffect.ShowError(DomainError.Network)), effects)
            assertEquals(1, viewModel.communities().size)
        }

    @Test
    fun `rowIndexOf counts the entries and their open chats`() {
        val rows = persistentListOf(row(2), row(4))
        val entry =
            CommunityEntry(
                id = COMMUNITY,
                name = "Kyra Labs",
                photo = null,
                chats = persistentListOf(row(3), row(5)),
                unreadCount = 0,
                isMuted = false,
                isExpanded = true,
                before = 1,
            )
        val content = ChatListContent.Content(rows, communities = persistentListOf(entry))

        assertEquals(0, content.rowIndexOf(2L))
        assertEquals(1, content.rowIndexOf(communityKey(COMMUNITY)))
        assertEquals(3, content.rowIndexOf(5L))
        assertEquals(4, content.rowIndexOf(4L))
        assertEquals(null, content.rowIndexOf(7L))
    }

    private fun row(id: Long) = ChatListPreviewData.items.first().copy(id = id)

    private fun ChatListViewModel.communities() = (state.content as ChatListContent.Content).communities

    private companion object {
        const val COMMUNITY = 70L
    }
}
