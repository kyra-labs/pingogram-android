package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.repository.ShownChatListRepository
import kotlinx.coroutines.flow.MutableStateFlow

/** [ShownChatListRepository] for tests: [shown] is the list shown, the main list at first. */
class FakeShownChatListRepository : ShownChatListRepository {
    override val shown = MutableStateFlow<ChatListType>(ChatListType.Main)

    override fun show(list: ChatListType) {
        shown.value = list
    }
}
