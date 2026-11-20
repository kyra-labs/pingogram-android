package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.repository.ShownChatListRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** [ShownChatListRepository] in memory, for the process: the main list until the Chats tab shows another. */
@Singleton
class InMemoryShownChatListRepository
    @Inject
    constructor() : ShownChatListRepository {
        private val mutableShown = MutableStateFlow<ChatListType>(ChatListType.Main)

        override val shown: StateFlow<ChatListType> = mutableShown.asStateFlow()

        override fun show(list: ChatListType) {
            mutableShown.value = list
        }
    }
