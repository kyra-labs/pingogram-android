package dev.kyralabs.pingogram.feature.chats.ui

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.onFailure
import dev.kyralabs.pingogram.core.domain.model.ArchiveRowStyle
import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.repository.ArchiveRowRepository
import dev.kyralabs.pingogram.core.domain.repository.ArchiveSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch

/**
 * What [ChatListViewModel] does to whole lists rather than to chats (`REQUIREMENTS.md` FR-L3, FR-L9), a part of it:
 *
 * - **The archive row's style** ([ChatListEvent.SetArchiveRowStyle]): kept on the device by [archiveRowRepository],
 *   which fails only on a broken disk (a snackbar then). Hiding the row ([shownArchive], the row shown now) offers to
 *   bring back the style it had ([ChatListEffect.ArchiveRowHidden]).
 * - **Mark all as read** ([ChatListEvent.MarkAllAsRead], a folder tab's menu): `readChatList` of the folder, or of the
 *   main list for "All chats"; its effect comes through TDLib's updates, a failure is a snackbar.
 * - **The archive's settings** ([ChatListEvent.OpenArchiveSettings]), from the archive's top bar.
 *
 * Its work runs in [scope], the view model's, and what it has to say goes to [effects].
 */
internal class ChatListActions(
    private val scope: CoroutineScope,
    private val archiveRowRepository: ArchiveRowRepository,
    private val archiveSettingsRepository: ArchiveSettingsRepository,
    private val shownArchive: () -> ArchiveEntry?,
    private val effects: SendChannel<ChatListEffect>,
) {
    fun onEvent(event: ChatListEvent) {
        when (event) {
            ChatListEvent.OpenArchiveSettings -> effects.trySend(ChatListEffect.OpenArchiveSettings)
            is ChatListEvent.SetArchiveRowStyle -> setArchiveRowStyle(event.style)
            is ChatListEvent.MarkAllAsRead -> markAllAsRead(event.folderId)
            else -> Unit
        }
    }

    private fun setArchiveRowStyle(style: ArchiveRowStyle) {
        val shown = shownArchive()
        val previous = if (shown?.isCompact == true) ArchiveRowStyle.Compact else ArchiveRowStyle.Full
        scope.launch {
            when (val saved = archiveRowRepository.setStyle(style)) {
                is Outcome.Success -> {
                    if (style == ArchiveRowStyle.Hidden && shown != null) {
                        effects.trySend(ChatListEffect.ArchiveRowHidden(previous))
                    }
                }

                is Outcome.Failure -> {
                    effects.trySend(ChatListEffect.ShowError(saved.error))
                }
            }
        }
    }

    private fun markAllAsRead(folderId: Int?) {
        val list = folderId?.let { ChatListType.Folder(it) } ?: ChatListType.Main
        scope.launch {
            archiveSettingsRepository.readChatList(list).onFailure { effects.trySend(ChatListEffect.ShowError(it)) }
        }
    }
}
