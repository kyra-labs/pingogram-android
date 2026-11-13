package dev.kyralabs.pingogram.feature.chat.ui.directmessages

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Supergroup
import dev.kyralabs.pingogram.core.domain.repository.ChatJoinRepository
import dev.kyralabs.pingogram.core.domain.repository.GroupRepository
import dev.kyralabs.pingogram.feature.chat.ui.ChatUiState
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * The composer's "Message channel" of a channel whose subscribers can write to its administrators (td_api.tl
 * `supergroup.has_direct_messages_group`), from the conversation's [supergroup] (null for other chats): the channel's
 * direct messages chat, looked up once through [repository] (it only reads, and joins nothing), with what each message
 * there costs, followed through [groups] as the price changes. Null while it is not known, for other chats, and where
 * the channel has no such chat.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun messageChannelButton(
    supergroup: Flow<Supergroup?>,
    repository: ChatJoinRepository,
    groups: GroupRepository,
): Flow<ComposerUiState.MessageChannel?> =
    supergroup
        .map { channel -> channel?.takeIf { it.isChannel && it.hasDirectMessages }?.id }
        .distinctUntilChanged()
        .flatMapLatest { channelId ->
            if (channelId == null) {
                flowOf(null)
            } else {
                flow<ComposerUiState.MessageChannel?> {
                    when (val chat = repository.directMessagesChat(channelId)) {
                        is Outcome.Failure -> {
                            emit(null)
                        }

                        is Outcome.Success -> {
                            val (chatId, supergroupId) = chat.value
                            emitAll(
                                groups.supergroup(supergroupId).map {
                                    ComposerUiState.MessageChannel(chatId, it?.paidMessageStarCount ?: 0)
                                },
                            )
                        }
                    }
                }
            }
        }.onStart { emit(null) }
        .distinctUntilChanged()

/**
 * The state with "Message channel" ([button]) in the composer's place where a channel the user only reads shows
 * nothing there; as it is otherwise.
 */
internal fun ChatUiState.withMessageChannel(button: ComposerUiState.MessageChannel?): ChatUiState =
    if (button != null && composer == ComposerUiState.Hidden) copy(composer = button) else this
