package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.SecretChat
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.GroupRepository
import dev.kyralabs.pingogram.feature.chat.ui.thread.ThreadState
import dev.kyralabs.pingogram.feature.chat.ui.thread.inThread
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest

/**
 * Chat [chatId] as the conversation of [thread] shows it ([inThread]), then every change of it; found again with each
 * of [attempts]. While TDLib has not announced the chat, it is pending and asked for: found, or why not.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun ChatRepository.lookup(
    chatId: Long,
    attempts: Flow<Int>,
    thread: Flow<ThreadState>,
): Flow<ChatLookup> =
    attempts
        .flatMapLatest {
            chat(chatId).transformLatest { chat ->
                if (chat != null) {
                    emit(ChatLookup.Found(chat))
                } else {
                    emit(ChatLookup.Pending)
                    // Found, TDLib announces it too, and chat() follows.
                    when (val found = getChat(chatId)) {
                        is Outcome.Success -> emit(ChatLookup.Found(found.value))
                        is Outcome.Failure -> emit(ChatLookup.Failed(found.error))
                    }
                }
            }
        }.combine(thread, ::inThread)
        .distinctUntilChanged()

/**
 * The basic group or supergroup of [chat] if it is a group or channel, its secret chat ([secretChat]) if it is a
 * secret chat; none for other chats, nor while unknown.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun GroupRepository.groupOf(
    chat: Flow<ChatLookup>,
    secretChat: Flow<SecretChat?>,
): Flow<ChatGroup> =
    chat
        .map { (it as? ChatLookup.Found)?.chat?.type }
        .distinctUntilChanged()
        .flatMapLatest { type ->
            when (type) {
                is ChatType.BasicGroup -> {
                    basicGroup(type.basicGroupId).map { ChatGroup(basicGroup = it) }
                }

                is ChatType.Supergroup -> {
                    supergroup(type.supergroupId).map { ChatGroup(supergroup = it) }
                }

                is ChatType.Secret -> {
                    secretChat.map { ChatGroup(secretChat = it) }
                }

                else -> {
                    flowOf(ChatGroup())
                }
            }
        }.distinctUntilChanged()

/**
 * The chat as the conversation of [thread] shows it ([ThreadState.inThread]): pending while a forum topic is on its
 * way, not found when the topic cannot be found.
 */
private fun inThread(
    lookup: ChatLookup,
    thread: ThreadState,
): ChatLookup =
    when {
        lookup !is ChatLookup.Found -> lookup
        thread == ThreadState.Missing -> ChatLookup.Failed(DomainError.NotFound)
        thread is ThreadState.Failed -> ChatLookup.Failed(thread.error)
        else -> ChatLookup.Found(thread.inThread(lookup.chat))
    }
