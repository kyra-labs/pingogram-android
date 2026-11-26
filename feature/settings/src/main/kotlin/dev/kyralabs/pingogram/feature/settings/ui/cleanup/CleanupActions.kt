package dev.kyralabs.pingogram.feature.settings.ui.cleanup

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.DeleteChatAction
import dev.kyralabs.pingogram.core.domain.model.DeleteChatOptions
import dev.kyralabs.pingogram.core.domain.repository.ChatOpeningsRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import javax.inject.Inject

/**
 * What Cleanup suggestions' choices do to a chat (`REQUIREMENTS.md` FR-X24), one chat at a time and only when the user
 * chooses: mute it for good, archive it, leave it (as deleting a group or channel does, never for everyone), or keep
 * it, counted as opened now so it is not suggested again for the months chosen.
 */
class CleanupActions internal constructor(
    val mute: suspend (chatId: Long) -> Outcome<Unit>,
    val archive: suspend (chatId: Long) -> Outcome<Unit>,
    val canLeave: suspend (chatId: Long) -> Boolean,
    val leave: suspend (chatId: Long) -> Outcome<Unit>,
    val keep: suspend (chatId: Long) -> Outcome<Unit>,
) {
    @Inject
    constructor(
        chatRepository: ChatRepository,
        openings: ChatOpeningsRepository,
    ) : this(
        mute = { chatRepository.setMuted(it, isMuted = true) },
        archive = { chatRepository.setArchived(it, isArchived = true) },
        canLeave = { chatId -> offersLeave((chatRepository.deleteOptions(chatId) as? Outcome.Success)?.value) },
        leave = { chatRepository.deleteChat(it, forEveryone = false) },
        keep = openings::markOpened,
    )
}

/**
 * Whether Leave is offered for a chat whose deletion does as [options] says (null when TDLib could not tell): a group
 * or channel the user is in, and does not own (owner decision 2026-11-25); only an owner can delete it for everyone.
 */
internal fun offersLeave(options: DeleteChatOptions?): Boolean =
    options != null && options.action == DeleteChatAction.Leave && !options.canDeleteForEveryone
