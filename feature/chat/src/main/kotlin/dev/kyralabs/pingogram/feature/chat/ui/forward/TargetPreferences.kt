package dev.kyralabs.pingogram.feature.chat.ui.forward

import dev.kyralabs.pingogram.core.domain.repository.ChatLockRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatsShownFirstRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

/**
 * What the sheets that pick chats ([ChatTargets]) take from the user's settings: the chats they leave out, locked
 * chats not opened with the passcode (`docs/adr/0029-locked-chats.md`), and those they show first, after Saved
 * Messages (Settings → Appearance → Chats → "Chats shown first"). One parameter for the view models that build them.
 */
class TargetPreferences(
    val hidden: Flow<Set<Long>> = flowOf(emptySet()),
    val shownFirst: Flow<List<Long>> = flowOf(emptyList()),
) {
    @Inject
    constructor(
        chatLock: ChatLockRepository,
        shownFirst: ChatsShownFirstRepository,
    ) : this(chatLock.hiddenChatIds.filterNotNull(), shownFirst.chatIds)
}
