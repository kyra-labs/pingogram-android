package dev.kyralabs.pingogram.feature.chat.ui.reply

import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.ReplyTarget
import dev.kyralabs.pingogram.core.domain.model.WriteAccess
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.ui.ChatEffect
import dev.kyralabs.pingogram.feature.chat.ui.ReplyEvent
import dev.kyralabs.pingogram.feature.chat.ui.composer.ChatComposer
import dev.kyralabs.pingogram.feature.chat.ui.forward.ChatTargets
import dev.kyralabs.pingogram.feature.chat.ui.share.ShareInbox
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * "Reply in another chat" for `ChatViewModel` (`REQUIREMENTS.md` FR-C6, td_api.tl
 * `inputMessageReplyToExternalMessage`), both ways:
 *
 * - **From this chat** ([open], once the message actions checked that TDLib allows it): the forward sheet's chat list
 *   ([targets], the chats where the user can write, no secret chats, which cannot reply to another chat's message)
 *   in its [sheet]. One tap picks a chat: this one replies here as "Reply" does; another one gets the reply through
 *   [inbox] ([ShareInbox.deliverReply]) and opens ([ChatEffect.OpenChat]). Nothing is sent: the user writes the reply
 *   in that chat.
 * - **Into this chat** ([start]): a reply delivered to chat [chatId] goes into the composer once the conversation is
 *   on screen ([onScreen]), which then replies to the other chat's message and takes the focus.
 *
 * [previewOf] gives one line of a message of this chat for the sheet. Runs in [scope], the view model's; the sheet's
 * list is mapped on [dispatcher].
 */
@Suppress("LongParameterList") // The chat, its picker's chats, the inbox, the screen, the composer, where it runs.
@OptIn(ExperimentalCoroutinesApi::class)
internal class ChatReplyElsewhere(
    private val chatId: Long,
    private val targets: ChatTargets,
    private val inbox: ShareInbox,
    private val onScreen: StateFlow<Boolean>,
    private val composer: ChatComposer,
    private val previewOf: (messageId: Long) -> UiText?,
    private val scope: CoroutineScope,
    dispatcher: CoroutineDispatcher,
    private val onEffect: (ChatEffect) -> Unit,
) {
    /** The message the sheet picks a chat for, with its line; null while the sheet is closed. */
    private val request = MutableStateFlow<Request?>(null)

    /** The chat picker; null while it is closed. */
    val sheet: StateFlow<ReplyChatsSheet?> =
        request
            .flatMapLatest { request ->
                if (request == null) {
                    flowOf(null)
                } else {
                    targets
                        .targets { chat, access, _ -> access == WriteAccess.Allowed && chat.type !is ChatType.Secret }
                        .map { found ->
                            ReplyChatsSheet(
                                messageId = request.messageId,
                                preview = request.preview,
                                targets = found.targets,
                                isSearching = found.isSearching,
                                isLoading = found.isLoading,
                                searchError = found.error,
                            )
                        }
                }
            }.flowOn(dispatcher)
            .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /** Starts taking the replies other conversations hand to this chat, once. */
    fun start() {
        scope.launch {
            combine(inbox.repliesTo(chatId), onScreen) { reply, shown -> reply?.takeIf { shown } }
                .filterNotNull()
                .collect { reply ->
                    if (inbox.claimReply(chatId, reply)) {
                        composer.replyElsewhere(reply)
                        onEffect(ChatEffect.FocusComposer)
                    }
                }
        }
    }

    /** Opens the chat picker for message [messageId] of this chat. */
    fun open(messageId: Long) {
        targets.setQuery("")
        request.value = Request(messageId, previewOf(messageId))
    }

    fun onEvent(event: ReplyEvent) {
        when (event) {
            is ReplyEvent.SearchChats -> targets.setQuery(event.query)
            ReplyEvent.LoadMoreChats -> targets.loadMore()
            is ReplyEvent.PickChat -> pick(event.chatId)
            ReplyEvent.DismissChats -> request.value = null
            else -> Unit
        }
    }

    private fun pick(pickedChatId: Long) {
        val messageId = request.value?.messageId ?: return
        request.value = null
        if (pickedChatId == chatId) {
            composer.reply(messageId)
            onEffect(ChatEffect.FocusComposer)
        } else {
            inbox.deliverReply(pickedChatId, ReplyTarget(messageId, chatId = chatId))
            onEffect(ChatEffect.OpenChat(pickedChatId))
        }
    }

    private class Request(
        val messageId: Long,
        val preview: UiText?,
    )

    private companion object {
        /** How long the sheet's list keeps running without a collector, so a rotation does not start it again. */
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
