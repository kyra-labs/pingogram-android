package dev.kyralabs.pingogram.feature.chat.ui.forward

import dev.kyralabs.pingogram.core.domain.repository.ChatLockRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatsShownFirstRepository
import dev.kyralabs.pingogram.core.domain.repository.LocalChatNamesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

/**
 * What the sheets that pick chats ([ChatTargets]) take from the user's settings: the chats they leave out, locked
 * chats not opened with the passcode (`docs/adr/0029-locked-chats.md`), those they show first, after Saved
 * Messages (Settings → Appearance → Chats → "Chats shown first"), and the chats' names on this phone
 * (`REQUIREMENTS.md` FR-X20). One parameter for the view models that build them.
 */
class TargetPreferences(
    val hidden: Flow<Set<Long>> = flowOf(emptySet()),
    val shownFirst: Flow<List<Long>> = flowOf(emptyList()),
    val localNames: Flow<Map<Long, String>> = flowOf(emptyMap()),
) {
    @Inject
    constructor(
        chatLock: ChatLockRepository,
        shownFirst: ChatsShownFirstRepository,
        localChatNames: LocalChatNamesRepository,
    ) : this(chatLock.hiddenChatIds.filterNotNull(), shownFirst.chatIds, localChatNames.names)
}
