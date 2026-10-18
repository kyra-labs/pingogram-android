package dev.kyralabs.pingogram.feature.chats.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.feature.chats.R
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * What the user can do to a chat from the chat list (`REQUIREMENTS.md` FR-L4): by swiping its row, through the screen
 * reader's actions on it, or to the selected chats from the selection toolbar (FR-L8). Each has its Material Symbol
 * and its label, which is also its accessibility name.
 */
enum class ChatRowAction(
    @param:DrawableRes val icon: Int,
    @param:StringRes val label: Int,
) {
    MarkAsRead(UiR.drawable.ic_mark_chat_read, R.string.chat_list_action_mark_read),
    MarkAsUnread(UiR.drawable.ic_mark_chat_unread, R.string.chat_list_action_mark_unread),
    Archive(UiR.drawable.ic_archive, R.string.chat_list_action_archive),
    Unarchive(UiR.drawable.ic_unarchive, R.string.chat_list_action_unarchive),
    Pin(UiR.drawable.ic_push_pin, R.string.chat_list_action_pin),
    Unpin(UiR.drawable.ic_keep_off, R.string.chat_list_action_unpin),
    Mute(UiR.drawable.ic_notifications_off, R.string.chat_list_action_mute),
    Unmute(UiR.drawable.ic_notifications, R.string.chat_list_action_unmute),

    /** Asks first ([ChatListUiState.deleteDialog]). */
    Delete(UiR.drawable.ic_delete, R.string.chat_list_action_delete),
}

/**
 * The actions on one chat, in the order screen readers list them: mark as read (with anything unread) or as unread,
 * archive or unarchive ([isArchived]), pin or unpin in the list shown, mute or unmute (neither without [canMute]: Saved
 * Messages cannot be muted), delete.
 */
internal fun rowActions(
    isUnread: Boolean,
    isArchived: Boolean,
    isPinned: Boolean,
    isMuted: Boolean,
    canMute: Boolean,
): ImmutableList<ChatRowAction> =
    listOfNotNull(
        if (isUnread) ChatRowAction.MarkAsRead else ChatRowAction.MarkAsUnread,
        if (isArchived) ChatRowAction.Unarchive else ChatRowAction.Archive,
        if (isPinned) ChatRowAction.Unpin else ChatRowAction.Pin,
        when {
            !canMute -> null
            isMuted -> ChatRowAction.Unmute
            else -> ChatRowAction.Mute
        },
        ChatRowAction.Delete,
    ).toImmutableList()

/** Swiping a row from start to end: mark it as read, or as unread when it has nothing unread. */
internal val ChatListItemModel.startSwipeAction: ChatRowAction
    get() = if (ChatRowAction.MarkAsRead in actions) ChatRowAction.MarkAsRead else ChatRowAction.MarkAsUnread

/** Swiping a row from end to start: archive it, or unarchive an archived chat. */
internal val ChatListItemModel.endSwipeAction: ChatRowAction
    get() = if (ChatRowAction.Unarchive in actions) ChatRowAction.Unarchive else ChatRowAction.Archive

/**
 * The selection toolbar's actions for the selected [rows], in its order: pin or unpin, mute or unmute, mark as read or
 * unread, archive or unarchive, delete. Each pair turns to its second action only when it applies to every selected
 * chat: unpin when all are pinned, unmute when all are muted, mark as unread when none has anything unread, unarchive
 * when all are archived. Mute is missing when no selected chat can be muted.
 */
internal fun selectionActions(rows: List<ChatListItemModel>): ImmutableList<ChatRowAction> =
    (SelectionToggles.mapNotNull { it.actionFor(rows) } + ChatRowAction.Delete).toImmutableList()

/** The chats among [rows] that [action] applies to: e.g. pinning leaves the chats that are pinned already alone. */
internal fun targetsOf(
    action: ChatRowAction,
    rows: List<ChatListItemModel>,
): List<Long> = rows.filter { action in it.actions }.map { it.id }

/** The two actions that undo each other; [off] applies when every chat that has either has [off]. */
private class Toggle(
    val on: ChatRowAction,
    val off: ChatRowAction,
) {
    fun actionFor(rows: List<ChatListItemModel>): ChatRowAction? {
        val candidates = rows.filter { on in it.actions || off in it.actions }
        return when {
            candidates.isEmpty() -> null
            candidates.all { off in it.actions } -> off
            else -> on
        }
    }
}

private val SelectionToggles =
    persistentListOf(
        Toggle(ChatRowAction.Pin, ChatRowAction.Unpin),
        Toggle(ChatRowAction.Mute, ChatRowAction.Unmute),
        Toggle(ChatRowAction.MarkAsRead, ChatRowAction.MarkAsUnread),
        Toggle(ChatRowAction.Archive, ChatRowAction.Unarchive),
    )

/** How an action on several chats went: the chats it was applied to, in that order, and the failure that stopped it. */
internal class ActionResult(
    val done: List<Long>,
    val error: DomainError?,
)

/**
 * Applies [action] to [chatIds] one after another, stopping at the first failure; pins and unpins in [list]. Deleting
 * deletes each chat for the current user only: several chats share one confirmation, which offers no per-chat choice.
 */
internal suspend fun ChatRepository.perform(
    action: ChatRowAction,
    chatIds: List<Long>,
    list: ChatListType,
): ActionResult {
    // A newly pinned chat goes to the top, so pinning from the bottom up keeps the chats' order.
    val ordered = if (action == ChatRowAction.Pin) chatIds.asReversed() else chatIds
    val done = ArrayList<Long>(ordered.size)
    for (chatId in ordered) {
        when (val outcome = performOne(action, chatId, list)) {
            is Outcome.Success -> done += chatId
            is Outcome.Failure -> return ActionResult(done, outcome.error)
        }
    }
    return ActionResult(done, error = null)
}

private suspend fun ChatRepository.performOne(
    action: ChatRowAction,
    chatId: Long,
    list: ChatListType,
): Outcome<Unit> =
    when (action) {
        ChatRowAction.MarkAsRead -> markAsRead(chatId)
        ChatRowAction.MarkAsUnread -> markAsUnread(chatId)
        ChatRowAction.Archive -> setArchived(chatId, isArchived = true)
        ChatRowAction.Unarchive -> setArchived(chatId, isArchived = false)
        ChatRowAction.Pin -> setPinned(chatId, list, isPinned = true)
        ChatRowAction.Unpin -> setPinned(chatId, list, isPinned = false)
        ChatRowAction.Mute -> setMuted(chatId, isMuted = true)
        ChatRowAction.Unmute -> setMuted(chatId, isMuted = false)
        ChatRowAction.Delete -> deleteChat(chatId, forEveryone = false)
    }
