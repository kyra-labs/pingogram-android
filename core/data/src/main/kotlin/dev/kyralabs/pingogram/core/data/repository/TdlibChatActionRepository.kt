package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.data.mapper.toChatAction
import dev.kyralabs.pingogram.core.data.mapper.toMessageSender
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.SenderAction
import dev.kyralabs.pingogram.core.domain.repository.ChatActionRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.drinkless.tdlib.TdApi
import javax.inject.Inject

/**
 * [ChatActionRepository] on TDLib's `updateChatAction`: the last action of each sender in each chat, until TDLib
 * cancels it (`chatActionCancel`). A sender's new action replaces the old one in place, so the order of the senders
 * stays as they started.
 *
 * - No timers: TDLib cancels every action it reported, when the sender's message arrives or 5.5 seconds after the
 *   sender last repeated it (`DIALOG_ACTION_TIMEOUT`, td/telegram/DialogActionManager.h).
 * - Only updates without `topic_id` are applied: TDLib reports an action in a message thread or forum topic, and its
 *   cancellation, both for the topic and for the chat itself (`DialogActionManager::on_dialog_action`), so the chat's
 *   own updates hold every sender's action in the chat. Actions per topic are left for forums, later.
 */
@AccountScoped
class TdlibChatActionRepository
    @Inject
    constructor(
        client: TdlibClient,
        @ApplicationScope scope: CoroutineScope,
    ) : ChatActionRepository,
        TdlibUpdateCache {
        private val store = UpdateStore(client, scope, persistentMapOf(), ChatActions::reduce)

        // A StateFlow, so equal snapshots are skipped; ignored updates keep the same map.
        override val actionsByChat: Flow<ImmutableMap<Long, ImmutableList<SenderAction>>> = store.state

        override fun actions(chatId: Long): Flow<ImmutableList<SenderAction>> =
            store.state.map { it[chatId] ?: persistentListOf() }.distinctUntilChanged()
    }

/** By chat id; a chat without actions has no entry. */
private typealias ChatActions = PersistentMap<Long, PersistentList<SenderAction>>

private fun ChatActions.reduce(update: TdApi.Update): ChatActions {
    if (update !is TdApi.UpdateChatAction || update.topicId != null) return this
    val sender = update.senderId.toMessageSender()
    // Null for a cancellation, and for an action added by a newer TDLib, which ends the sender's previous one too.
    val action = update.action.toChatAction()
    return when {
        // A sender type added by a newer TDLib.
        sender == null -> this

        action == null -> withoutAction(update.chatId, sender)

        else -> withAction(update.chatId, SenderAction(sender, action))
    }
}

private fun ChatActions.withAction(
    chatId: Long,
    action: SenderAction,
): ChatActions {
    val actions = get(chatId) ?: persistentListOf()
    val index = actions.indexOfFirst { it.sender == action.sender }
    return when {
        index < 0 -> putting(chatId, actions.adding(action))
        actions[index] == action -> this
        else -> putting(chatId, actions.replacingAt(index, action))
    }
}

private fun ChatActions.withoutAction(
    chatId: Long,
    sender: MessageSender,
): ChatActions {
    val actions = get(chatId) ?: return this
    val remaining = actions.removingAll { it.sender == sender }
    return when {
        remaining.size == actions.size -> this
        remaining.isEmpty() -> removing(chatId)
        else -> putting(chatId, remaining)
    }
}
