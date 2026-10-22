package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.mapper.toFormattedText
import dev.kyralabs.pingogram.core.data.mapper.toGroupMember
import dev.kyralabs.pingogram.core.data.mapper.toInviteLink
import dev.kyralabs.pingogram.core.data.mapper.toMemberPermissions
import dev.kyralabs.pingogram.core.data.mapper.toMemberRole
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.GroupDetails
import dev.kyralabs.pingogram.core.domain.model.GroupInfo
import dev.kyralabs.pingogram.core.domain.model.GroupKind
import dev.kyralabs.pingogram.core.domain.model.GroupMember
import dev.kyralabs.pingogram.core.domain.model.GroupMembersPage
import dev.kyralabs.pingogram.core.domain.model.MemberFilter
import dev.kyralabs.pingogram.core.domain.model.MemberPermissions
import dev.kyralabs.pingogram.core.domain.model.MemberRole
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.isAdministrator
import dev.kyralabs.pingogram.core.domain.repository.GroupInfoRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [GroupInfoRepository] on TDLib: `getBasicGroup` or `getSupergroup` and their full info, followed through
 * `updateBasicGroup`, `updateSupergroup` and their `…FullInfo` updates while collected, and the members through
 * `getSupergroupMembers` (supergroups and channels, a page at a time) or `searchChatMembers` (basic groups, whose full
 * info holds every member). Nothing is cached, so it is no `TdlibUpdateCache`: TDLib keeps all of it.
 *
 * - **Order**: the updates are followed before the requests go out, so a change in between is not missed; whichever
 *   arrives last wins, as TDLib sends an update before the answer it causes.
 * - **Description**: TDLib sends it as plain text; its links, mentions and hashtags come from `getTextEntities`, which
 *   TDLib answers on the device.
 * - **Default permissions** come from the chat (`getChat`, then `updateChatPermissions`).
 * - **Basic groups** count their administrators from the members of the full info; they have no restricted or banned
 *   members, no slow mode and no usernames.
 *
 * Nothing about a group (description, usernames, links) is logged.
 */
@Singleton
class TdlibGroupInfoRepository
    @Inject
    constructor(
        private val client: TdlibClient,
    ) : GroupInfoRepository {
        override fun groupInfo(chatId: Long): Flow<Outcome<GroupInfo>> =
            channelFlow {
                val chat =
                    when (val found = client.request(TdApi.GetChat(chatId))) {
                        is Outcome.Success -> {
                            found.value
                        }

                        is Outcome.Failure -> {
                            send(found)
                            return@channelFlow
                        }
                    }
                val source =
                    sourceOf(chat.type) ?: run {
                        send(Outcome.Failure(DomainError.NotFound))
                        return@channelFlow
                    }
                val state = MutableStateFlow(GroupState(permissions = chat.permissions.toMemberPermissions()))
                launch(start = CoroutineStart.UNDISPATCHED) {
                    client.updates.collect { update ->
                        if (update is TdApi.UpdateChatPermissions && update.chatId == chatId) {
                            state.update { it.copy(permissions = update.permissions.toMemberPermissions()) }
                        } else {
                            source.reduce(update, state)
                        }
                    }
                }
                launch { state.update { it.withHead(client.request(source.groupRequest()).map(source::head)) } }
                launch {
                    val details =
                        when (val full = client.request(source.fullInfoRequest())) {
                            is Outcome.Success -> Outcome.Success(source.details(full.value))
                            is Outcome.Failure -> full
                        }
                    state.update { it.withDetails(details) }
                }
                state.collectLatest { current -> current.toOutcome()?.let { send(it) } }
            }

        override suspend fun members(
            chatId: Long,
            filter: MemberFilter,
            query: String,
            offset: Int,
            limit: Int,
        ): Outcome<GroupMembersPage> {
            val source =
                when (val found = groupOf(chatId)) {
                    is Outcome.Success -> found.value
                    is Outcome.Failure -> return found
                }
            val request = membersRequest(chatId, source, filter, query, offset, limit)
            return if (request == null) {
                // All of a basic group's members come at once; it restricts and bans nobody.
                Outcome.Success(GroupMembersPage(persistentListOf(), 0))
            } else {
                client.request(request).map { found ->
                    GroupMembersPage(
                        members =
                            found.members
                                .orEmpty()
                                .mapNotNull { it?.toGroupMember() }
                                .toImmutableList(),
                        totalCount = found.totalCount.coerceAtLeast(0),
                    )
                }
            }
        }

        /** What asks TDLib for the members; null where there are none to ask for. */
        @Suppress("LongParameterList") // The page asked for, and the group it is of.
        private fun membersRequest(
            chatId: Long,
            source: GroupSource,
            filter: MemberFilter,
            query: String,
            offset: Int,
            limit: Int,
        ): TdApi.Function<TdApi.ChatMembers>? =
            when (source) {
                is GroupSource.Basic -> {
                    val members =
                        when (filter) {
                            MemberFilter.Recent -> TdApi.ChatMembersFilterMembers()
                            MemberFilter.Administrators -> TdApi.ChatMembersFilterAdministrators()
                            MemberFilter.Restricted, MemberFilter.Banned -> null
                        }
                    members
                        ?.takeIf { offset == 0 }
                        ?.let { TdApi.SearchChatMembers(chatId, query, BASIC_GROUP_MEMBERS_MAX, it) }
                }

                is GroupSource.Super -> {
                    TdApi.GetSupergroupMembers(source.id, filter.toSupergroupFilter(query), offset, limit)
                }
            }

        override suspend fun member(
            chatId: Long,
            userId: Long,
        ): Outcome<GroupMember> =
            client.request(TdApi.GetChatMember(chatId, TdApi.MessageSenderUser(userId))).map { member ->
                member.toGroupMember() ?: GroupMember(MessageSender.User(userId), MemberRole.Left)
            }

        /** The group or channel behind chat [chatId]; `DomainError.NotFound` for any other chat. */
        private suspend fun groupOf(chatId: Long): Outcome<GroupSource> =
            when (val chat = client.request(TdApi.GetChat(chatId))) {
                is Outcome.Failure -> {
                    chat
                }

                is Outcome.Success -> {
                    sourceOf(chat.value.type)?.let { Outcome.Success(it) }
                        ?: Outcome.Failure(DomainError.NotFound)
                }
            }

        /** How a chat of [type] is read; null for chats that are no group or channel. */
        private fun sourceOf(type: TdApi.ChatType?): GroupSource? =
            when (type) {
                is TdApi.ChatTypeBasicGroup -> GroupSource.Basic(type.basicGroupId, ::describe)
                is TdApi.ChatTypeSupergroup -> GroupSource.Super(type.supergroupId, ::describe)
                else -> null
            }

        /** The group's [description] with the entities TDLib finds in it; without them if it cannot. */
        private suspend fun describe(description: String?): FormattedText {
            val text = description.orEmpty()
            if (text.isBlank()) return FormattedText.Empty
            val entities =
                when (val found = client.request(TdApi.GetTextEntities(text))) {
                    is Outcome.Success -> found.value.entities.orEmpty()
                    is Outcome.Failure -> emptyArray()
                }
            return TdApi.FormattedText(text, entities).toFormattedText()
        }

        /** How a basic group or a supergroup is read and followed. */
        private sealed interface GroupSource {
            fun groupRequest(): TdApi.Function<out TdApi.Object>

            fun fullInfoRequest(): TdApi.Function<out TdApi.Object>

            fun head(group: TdApi.Object): GroupHead

            suspend fun details(fullInfo: TdApi.Object): GroupDetails

            suspend fun reduce(
                update: TdApi.Update,
                state: MutableStateFlow<GroupState>,
            )

            /** [describe] finds the entities of a description. */
            class Basic(
                val id: Long,
                private val describe: suspend (String?) -> FormattedText,
            ) : GroupSource {
                override fun groupRequest() = TdApi.GetBasicGroup(id)

                override fun fullInfoRequest() = TdApi.GetBasicGroupFullInfo(id)

                override fun head(group: TdApi.Object): GroupHead {
                    group as TdApi.BasicGroup
                    return GroupHead(GroupKind.BasicGroup, emptyList(), group.memberCount, group.status.toMemberRole())
                }

                override suspend fun details(fullInfo: TdApi.Object): GroupDetails {
                    fullInfo as TdApi.BasicGroupFullInfo
                    val members = fullInfo.members.orEmpty().mapNotNull { it?.toGroupMember() }
                    return GroupDetails(
                        description = describe(fullInfo.description),
                        memberCount = members.size,
                        administratorCount = members.count { it.role.isAdministrator },
                        restrictedCount = 0,
                        bannedCount = 0,
                        canGetMembers = true,
                        hasHiddenMembers = false,
                        slowModeDelay = 0,
                        members = members.toImmutableList(),
                        primaryInviteLink = fullInfo.inviteLink?.toInviteLink(),
                    )
                }

                override suspend fun reduce(
                    update: TdApi.Update,
                    state: MutableStateFlow<GroupState>,
                ) {
                    when {
                        update is TdApi.UpdateBasicGroup && update.basicGroup?.id == id -> {
                            state.update { it.withHead(Outcome.Success(head(update.basicGroup))) }
                        }

                        update is TdApi.UpdateBasicGroupFullInfo && update.basicGroupId == id -> {
                            val full = update.basicGroupFullInfo ?: return
                            val details = details(full)
                            state.update { it.withDetails(Outcome.Success(details)) }
                        }
                    }
                }
            }

            class Super(
                val id: Long,
                private val describe: suspend (String?) -> FormattedText,
            ) : GroupSource {
                override fun groupRequest() = TdApi.GetSupergroup(id)

                override fun fullInfoRequest() = TdApi.GetSupergroupFullInfo(id)

                override fun head(group: TdApi.Object): GroupHead {
                    group as TdApi.Supergroup
                    val verification = group.verificationStatus
                    return GroupHead(
                        kind = if (group.isChannel) GroupKind.Channel else GroupKind.Supergroup,
                        usernames =
                            group.usernames
                                ?.activeUsernames
                                .orEmpty()
                                .toList(),
                        memberCount = group.memberCount,
                        role = group.status.toMemberRole(),
                        isVerified = verification?.isVerified == true,
                        isScam = verification?.isScam == true,
                        isFake = verification?.isFake == true,
                    )
                }

                override suspend fun details(fullInfo: TdApi.Object): GroupDetails {
                    fullInfo as TdApi.SupergroupFullInfo
                    return GroupDetails(
                        description = describe(fullInfo.description),
                        memberCount = fullInfo.memberCount,
                        administratorCount = fullInfo.administratorCount,
                        restrictedCount = fullInfo.restrictedCount,
                        bannedCount = fullInfo.bannedCount,
                        canGetMembers = fullInfo.canGetMembers,
                        hasHiddenMembers = fullInfo.hasHiddenMembers,
                        slowModeDelay = fullInfo.slowModeDelay,
                        members = persistentListOf(),
                        primaryInviteLink = fullInfo.inviteLink?.toInviteLink(),
                    )
                }

                override suspend fun reduce(
                    update: TdApi.Update,
                    state: MutableStateFlow<GroupState>,
                ) {
                    when {
                        update is TdApi.UpdateSupergroup && update.supergroup?.id == id -> {
                            state.update { it.withHead(Outcome.Success(head(update.supergroup))) }
                        }

                        update is TdApi.UpdateSupergroupFullInfo && update.supergroupId == id -> {
                            val full = update.supergroupFullInfo ?: return
                            val details = details(full)
                            state.update { it.withDetails(Outcome.Success(details)) }
                        }
                    }
                }
            }
        }

        private companion object {
            /** As many members as a basic group can have, so one search returns them all. */
            const val BASIC_GROUP_MEMBERS_MAX = 200
        }
    }

/** What the group itself says, before and apart from its full info. */
private data class GroupHead(
    val kind: GroupKind,
    val usernames: List<String>,
    val memberCount: Int,
    val role: MemberRole,
    val isVerified: Boolean = false,
    val isScam: Boolean = false,
    val isFake: Boolean = false,
)

/** The group and its full info as far as read: null while on their way. */
private data class GroupState(
    val head: Outcome<GroupHead>? = null,
    val details: Outcome<GroupDetails>? = null,
    val permissions: MemberPermissions = MemberPermissions.None,
) {
    /** A group read once keeps showing when a later read fails; a failure only stands in for none. */
    fun withHead(new: Outcome<GroupHead>): GroupState =
        if (new is Outcome.Failure && head is Outcome.Success) this else copy(head = new)

    fun withDetails(new: Outcome<GroupDetails>): GroupState =
        if (new is Outcome.Failure && details is Outcome.Success) this else copy(details = new)

    /** Null until the group is read. */
    fun toOutcome(): Outcome<GroupInfo>? =
        when (val head = head) {
            null -> {
                null
            }

            is Outcome.Failure -> {
                head
            }

            is Outcome.Success -> {
                val group = head.value
                Outcome.Success(
                    GroupInfo(
                        kind = group.kind,
                        usernames = group.usernames.toImmutableList(),
                        memberCount = group.memberCount,
                        isVerified = group.isVerified,
                        isScam = group.isScam,
                        isFake = group.isFake,
                        myRole = group.role,
                        defaultPermissions =
                            if (group.kind == GroupKind.Channel) {
                                MemberPermissions.None
                            } else {
                                permissions
                            },
                        details = (details as? Outcome.Success)?.value,
                        detailsError = (details as? Outcome.Failure)?.error,
                    ),
                )
            }
        }
}

/** The supergroup filter of [this] with [query] (a name or username), which administrators are not searched by. */
private fun MemberFilter.toSupergroupFilter(query: String): TdApi.SupergroupMembersFilter =
    when (this) {
        MemberFilter.Recent -> {
            if (query.isBlank()) {
                TdApi.SupergroupMembersFilterRecent()
            } else {
                TdApi.SupergroupMembersFilterSearch(query)
            }
        }

        MemberFilter.Administrators -> {
            TdApi.SupergroupMembersFilterAdministrators()
        }

        MemberFilter.Restricted -> {
            TdApi.SupergroupMembersFilterRestricted(query)
        }

        MemberFilter.Banned -> {
            TdApi.SupergroupMembersFilterBanned(query)
        }
    }
