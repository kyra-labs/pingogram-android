package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.data.mapper.toChatAction
import dev.kyralabs.pingogram.core.data.mapper.toMessageSender
import dev.kyralabs.pingogram.core.data.mapper.toMessageTopic
import dev.kyralabs.pingogram.core.domain.model.ChatAction
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.MessageTopic
import dev.kyralabs.pingogram.core.domain.model.SenderAction
import dev.kyralabs.pingogram.core.domain.repository.ChatActionRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import kotlin.reflect.KClass

/**
 * [ChatActionRepository] on TDLib's `updateChatAction`: the last action of each sender in each chat, and in each forum
 * topic or message thread, until TDLib cancels it (`chatActionCancel`). A sender's new action replaces the old one in
 * place, so the order of the senders stays as they started.
 *
 * - No timers: TDLib cancels every action it reported, when the sender's message arrives or 5.5 seconds after the
 *   sender last repeated it (`DIALOG_ACTION_TIMEOUT`, td/telegram/DialogActionManager.h).
 * - TDLib reports an action in a forum topic or message thread, and its cancellation, both with its `topic_id` and
 *   without, for the chat itself (`DialogActionManager::on_dialog_action`), so the chat's own updates hold every
 *   sender's action in the chat, and the topic's those in the topic alone. When a sender moves to another topic, TDLib
 *   cancels the old topic's action only, then reports the new one for the topic and the chat.
 * - An action typed in a forum without a topic (Telegram's apps type so in General) comes without `topic_id` only.
 */
@AccountScoped
class TdlibChatActionRepository
    @Inject
    constructor(
        client: TdlibClient,
        @ApplicationScope scope: CoroutineScope,
    ) : ChatActionRepository,
        TdlibUpdateCache {
        private val store = UpdateStore(client, scope, ACTION_UPDATES, Actions(), Actions::reduce)

        // Compared by identity: an update that changes no chat keeps the same map.
        override val actionsByChat: Flow<ImmutableMap<Long, ImmutableList<SenderAction>>> =
            store.state.map { it.byChat }.distinctUntilChanged { old, new -> old === new }

        override fun actions(chatId: Long): Flow<ImmutableList<SenderAction>> =
            store.state.map { it.byChat[chatId] ?: persistentListOf() }.distinctUntilChanged()

        override fun actions(
            chatId: Long,
            topic: MessageTopic,
        ): Flow<ImmutableList<SenderAction>> = store.state.map { it.inTopic(chatId, topic) }.distinctUntilChanged()
    }

/** Senders' actions in order; a chat or topic without actions has no entry. */
private typealias SenderActions = PersistentList<SenderAction>

/**
 * The actions of each chat by chat id ([byChat], every topic's included), and of each topic by chat id and topic
 * ([byTopic]).
 */
private data class Actions(
    val byChat: PersistentMap<Long, SenderActions> = persistentMapOf(),
    val byTopic: PersistentMap<Long, PersistentMap<MessageTopic, SenderActions>> = persistentMapOf(),
) {
    fun inTopic(
        chatId: Long,
        topic: MessageTopic,
    ): ImmutableList<SenderAction> =
        when {
            topic is MessageTopic.Forum && topic.isGeneral -> inGeneral(chatId)
            topic is MessageTopic.Forum || topic is MessageTopic.Thread -> byTopic[chatId]?.get(topic)
            else -> byChat[chatId]
        } ?: persistentListOf()

    /** General's own actions, and the chat's of the senders acting in no topic, in the chat's order. */
    private fun inGeneral(chatId: Long): ImmutableList<SenderAction>? {
        val topics = byTopic[chatId]
        val chat = byChat[chatId]
        return if (topics == null || chat == null) {
            chat
        } else {
            val general = topics[GENERAL].orEmpty().mapTo(HashSet()) { it.sender }
            val elsewhere =
                topics.entries
                    .filter { (topic, _) -> topic != GENERAL }
                    .flatMapTo(HashSet()) { (_, actions) -> actions.map { it.sender } }
            chat.filter { it.sender in general || it.sender !in elsewhere }.toImmutableList()
        }
    }

    fun reduce(update: TdApi.Update): Actions {
        val sender = (update as? TdApi.UpdateChatAction)?.senderId?.toMessageSender()
        // A sender type or a kind of topic added by a newer TDLib is left out.
        val topic = (update as? TdApi.UpdateChatAction)?.topicId?.let { it.toMessageTopic() ?: return this }
        return when {
            update !is TdApi.UpdateChatAction || sender == null -> this

            // Null for a cancellation, and for an action added by a newer TDLib, which ends the sender's previous too.
            topic == null -> withChatAction(update.chatId, sender, update.action.toChatAction())

            else -> withTopicAction(update.chatId, topic, sender, update.action.toChatAction())
        }
    }

    private fun withChatAction(
        chatId: Long,
        sender: MessageSender,
        action: ChatAction?,
    ): Actions {
        val actions = byChat[chatId]
        val changed = actions.with(sender, action)
        return if (changed === actions) this else copy(byChat = byChat.withEntry(chatId, changed))
    }

    private fun withTopicAction(
        chatId: Long,
        topic: MessageTopic,
        sender: MessageSender,
        action: ChatAction?,
    ): Actions {
        val topics = byTopic[chatId] ?: persistentMapOf()
        val actions = topics[topic]
        val changed = actions.with(sender, action)
        return if (changed === actions) {
            this
        } else {
            val changedTopics = topics.withEntry(topic, changed)
            copy(
                byTopic =
                    if (changedTopics.isEmpty()) {
                        byTopic.removing(
                            chatId,
                        )
                    } else {
                        byTopic.putting(chatId, changedTopics)
                    },
            )
        }
    }

    private companion object {
        val GENERAL = MessageTopic.Forum(MessageTopic.GENERAL_TOPIC_ID)
    }
}

/** These actions with [sender]'s set to [action], or without it for null; the same instance when nothing changed. */
private fun SenderActions?.with(
    sender: MessageSender,
    action: ChatAction?,
): SenderActions? {
    val actions = this ?: persistentListOf()
    val index = actions.indexOfFirst { it.sender == sender }
    val changed =
        when {
            index < 0 -> if (action == null) actions else actions.adding(SenderAction(sender, action))
            action == null -> actions.removingAt(index)
            actions[index].action == action -> actions
            else -> actions.replacingAt(index, SenderAction(sender, action))
        }
    return if (changed === actions) this else changed
}

/** This map with [key] set to [actions], or without it when they are null or empty. */
private fun <K> PersistentMap<K, SenderActions>.withEntry(
    key: K,
    actions: SenderActions?,
): PersistentMap<K, SenderActions> = if (actions.isNullOrEmpty()) removing(key) else putting(key, actions)

/** The updates its store's reducer handles. */
private val ACTION_UPDATES: Set<KClass<out TdApi.Update>> = setOf(TdApi.UpdateChatAction::class)
