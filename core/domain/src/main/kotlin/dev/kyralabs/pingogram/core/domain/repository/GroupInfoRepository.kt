package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.GroupInfo
import dev.kyralabs.pingogram.core.domain.model.GroupMember
import dev.kyralabs.pingogram.core.domain.model.GroupMembersPage
import dev.kyralabs.pingogram.core.domain.model.MemberFilter
import kotlinx.coroutines.flow.Flow

/**
 * What the info page of a group or channel reads (`REQUIREMENTS.md` FR-P3): the group with the current user's rights
 * and its full info, and its members. Nothing is cached here: TDLib keeps both and reports their changes. The chat
 * itself (title, photo, mute) is `ChatRepository`'s.
 *
 * Requests that get no answer in time fail with `DomainError.Network`; a chat TDLib does not know, or one that is no
 * group or channel, fails with `DomainError.NotFound`.
 */
interface GroupInfoRepository {
    /**
     * The group or channel of chat [chatId] (TDLib answers from memory), then again with its full info once TDLib has
     * it (it asks Telegram the first time), then after every change of either while collected: a member promoted, the
     * description edited on another device, the current user's rights changed. A group that cannot be read is the
     * flow's first value as a failure; a full info that cannot be read leaves [GroupInfo.details] null with
     * [GroupInfo.detailsError].
     */
    fun groupInfo(chatId: Long): Flow<Outcome<GroupInfo>>

    /**
     * Up to [limit] members of chat [chatId] that [filter] and [query] select (a name or username; ignored by
     * [MemberFilter.Administrators]), after the first [offset] of them. A supergroup's or channel's members come a page
     * at a time; a basic group's all at once, in the first page (later pages are empty), and it has no restricted or
     * banned members. Only administrators may list a channel's subscribers or a group's hidden members.
     */
    suspend fun members(
        chatId: Long,
        filter: MemberFilter,
        query: String = "",
        offset: Int = 0,
        limit: Int = PAGE_SIZE,
    ): Outcome<GroupMembersPage>

    /** User [userId] as a member of chat [chatId], with their role; `MemberRole.Left` for one who is not. */
    suspend fun member(
        chatId: Long,
        userId: Long,
    ): Outcome<GroupMember>

    companion object {
        /** Members a page holds: the most `getSupergroupMembers` returns at once. */
        const val PAGE_SIZE = 200
    }
}
