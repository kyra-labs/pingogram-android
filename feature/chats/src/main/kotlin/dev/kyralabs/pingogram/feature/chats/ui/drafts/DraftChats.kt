package dev.kyralabs.pingogram.feature.chats.ui.drafts

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.repository.ChatLockRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.feature.chats.ui.shownDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * The chats of the Drafts page (`REQUIREMENTS.md` FR-X16): every main or archived chat whose draft the chat list
 * shows ([drafts]), never a locked one, as in the chat list (`docs/adr/0029-locked-chats.md`); and [loaded], which
 * loads the rest of both lists, as TDLib knows only the chats loaded so far.
 */
class DraftChats
    @Inject
    constructor(
        private val chatRepository: ChatRepository,
        chatLock: ChatLockRepository,
    ) {
        /** The chats with a draft the chat list shows, from both lists, newest draft first, then every change. */
        val drafts: Flow<List<Chat>> =
            combine(combine(LISTS.map(chatRepository::chatList)) { it.toList() }, chatLock.lockedChatIds) {
                lists,
                locked,
                ->
                lists
                    .flatMap { it }
                    .distinctBy { it.id }
                    .filterNot { it.id in locked }
                    .mapNotNull { chat -> chat.shownDraft?.let { chat to it.date } }
                    .sortedByDescending { it.second }
                    .map { it.first }
            }

        /**
         * Once collected, false while the rest of both lists loads, page by page, until each ends or a page fails;
         * then true.
         */
        val loaded: Flow<Boolean> =
            flow {
                emit(false)
                LISTS.forEach { list -> loadAll(list) }
                emit(true)
            }

        /** Loads the rest of [list], page by page, until it ends or a page fails. */
        private suspend fun loadAll(list: ChatListType) {
            // Each page reaches the list's flow by itself.
            do {
                val more = (chatRepository.loadChats(list) as? Outcome.Success)?.value == true
            } while (more)
        }

        private companion object {
            val LISTS = listOf(ChatListType.Main, ChatListType.Archive)
        }
    }
