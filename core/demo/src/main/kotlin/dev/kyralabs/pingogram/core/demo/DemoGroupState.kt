package dev.kyralabs.pingogram.core.demo

import org.drinkless.tdlib.TdApi

/**
 * The demo's groups and channels beyond their chats (`docs/adr/0034-demo-mode-for-store-review.md`): the basic group
 * and the channel the user made, with their full info, members, invite links, join requests and recent actions, and
 * the groups and channels the user creates in the demo. Not thread-safe: used under the client's lock.
 *
 * One accessor per kind of thing a group has, guard clauses for chats that are not the demo's, and sample numbers:
 * hence the suppressions.
 */
@Suppress("TooManyFunctions", "ReturnCount", "MagicNumber")
internal class DemoGroupState(
    data: DemoData,
    private val now: () -> Int,
) {
    val basicGroups = mutableMapOf(data.group.id to data.group)
    val basicFullInfo = mutableMapOf(data.group.id to data.groupFullInfo)
    val supergroups = mutableMapOf(data.channel.id to data.channel)
    val supergroupFullInfo = mutableMapOf(data.channel.id to data.channelFullInfo)

    /** A supergroup's or channel's members; a basic group's live in its full info. */
    val members = mutableMapOf<Long, MutableList<TdApi.ChatMember>>()
    val links = mutableMapOf<Long, MutableList<TdApi.ChatInviteLink>>()
    val requests = mutableMapOf<Long, MutableList<TdApi.ChatJoinRequest>>()
    val events = mutableMapOf<Long, MutableList<TdApi.ChatEvent>>()

    private var nextBasicGroupId = data.group.id + 1
    private var nextSupergroupId = data.channel.id + 1
    private var nextLink = 1
    private var nextEvent = 1L

    init {
        val started = data.channel.date
        members[DemoData.CHANNEL_CHAT] =
            listOf(DemoData.ME, DemoData.ADA, DemoData.BEN, DemoData.CHLOE)
                .mapIndexed { index, user ->
                    val status: TdApi.ChatMemberStatus =
                        if (user ==
                            DemoData.ME
                        ) {
                            TdApi.ChatMemberStatusCreator(false, true)
                        } else {
                            TdApi.ChatMemberStatusMember(0)
                        }
                    TdApi.ChatMember(TdApi.MessageSenderUser(user), "", 0, started + index * HOUR, status)
                }.toMutableList()
        links[DemoData.GROUP_CHAT] = mutableListOf(link(primary = true, date = started))
        links[DemoData.CHANNEL_CHAT] =
            mutableListOf(
                link(primary = true, date = started).apply { memberCount = 3 },
                link(primary = false, date = started + DAY, name = data.text(R.string.demo_link_newsletter)).apply {
                    createsJoinRequest = true
                    pendingJoinRequestCount = 2
                },
            )
        requests[DemoData.CHANNEL_CHAT] =
            mutableListOf(
                TdApi.ChatJoinRequest(DemoData.DEV, started + 2 * DAY, data.text(R.string.demo_dev_join_request)),
                TdApi.ChatJoinRequest(DemoData.FINN, started + 3 * DAY, data.text(R.string.demo_finn_join_request)),
            )
        events[DemoData.CHANNEL_CHAT] =
            mutableListOf(
                event(DemoData.ADA, TdApi.ChatEventMemberJoined(), started + HOUR),
                event(
                    DemoData.ME,
                    TdApi.ChatEventDescriptionChanged("", data.channelFullInfo.description),
                    started + 2 * HOUR,
                ),
                event(DemoData.BEN, TdApi.ChatEventMemberJoined(), started + 3 * HOUR),
                event(DemoData.CHLOE, TdApi.ChatEventMemberJoined(), started + DAY),
            )
        basicFullInfo.getValue(data.group.id).inviteLink = links.getValue(DemoData.GROUP_CHAT).first()
        supergroupFullInfo.getValue(data.channel.id).inviteLink = links.getValue(DemoData.CHANNEL_CHAT).first()
    }

    fun nextBasicGroupId(): Long = nextBasicGroupId++

    fun nextSupergroupId(): Long = nextSupergroupId++

    /** A new invite link of the user's, unique and made up: it opens nothing outside the demo. */
    fun link(
        primary: Boolean,
        date: Int = now(),
        name: String = "",
    ) = TdApi.ChatInviteLink().apply {
        inviteLink = "https://t.me/+demo${nextLink++}"
        this.name = name
        creatorUserId = DemoData.ME
        this.date = date
        isPrimary = primary
    }

    /** Records an action in a supergroup's or channel's recent actions, the newest first. */
    fun record(
        chatId: Long,
        action: TdApi.ChatEventAction,
        userId: Long = DemoData.ME,
    ) {
        if (DemoIds.supergroupId(chatId) == null) return
        events.getOrPut(chatId) { mutableListOf() }.add(event(userId, action, now()))
    }

    private fun event(
        userId: Long,
        action: TdApi.ChatEventAction,
        date: Int,
    ) = TdApi.ChatEvent(nextEvent++, date, TdApi.MessageSenderUser(userId), action)

    /** A chat's members: a basic group's from its full info, a supergroup's or channel's from [members]. */
    fun membersOf(chatId: Long): List<TdApi.ChatMember> =
        DemoIds.basicGroupId(chatId)?.let { basicFullInfo[it]?.members?.toList() } ?: members[chatId].orEmpty()

    fun setMembers(
        chatId: Long,
        list: List<TdApi.ChatMember>,
    ): List<TdApi.Update> {
        DemoIds.basicGroupId(chatId)?.let { id ->
            val info = basicFullInfo[id] ?: return emptyList()
            val group = basicGroups.getValue(id)
            info.members = list.toTypedArray()
            group.memberCount = list.count { it.isPresent() }
            return listOf(TdApi.UpdateBasicGroupFullInfo(id, info), TdApi.UpdateBasicGroup(group))
        }
        val id = DemoIds.supergroupId(chatId) ?: return emptyList()
        members[chatId] = list.toMutableList()
        val group = supergroups[id] ?: return emptyList()
        val info = supergroupFullInfo.getValue(id)
        val present = list.count { it.isPresent() }
        if (!group.isChannel) group.memberCount = present
        info.memberCount = group.memberCount
        info.administratorCount =
            list.count {
                it.status is TdApi.ChatMemberStatusAdministrator || it.status is TdApi.ChatMemberStatusCreator
            }
        info.restrictedCount = list.count { it.status is TdApi.ChatMemberStatusRestricted }
        info.bannedCount = list.count { it.status is TdApi.ChatMemberStatusBanned }
        return listOf(TdApi.UpdateSupergroup(group), TdApi.UpdateSupergroupFullInfo(id, info))
    }

    /** The user's own status in a chat: the creator in the demo's groups and channels. */
    fun statusOf(chatId: Long): TdApi.ChatMemberStatus? =
        DemoIds.basicGroupId(chatId)?.let { basicGroups[it]?.status }
            ?: DemoIds.supergroupId(chatId)?.let { supergroups[it]?.status }

    private fun TdApi.ChatMember.isPresent() =
        status !is TdApi.ChatMemberStatusBanned && status !is TdApi.ChatMemberStatusLeft &&
            (status as? TdApi.ChatMemberStatusRestricted)?.isMember != false

    private companion object {
        const val HOUR = 3_600
        const val DAY = 86_400
    }
}

/** TDLib's chat ids of basic groups (their negative id) and supergroups (-100… then their id), both ways. */
internal object DemoIds {
    private const val SUPERGROUP_BASE = -1_000_000_000_000L

    fun basicGroupChat(id: Long) = -id

    fun supergroupChat(id: Long) = SUPERGROUP_BASE - id

    fun basicGroupId(chatId: Long): Long? = (-chatId).takeIf { chatId < 0 && chatId > SUPERGROUP_BASE }

    fun supergroupId(chatId: Long): Long? = (SUPERGROUP_BASE - chatId).takeIf { chatId < SUPERGROUP_BASE }
}
