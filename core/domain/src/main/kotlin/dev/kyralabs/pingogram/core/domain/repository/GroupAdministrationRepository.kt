package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.MemberPermissions
import dev.kyralabs.pingogram.core.domain.model.MemberRole
import dev.kyralabs.pingogram.core.domain.model.MessageSender

/**
 * What administrators change in a group or channel (`REQUIREMENTS.md` FR-P3), and clearing its history (FR-P4): one
 * request per action of the user's, never in bulk (Telegram's API terms). Changes reach `ChatRepository` and
 * `GroupInfoRepository` through TDLib's updates.
 *
 * Each fails with a `DomainError`: `AdminRightsRequired` for a change the current user's rights do not allow,
 * `FloodWait`, `Network` for a request that gets no answer in time, …
 */
interface GroupAdministrationRepository {
    /** Renames chat [chatId]; [title] must not be blank (`DomainError.TitleInvalid`). */
    suspend fun setTitle(
        chatId: Long,
        title: String,
    ): Outcome<Unit>

    /** Sets the description of chat [chatId], plain text; an empty one removes it. */
    suspend fun setDescription(
        chatId: Long,
        description: String,
    ): Outcome<Unit>

    /**
     * Makes the photo at [path] (a copy from `PickedPhotoRepository.preparePhoto`, which the caller deletes afterwards)
     * the photo of chat [chatId], waiting while it uploads and handing the share uploaded, from 0 to 1, to
     * [onProgress].
     */
    suspend fun setPhoto(
        chatId: Long,
        path: String,
        onProgress: (progress: Float) -> Unit,
    ): Outcome<Unit>

    /** Removes the photo of chat [chatId]; the chat then shows its initials. */
    suspend fun removePhoto(chatId: Long): Outcome<Unit>

    /** Sets what members of group [chatId] may do by default (not for channels). */
    suspend fun setPermissions(
        chatId: Long,
        permissions: MemberPermissions,
    ): Outcome<Unit>

    /** Sets the slow mode of supergroup [chatId]: one of `SlowModeDelays`, in seconds; 0 turns it off. */
    suspend fun setSlowModeDelay(
        chatId: Long,
        seconds: Int,
    ): Outcome<Unit>

    /**
     * Gives [member] of chat [chatId] [role]: promotes them with administrator rights, takes the rights away
     * ([MemberRole.Member]), restricts, removes and bans ([MemberRole.Banned]), or lets a banned user back
     * ([MemberRole.Left]). Ownership is not transferred here.
     */
    suspend fun setMemberRole(
        chatId: Long,
        member: MessageSender,
        role: MemberRole,
    ): Outcome<Unit>

    /** Deletes the history of chat [chatId] for the current user only, keeping the chat in the lists. */
    suspend fun clearHistory(chatId: Long): Outcome<Unit>
}
