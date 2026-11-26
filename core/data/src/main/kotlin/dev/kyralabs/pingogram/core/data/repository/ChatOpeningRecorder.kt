package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.repository.ChatLockRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatOpeningsRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Records a chat as opened ([ChatOpeningsRepository.markOpened], `REQUIREMENTS.md` FR-X24) each time its conversation
 * comes on screen ([OpenChats]), from when the account's graph is made: a [TdlibUpdateCache] only to be made then.
 * A locked chat and a secret chat are never recorded: the log must not tell they were opened.
 */
@AccountScoped
class ChatOpeningRecorder
    @Inject
    constructor(
        openChats: OpenChats,
        openings: ChatOpeningsRepository,
        chats: ChatRepository,
        chatLock: ChatLockRepository,
        @ApplicationScope scope: CoroutineScope,
    ) : TdlibUpdateCache {
        init {
            scope.launch {
                var shown = emptySet<Long>()
                openChats.chatIds.collect { open ->
                    val opened = open - shown
                    shown = open
                    if (opened.isEmpty()) return@collect
                    val locked = chatLock.lockedChatIds.first()
                    opened
                        .filterNot { it in locked || chats.isSecret(it) }
                        .forEach { openings.markOpened(it) }
                }
            }
        }

        private companion object {
            /** From memory: a conversation on screen is a chat TDLib has announced. */
            suspend fun ChatRepository.isSecret(chatId: Long): Boolean = chat(chatId).first()?.type is ChatType.Secret
        }
    }
