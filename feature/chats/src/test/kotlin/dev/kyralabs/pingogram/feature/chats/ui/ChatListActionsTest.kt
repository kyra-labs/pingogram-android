package dev.kyralabs.pingogram.feature.chats.ui

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.DeleteChatAction
import dev.kyralabs.pingogram.core.domain.model.DeleteChatOptions
import dev.kyralabs.pingogram.core.domain.repository.SwipeAction
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.ME
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.chat
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.folder
import dev.kyralabs.pingogram.feature.chats.ui.ChatRowAction.Archive
import dev.kyralabs.pingogram.feature.chats.ui.ChatRowAction.Delete
import dev.kyralabs.pingogram.feature.chats.ui.ChatRowAction.MarkAsRead
import dev.kyralabs.pingogram.feature.chats.ui.ChatRowAction.MarkAsUnread
import dev.kyralabs.pingogram.feature.chats.ui.ChatRowAction.Mute
import dev.kyralabs.pingogram.feature.chats.ui.ChatRowAction.Pin
import dev.kyralabs.pingogram.feature.chats.ui.ChatRowAction.Unarchive
import dev.kyralabs.pingogram.feature.chats.ui.ChatRowAction.Unmute
import dev.kyralabs.pingogram.feature.chats.ui.ChatRowAction.Unpin
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import dev.kyralabs.pingogram.core.ui.R as UiR

/** What [ChatListViewModel] does to chats (FR-L4, FR-L8): row actions, toolbar actions, archive with undo, delete. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatListActionsTest : ChatListViewModelHarness() {
    // The actions of a row

    @Test
    fun `a row offers what fits its chat, in the order screen readers list them`() =
        runTest {
            chats.setChats(
                ChatListType.Main,
                chat(id = 2, unreadCount = 1, pinnedIn = listOf(ChatListType.Main), isMuted = true),
                chat(id = 3),
            )
            val viewModel = viewModel()

            assertEquals(listOf(MarkAsRead, Archive, Unpin, Unmute, Delete), viewModel.item(2).actions)
            assertEquals(listOf(MarkAsUnread, Archive, Pin, Mute, Delete), viewModel.item(3).actions)
        }

    @Test
    fun `a chat marked as unread is marked as read`() =
        runTest {
            assertEquals(MarkAsRead, rowOf(chat(id = 2, isMarkedAsUnread = true)).actions.first())
        }

    @Test
    fun `Saved Messages cannot be muted`() =
        runTest {
            assertEquals(listOf(MarkAsUnread, Archive, Pin, Delete), rowOf(chat(id = ME)).actions)
        }

    @Test
    fun `an archived chat in a folder is unarchived`() =
        runTest {
            chats.folderList.value = persistentListOf(folder(1))
            val archived = chat(id = 4, lists = listOf(ChatListType.Folder(1), ChatListType.Archive))
            chats.setChats(ChatListType.Folder(1), archived)
            val viewModel = viewModel()

            send(viewModel, ChatListEvent.SelectFolder(1))

            assertEquals(Unarchive, viewModel.item(4).endSwipeAction)
        }

    @Test
    fun `swiping from start to end reads, from end to start archives`() =
        runTest {
            val row = rowOf(chat(id = 2, unreadCount = 4))

            assertEquals(MarkAsRead, row.startSwipeAction)
            assertEquals(Archive, row.endSwipeAction)
        }

    @Test
    fun `swiping a read chat marks it as unread`() =
        runTest {
            assertEquals(MarkAsUnread, rowOf(chat(id = 2)).startSwipeAction)
        }

    @Test
    fun `chosen swipes act as the row allows, and nothing does not swipe`() =
        runTest {
            val muted = rowOf(chat(id = 2, pinnedIn = listOf(ChatListType.Main), isMuted = true))

            assertEquals(Unpin, muted.rowActionFor(SwipeAction.Pin))
            assertEquals(Unmute, muted.rowActionFor(SwipeAction.Mute))
            assertEquals(Delete, muted.rowActionFor(SwipeAction.Delete))
            assertEquals(Pin, rowOf(chat(id = 3)).rowActionFor(SwipeAction.Pin))
            assertNull(muted.rowActionFor(SwipeAction.None))
        }

    @Test
    fun `Saved Messages has no mute swipe`() =
        runTest {
            assertNull(rowOf(chat(id = ME)).rowActionFor(SwipeAction.Mute))
        }

    // Running them

    @Test
    fun `each action calls the repository for its chat`() =
        runTest {
            chats.setChats(
                ChatListType.Main,
                chat(id = 2, unreadCount = 1, pinnedIn = listOf(ChatListType.Main), isMuted = true),
                chat(id = 3),
            )
            val viewModel = viewModel()

            send(
                viewModel,
                ChatListEvent.RowAction(2, MarkAsRead),
                ChatListEvent.RowAction(3, MarkAsUnread),
                ChatListEvent.RowAction(3, Pin),
                ChatListEvent.RowAction(2, Unpin),
                ChatListEvent.RowAction(3, Mute),
                ChatListEvent.RowAction(2, Unmute),
            )

            assertEquals(
                listOf(
                    ChatCall.MarkAsRead(2),
                    ChatCall.MarkAsUnread(3),
                    ChatCall.SetPinned(3, ChatListType.Main, isPinned = true),
                    ChatCall.SetPinned(2, ChatListType.Main, isPinned = false),
                    ChatCall.SetMuted(3, isMuted = true),
                    ChatCall.SetMuted(2, isMuted = false),
                ),
                chats.calls,
            )
        }

    @Test
    fun `pins in the folder shown`() =
        runTest {
            chats.folderList.value = persistentListOf(folder(1))
            chats.setChats(ChatListType.Folder(1), chat(id = 3, lists = listOf(ChatListType.Folder(1))))
            val viewModel = viewModel()

            send(viewModel, ChatListEvent.SelectFolder(1), ChatListEvent.RowAction(3, Pin))

            assertEquals(listOf(ChatCall.SetPinned(3, ChatListType.Folder(1), isPinned = true)), chats.calls)
        }

    @Test
    fun `pins in the archive`() =
        runTest {
            chats.setChats(ChatListType.Archive, chat(id = 5, lists = listOf(ChatListType.Archive)))
            val viewModel = viewModel(kind = ChatListKind.Archive)

            send(viewModel, ChatListEvent.RowAction(5, Pin), ChatListEvent.RowAction(5, Unarchive))

            assertEquals(
                listOf(ChatCall.SetPinned(5, ChatListType.Archive, isPinned = true), ChatCall.SetArchived(5, false)),
                chats.calls,
            )
        }

    @Test
    fun `an action that does not fit the chat does nothing`() =
        runTest {
            chats.setChats(ChatListType.Main, chat(id = 2))
            val viewModel = viewModel()

            send(viewModel, ChatListEvent.RowAction(2, Unpin), ChatListEvent.RowAction(9, Pin))

            assertEquals(emptyList<ChatCall>(), chats.calls)
        }

    // Archive and undo

    @Test
    fun `archiving a chat offers to undo it`() =
        runTest {
            chats.setChats(ChatListType.Main, chat(id = 2))
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, ChatListEvent.RowAction(2, Archive))

            assertEquals(listOf(ChatCall.SetArchived(2, isArchived = true)), chats.calls)
            assertEquals(listOf(ChatListEffect.ChatsArchived(persistentListOf(2L))), effects)
        }

    @Test
    fun `undo moves the archived chats back`() =
        runTest {
            val viewModel = viewModel()

            send(viewModel, ChatListEvent.UndoArchive(persistentListOf(2L, 3L)))

            assertEquals(
                listOf(ChatCall.SetArchived(2, isArchived = false), ChatCall.SetArchived(3, isArchived = false)),
                chats.calls,
            )
        }

    @Test
    fun `unarchiving offers no undo`() =
        runTest {
            chats.setChats(ChatListType.Archive, chat(id = 5, lists = listOf(ChatListType.Archive)))
            val viewModel = viewModel(kind = ChatListKind.Archive)
            val effects = effectsOf(viewModel)

            send(viewModel, ChatListEvent.RowAction(5, Unarchive))

            assertEquals(emptyList<ChatListEffect>(), effects)
        }

    // Errors

    @Test
    fun `a failed action shows its error`() =
        runTest {
            chats.setChats(ChatListType.Main, chat(id = 2))
            chats.onCall = { Outcome.Failure(DomainError.Network) }
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, ChatListEvent.RowAction(2, Mute))

            assertEquals(listOf(ChatListEffect.ShowError(DomainError.Network)), effects)
        }

    @Test
    fun `pinning past the limit says to unpin a chat first`() =
        runTest {
            chats.setChats(ChatListType.Main, chat(id = 2))
            chats.onCall = { Outcome.Failure(DomainError.TooManyPinnedChats) }
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, ChatListEvent.RowAction(2, Pin))

            assertEquals(listOf(ChatListEffect.ShowError(DomainError.TooManyPinnedChats)), effects)
        }

    @Test
    fun `a failed archive shows the error and offers no undo`() =
        runTest {
            chats.setChats(ChatListType.Main, chat(id = 2))
            chats.onCall = { Outcome.Failure(DomainError.FloodWait(30)) }
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, ChatListEvent.RowAction(2, Archive))

            assertEquals(listOf(ChatListEffect.ShowError(DomainError.FloodWait(30))), effects)
        }

    // The selection toolbar

    @Test
    fun `the toolbar offers pin, mute, read and archive while some selected chats are not yet so`() =
        runTest {
            val viewModel = selecting(chat(id = 2, pinnedIn = listOf(ChatListType.Main), unreadCount = 1), chat(id = 3))

            assertEquals(listOf(Pin, Mute, MarkAsRead, Archive, Delete), viewModel.state.selection?.actions)
        }

    @Test
    fun `the toolbar turns to unpin, unmute and mark as unread when they fit every selected chat`() =
        runTest {
            val pinnedMuted = { id: Long -> chat(id = id, pinnedIn = listOf(ChatListType.Main), isMuted = true) }
            val viewModel = selecting(pinnedMuted(2), pinnedMuted(3))

            assertEquals(listOf(Unpin, Unmute, MarkAsUnread, Archive, Delete), viewModel.state.selection?.actions)
        }

    @Test
    fun `the toolbar unarchives in the archive`() =
        runTest {
            chats.setChats(ChatListType.Archive, chat(id = 5, lists = listOf(ChatListType.Archive)))
            val viewModel = viewModel(kind = ChatListKind.Archive)

            send(viewModel, ChatListEvent.ChatLongClicked(5))

            assertEquals(listOf(Pin, Mute, MarkAsUnread, Unarchive, Delete), viewModel.state.selection?.actions)
        }

    @Test
    fun `the toolbar has no mute for Saved Messages alone`() =
        runTest {
            val viewModel = selecting(chat(id = ME))

            assertEquals(listOf(Pin, MarkAsUnread, Archive, Delete), viewModel.state.selection?.actions)
        }

    @Test
    fun `pinning the selection pins the chats not yet pinned, bottom up to keep their order, and ends selection`() =
        runTest {
            val viewModel = selecting(chat(id = 2, pinnedIn = listOf(ChatListType.Main)), chat(id = 3), chat(id = 4))

            send(viewModel, ChatListEvent.SelectionAction(Pin))

            assertEquals(
                listOf(
                    ChatCall.SetPinned(4, ChatListType.Main, isPinned = true),
                    ChatCall.SetPinned(3, ChatListType.Main, isPinned = true),
                ),
                chats.calls,
            )
            assertNull(viewModel.state.selection)
        }

    @Test
    fun `unmuting the selection unmutes every selected chat`() =
        runTest {
            val viewModel = selecting(chat(id = 2, isMuted = true), chat(id = 3, isMuted = true))

            send(viewModel, ChatListEvent.SelectionAction(Unmute))

            assertEquals(listOf(ChatCall.SetMuted(2, false), ChatCall.SetMuted(3, false)), chats.calls)
        }

    @Test
    fun `reading the selection reads the unread chats`() =
        runTest {
            val viewModel =
                selecting(chat(id = 2, unreadCount = 3), chat(id = 3), chat(id = 4, isMarkedAsUnread = true))

            send(viewModel, ChatListEvent.SelectionAction(MarkAsRead))

            assertEquals(listOf(ChatCall.MarkAsRead(2), ChatCall.MarkAsRead(4)), chats.calls)
        }

    @Test
    fun `archiving the selection offers to undo all of it`() =
        runTest {
            val viewModel = selecting(chat(id = 2), chat(id = 3))
            val effects = effectsOf(viewModel)

            send(viewModel, ChatListEvent.SelectionAction(Archive))

            assertEquals(listOf(ChatCall.SetArchived(2, true), ChatCall.SetArchived(3, true)), chats.calls)
            assertEquals(listOf(ChatListEffect.ChatsArchived(persistentListOf(2L, 3L))), effects)
        }

    @Test
    fun `a selection action stops at the first failure and shows it`() =
        runTest {
            val viewModel = selecting(chat(id = 2), chat(id = 3), chat(id = 4))
            chats.onCall =
                { call -> if (call.chatId == 3L) Outcome.Failure(DomainError.Network) else Outcome.Success(Unit) }
            val effects = effectsOf(viewModel)

            send(viewModel, ChatListEvent.SelectionAction(Archive))

            assertEquals(listOf(2L, 3L), chats.calls.map { it.chatId })
            assertEquals(
                listOf(
                    ChatListEffect.ChatsArchived(persistentListOf(2L)),
                    ChatListEffect.ShowError(DomainError.Network),
                ),
                effects,
            )
        }

    // Delete

    @Test
    fun `deleting a private chat asks first, offering to delete it for the other user too`() =
        runTest {
            chats.setChats(ChatListType.Main, chat(id = 2, title = "Ada"))
            chats.onDeleteOptions = { Outcome.Success(DeleteChatOptions(DeleteChatAction.Delete, true)) }
            val viewModel = viewModel()

            send(viewModel, ChatListEvent.RowAction(2, Delete))

            assertEquals(
                DeleteDialog.Single(2, UiText.Plain("Ada"), DeleteKind.DeleteChat, DeleteForEveryone.OtherUser),
                viewModel.state.deleteDialog,
            )
            assertEquals(emptyList<ChatCall>(), chats.calls)
        }

    @Test
    fun `Saved Messages is deleted under its own name`() =
        runTest {
            chats.setChats(ChatListType.Main, chat(id = ME))
            val viewModel = viewModel()

            send(viewModel, ChatListEvent.RowAction(ME, Delete))

            assertEquals(
                DeleteDialog.Single(ME, res(UiR.string.chat_saved_messages), DeleteKind.DeleteChat, forEveryone = null),
                viewModel.state.deleteDialog,
            )
        }

    @Test
    fun `leaving a group`() =
        runTest {
            assertEquals(
                DeleteDialog.Single(GROUP, UiText.Plain("Chat $GROUP"), DeleteKind.LeaveGroup, forEveryone = null),
                deleteDialogFor(BASIC_GROUP, DeleteChatOptions(DeleteChatAction.Leave, canDeleteForEveryone = false)),
            )
        }

    @Test
    fun `the owner of a group may delete it for all members`() =
        runTest {
            val supergroup = ChatType.Supergroup(supergroupId = 7, isChannel = false)

            assertEquals(
                DeleteForEveryone.AllMembers,
                deleteDialogFor(supergroup, DeleteChatOptions(DeleteChatAction.Leave, true))?.forEveryone,
            )
        }

    @Test
    fun `the owner of a channel may delete it for all subscribers`() =
        runTest {
            val dialog =
                deleteDialogFor(
                    ChatType.Supergroup(supergroupId = 7, isChannel = true),
                    DeleteChatOptions(DeleteChatAction.Leave, canDeleteForEveryone = true),
                )

            assertEquals(DeleteKind.LeaveChannel, dialog?.kind)
            assertEquals(DeleteForEveryone.AllSubscribers, dialog?.forEveryone)
        }

    @Test
    fun `confirming with the check box deletes the chat for everyone`() =
        runTest {
            chats.setChats(ChatListType.Main, chat(id = 2))
            chats.onDeleteOptions = { Outcome.Success(DeleteChatOptions(DeleteChatAction.Delete, true)) }
            val viewModel = viewModel()

            send(viewModel, ChatListEvent.RowAction(2, Delete), ChatListEvent.ConfirmDelete(forEveryone = true))

            assertEquals(listOf(ChatCall.DeleteChat(2, forEveryone = true)), chats.calls)
            assertNull(viewModel.state.deleteDialog)
        }

    @Test
    fun `confirming without the check box deletes the chat for the current user`() =
        runTest {
            chats.setChats(ChatListType.Main, chat(id = 2))
            chats.onDeleteOptions = { Outcome.Success(DeleteChatOptions(DeleteChatAction.Delete, true)) }
            val viewModel = viewModel()

            send(viewModel, ChatListEvent.RowAction(2, Delete), ChatListEvent.ConfirmDelete(forEveryone = false))

            assertEquals(listOf(ChatCall.DeleteChat(2, forEveryone = false)), chats.calls)
        }

    @Test
    fun `for everyone is not asked of TDLib where it is not allowed`() =
        runTest {
            chats.setChats(ChatListType.Main, chat(id = 2))
            val viewModel = viewModel()

            send(viewModel, ChatListEvent.RowAction(2, Delete), ChatListEvent.ConfirmDelete(forEveryone = true))

            assertEquals(listOf(ChatCall.DeleteChat(2, forEveryone = false)), chats.calls)
        }

    @Test
    fun `cancelling deletes nothing`() =
        runTest {
            chats.setChats(ChatListType.Main, chat(id = 2))
            val viewModel = viewModel()

            send(viewModel, ChatListEvent.RowAction(2, Delete), ChatListEvent.DismissDelete)

            assertNull(viewModel.state.deleteDialog)
            assertEquals(emptyList<ChatCall>(), chats.calls)
        }

    @Test
    fun `asking twice while the options load asks TDLib once`() =
        runTest {
            chats.setChats(ChatListType.Main, chat(id = 2))
            val options = CompletableDeferred<Outcome<DeleteChatOptions>>()
            var requests = 0
            chats.onDeleteOptions = {
                requests++
                options.await()
            }
            val viewModel = viewModel()

            send(viewModel, ChatListEvent.RowAction(2, Delete), ChatListEvent.RowAction(2, Delete))

            assertEquals(1, requests)
        }

    @Test
    fun `options that cannot be read show the error and no dialog`() =
        runTest {
            chats.setChats(ChatListType.Main, chat(id = 2))
            chats.onDeleteOptions = { Outcome.Failure(DomainError.Network) }
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, ChatListEvent.RowAction(2, Delete))

            assertNull(viewModel.state.deleteDialog)
            assertEquals(listOf(ChatListEffect.ShowError(DomainError.Network)), effects)
        }

    @Test
    fun `a failed delete shows the error`() =
        runTest {
            chats.setChats(ChatListType.Main, chat(id = 2))
            chats.onCall = { Outcome.Failure(DomainError.Unauthorized) }
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, ChatListEvent.RowAction(2, Delete), ChatListEvent.ConfirmDelete(forEveryone = false))

            assertEquals(listOf(ChatListEffect.ShowError(DomainError.Unauthorized)), effects)
        }

    @Test
    fun `deleting the selection asks once, then deletes each chat for the current user and ends selection`() =
        runTest {
            val viewModel = selecting(chat(id = 2), chat(id = 3))

            send(viewModel, ChatListEvent.SelectionAction(Delete))

            assertEquals(DeleteDialog.Bulk(persistentListOf(2L, 3L)), viewModel.state.deleteDialog)
            assertEquals(setOf(2L, 3L), viewModel.state.selection?.chatIds)

            send(viewModel, ChatListEvent.ConfirmDelete(forEveryone = false))

            assertEquals(listOf(ChatCall.DeleteChat(2, false), ChatCall.DeleteChat(3, false)), chats.calls)
            assertNull(viewModel.state.selection)
        }

    @Test
    fun `cancelling the deletion of the selection keeps the selection`() =
        runTest {
            val viewModel = selecting(chat(id = 2), chat(id = 3))

            send(viewModel, ChatListEvent.SelectionAction(Delete), ChatListEvent.DismissDelete)

            assertEquals(setOf(2L, 3L), viewModel.state.selection?.chatIds)
        }

    @Test
    fun `deleting one selected chat asks as for that chat`() =
        runTest {
            val viewModel = selecting(chat(id = 2))

            send(viewModel, ChatListEvent.SelectionAction(Delete))

            assertEquals(2L, (viewModel.state.deleteDialog as? DeleteDialog.Single)?.chatId)
        }

    /** A view model on the main list of [chatList], all of them selected. */
    private fun TestScope.selecting(vararg chatList: Chat): ChatListViewModel {
        chats.setChats(ChatListType.Main, *chatList)
        val viewModel = viewModel()
        chatList.forEach { send(viewModel, ChatListEvent.ChatLongClicked(it.id)) }
        return viewModel
    }

    /** The dialog for deleting a chat of [type] that TDLib allows [options] for. */
    private fun TestScope.deleteDialogFor(
        type: ChatType,
        options: DeleteChatOptions,
    ): DeleteDialog.Single? {
        chats.setChats(ChatListType.Main, chat(id = GROUP, type = type))
        chats.onDeleteOptions = { Outcome.Success(options) }
        val viewModel = viewModel()
        send(viewModel, ChatListEvent.RowAction(GROUP, Delete))
        return viewModel.state.deleteDialog as? DeleteDialog.Single
    }
}
