package dev.kyralabs.pingogram.core.demo

import org.drinkless.tdlib.TdApi

/**
 * The admin pages of the demo's groups and channels (`docs/adr/0034-demo-mode-for-store-review.md`): invite links,
 * join requests, recent actions, statistics (made-up numbers in Telegram's chart format) and boosts (read only, as the
 * app shows them). On [DemoWorld.groups].
 *
 * One branch per function, each starting with guard clauses for chats that are not the demo's, and made-up sample
 * statistics: hence the suppressions.
 */
@Suppress("CyclomaticComplexMethod", "LongMethod", "TooManyFunctions", "ReturnCount", "MagicNumber")
internal class DemoGroupAdmin : DemoArea {
    override fun answer(
        world: DemoWorld,
        function: TdApi.Function<*>,
    ): DemoAnswer? =
        when (function) {
            is TdApi.GetChatInviteLinks -> {
                val links =
                    world.groups.links[function.chatId]
                        .orEmpty()
                        .filter { it.creatorUserId == function.creatorUserId && it.isRevoked == function.isRevoked }
                        .take(function.limit)
                ok(TdApi.ChatInviteLinks(links.size, links.toTypedArray()))
            }

            is TdApi.GetChatInviteLink -> {
                findLink(world, function.chatId, function.inviteLink)?.let(::ok) ?: notFound()
            }

            is TdApi.CreateChatInviteLink -> {
                createLink(world, function)
            }

            is TdApi.EditChatInviteLink -> {
                editLink(world, function)
            }

            is TdApi.RevokeChatInviteLink -> {
                revokeLink(world, function.chatId, function.inviteLink)
            }

            is TdApi.ReplacePrimaryChatInviteLink -> {
                replacePrimary(world, function.chatId)
            }

            is TdApi.DeleteRevokedChatInviteLink -> {
                val links = world.groups.links[function.chatId] ?: return notFound()
                links.removeAll { it.inviteLink == function.inviteLink && it.isRevoked }
                okChanged(emptyList())
            }

            is TdApi.DeleteAllRevokedChatInviteLinks -> {
                world.groups.links[function.chatId]?.removeAll { it.isRevoked }
                okChanged(emptyList())
            }

            is TdApi.GetChatJoinRequests -> {
                val requests = world.groups.requests[function.chatId].orEmpty()
                ok(TdApi.ChatJoinRequests(requests.size, requests.take(function.limit).toTypedArray()))
            }

            is TdApi.ProcessChatJoinRequest -> {
                process(world, function.chatId, listOf(function.userId), function.approve)
            }

            is TdApi.ProcessChatJoinRequests -> {
                process(
                    world,
                    function.chatId,
                    world.groups.requests[function.chatId]
                        .orEmpty()
                        .map { it.userId },
                    function.approve,
                )
            }

            is TdApi.GetChatEventLog -> {
                eventLog(world, function)
            }

            is TdApi.GetChatStatistics -> {
                statistics(world, function.chatId)
            }

            is TdApi.GetChatBoostStatus -> {
                world.chat(function.chatId) ?: return notFound()
                ok(boostStatus(function.chatId))
            }

            // The user has no boosts to give: boosting needs Telegram Premium, which the app never offers.
            is TdApi.GetAvailableChatBoostSlots -> {
                ok(TdApi.ChatBoostSlots(emptyArray()))
            }

            else -> {
                null
            }
        }

    private fun findLink(
        world: DemoWorld,
        chatId: Long,
        link: String,
    ): TdApi.ChatInviteLink? = world.groups.links[chatId]?.firstOrNull { it.inviteLink == link }

    private fun createLink(
        world: DemoWorld,
        function: TdApi.CreateChatInviteLink,
    ): DemoAnswer {
        world.chat(function.chatId) ?: return notFound()
        val link =
            world.groups.link(primary = false, name = function.name.orEmpty()).apply {
                expirationDate = function.expirationDate
                memberLimit = if (function.createsJoinRequest) 0 else function.memberLimit
                createsJoinRequest = function.createsJoinRequest
            }
        world.groups.links
            .getOrPut(function.chatId) { mutableListOf() }
            .add(link)
        return changed(link, emptyList())
    }

    private fun editLink(
        world: DemoWorld,
        function: TdApi.EditChatInviteLink,
    ): DemoAnswer {
        val link = findLink(world, function.chatId, function.inviteLink) ?: return notFound()
        val old = DemoCodec.decode<TdApi.ChatInviteLink>(DemoCodec.encode(link))
        link.name = function.name.orEmpty()
        link.expirationDate = function.expirationDate
        link.memberLimit = if (function.createsJoinRequest) 0 else function.memberLimit
        link.createsJoinRequest = function.createsJoinRequest
        link.editDate = world.now
        world.groups.record(function.chatId, TdApi.ChatEventInviteLinkEdited(old, link))
        return changed(link, emptyList())
    }

    private fun revokeLink(
        world: DemoWorld,
        chatId: Long,
        inviteLink: String,
    ): DemoAnswer {
        val link = findLink(world, chatId, inviteLink) ?: return notFound()
        link.isRevoked = true
        world.groups.record(chatId, TdApi.ChatEventInviteLinkRevoked(link))
        if (!link.isPrimary) return changed(TdApi.ChatInviteLinks(1, arrayOf(link)), emptyList())
        link.isPrimary = false
        val primary = newPrimary(world, chatId)
        return changed(TdApi.ChatInviteLinks(2, arrayOf(link, primary)), fullInfoUpdates(world, chatId))
    }

    private fun replacePrimary(
        world: DemoWorld,
        chatId: Long,
    ): DemoAnswer {
        world.chat(chatId) ?: return notFound()
        world.groups.links[chatId]?.filter { it.isPrimary }?.forEach {
            it.isPrimary = false
            it.isRevoked = true
        }
        val primary = newPrimary(world, chatId)
        return changed(primary, fullInfoUpdates(world, chatId))
    }

    private fun newPrimary(
        world: DemoWorld,
        chatId: Long,
    ): TdApi.ChatInviteLink {
        val primary = world.groups.link(primary = true)
        world.groups.links
            .getOrPut(chatId) { mutableListOf() }
            .add(0, primary)
        DemoIds.basicGroupId(chatId)?.let { world.groups.basicFullInfo[it]?.inviteLink = primary }
        DemoIds.supergroupId(chatId)?.let { world.groups.supergroupFullInfo[it]?.inviteLink = primary }
        return primary
    }

    private fun fullInfoUpdates(
        world: DemoWorld,
        chatId: Long,
    ): List<TdApi.Update> =
        listOfNotNull(
            DemoIds.basicGroupId(chatId)?.let { id ->
                world.groups.basicFullInfo[id]?.let { TdApi.UpdateBasicGroupFullInfo(id, it) }
            },
            DemoIds.supergroupId(chatId)?.let { id ->
                world.groups.supergroupFullInfo[id]?.let { TdApi.UpdateSupergroupFullInfo(id, it) }
            },
        )

    /** Approved people join as members; either way their request leaves the list and the chat's count. */
    private fun process(
        world: DemoWorld,
        chatId: Long,
        userIds: List<Long>,
        approve: Boolean,
    ): DemoAnswer {
        val chat = world.chat(chatId) ?: return notFound()
        val requests = world.groups.requests[chatId] ?: return notFound()
        val handled = requests.filter { it.userId in userIds }
        if (handled.isEmpty()) return badRequest("HIDE_REQUESTER_MISSING")
        requests.removeAll(handled)
        val updates = mutableListOf<TdApi.Update>()
        if (approve) {
            val members = world.groups.membersOf(chatId).toMutableList()
            handled.forEach { request ->
                members.removeAll { (it.memberId as? TdApi.MessageSenderUser)?.userId == request.userId }
                members +=
                    TdApi.ChatMember(
                        TdApi.MessageSenderUser(request.userId),
                        "",
                        0,
                        world.now,
                        TdApi.ChatMemberStatusMember(0),
                    )
                world.groups.record(chatId, TdApi.ChatEventMemberJoinedByRequest(DemoData.ME, null), request.userId)
            }
            updates += world.groups.setMembers(chatId, members)
        }
        world.groups.links[chatId]?.forEach {
            it.pendingJoinRequestCount =
                minOf(it.pendingJoinRequestCount, requests.size)
        }
        chat.pendingJoinRequests = TdApi.ChatJoinRequestsInfo(requests.size, requests.map { it.userId }.toLongArray())
        updates += TdApi.UpdateChatPendingJoinRequests(chatId, chat.pendingJoinRequests)
        return okChanged(updates)
    }

    private fun eventLog(
        world: DemoWorld,
        function: TdApi.GetChatEventLog,
    ): DemoAnswer {
        val filters = function.filters
        val events =
            world.groups.events[function.chatId]
                .orEmpty()
                .asReversed()
                .filter { function.fromEventId == 0L || it.id < function.fromEventId }
                .filter { filters == null || kept(filters, it.action) }
                .take(function.limit)
        return ok(TdApi.ChatEvents(events.toTypedArray()))
    }

    /** Whether the page's chips keep an action, as `chatEventLogFilters` sorts them. */
    private fun kept(
        filters: TdApi.ChatEventLogFilters,
        action: TdApi.ChatEventAction,
    ): Boolean =
        when (action) {
            is TdApi.ChatEventMessageEdited -> {
                filters.messageEdits
            }

            is TdApi.ChatEventMessageDeleted -> {
                filters.messageDeletions
            }

            is TdApi.ChatEventMessagePinned, is TdApi.ChatEventMessageUnpinned -> {
                filters.messagePins
            }

            is TdApi.ChatEventMemberJoined,
            is TdApi.ChatEventMemberJoinedByInviteLink,
            is TdApi.ChatEventMemberJoinedByRequest,
            -> {
                filters.memberJoins
            }

            is TdApi.ChatEventMemberLeft -> {
                filters.memberLeaves
            }

            is TdApi.ChatEventMemberInvited -> {
                filters.memberInvites
            }

            is TdApi.ChatEventMemberPromoted -> {
                filters.memberPromotions
            }

            is TdApi.ChatEventMemberRestricted -> {
                filters.memberRestrictions
            }

            is TdApi.ChatEventMemberTagChanged -> {
                filters.memberTagChanges
            }

            is TdApi.ChatEventTitleChanged, is TdApi.ChatEventDescriptionChanged, is TdApi.ChatEventPhotoChanged,
            is TdApi.ChatEventUsernameChanged,
            -> {
                filters.infoChanges
            }

            is TdApi.ChatEventInviteLinkEdited,
            is TdApi.ChatEventInviteLinkRevoked,
            is TdApi.ChatEventInviteLinkDeleted,
            -> {
                filters.inviteLinkChanges
            }

            else -> {
                filters.settingChanges
            }
        }

    /** A month of made-up statistics, the period's values against the month before, in Telegram's chart format. */
    private fun statistics(
        world: DemoWorld,
        chatId: Long,
    ): DemoAnswer {
        val id = DemoIds.supergroupId(chatId) ?: return badRequest("CHAT_ADMIN_REQUIRED")
        val group = world.groups.supergroups[id] ?: return notFound()
        val end = world.now - world.now % DAY
        val start = end - DAYS * DAY
        val text = world.data.text
        val members = DAYS_SERIES.map { group.memberCount - (DAYS - it) * 3 }
        val joined = DAYS_SERIES.map { 2 + it % 4 }
        val left = DAYS_SERIES.map { it % 3 / 2 }
        val membersGraph = graph(start, listOf(text(R.string.demo_stat_subscribers) to members), "line")
        val joinsGraph =
            graph(
                start,
                listOf(
                    text(R.string.demo_stat_joined) to joined,
                    text(R.string.demo_stat_left) to left,
                ),
                "bar",
            )
        if (group.isChannel) {
            return ok(
                TdApi.ChatStatisticsChannel().apply {
                    period = TdApi.DateRange(start, end)
                    memberCount = value(members.last().toDouble(), members.first().toDouble())
                    meanMessageViewCount = value(VIEWS, VIEWS * 0.9)
                    meanMessageShareCount = value(SHARES, SHARES)
                    meanMessageReactionCount = value(REACTIONS, REACTIONS * 0.8)
                    meanStoryViewCount = value(0.0, 0.0)
                    meanStoryShareCount = value(0.0, 0.0)
                    meanStoryReactionCount = value(0.0, 0.0)
                    enabledNotificationsPercentage = NOTIFICATIONS
                    memberCountGraph = membersGraph
                    joinGraph = joinsGraph
                    viewCountByHourGraph =
                        graph(start, listOf(text(R.string.demo_stat_views) to DAYS_SERIES.map { 40 + it * 2 }), "line")
                    recentInteractions = emptyArray()
                },
            )
        }
        return ok(
            TdApi.ChatStatisticsSupergroup().apply {
                period = TdApi.DateRange(start, end)
                memberCount = value(members.last().toDouble(), members.first().toDouble())
                messageCount = value(MESSAGES, MESSAGES * 0.7)
                viewerCount = value(group.memberCount.toDouble(), group.memberCount.toDouble())
                senderCount = value(group.memberCount.toDouble(), 2.0)
                memberCountGraph = membersGraph
                joinGraph = joinsGraph
                messageContentGraph =
                    graph(start, listOf(text(R.string.demo_stat_messages) to DAYS_SERIES.map { 5 + it % 7 }), "bar")
                topSenders = emptyArray()
                topAdministrators = emptyArray()
                topInviters = emptyArray()
            },
        )
    }

    private fun value(
        current: Double,
        previous: Double,
    ) = TdApi.StatisticalValue(
        current,
        previous,
        if (previous ==
            0.0
        ) {
            0.0
        } else {
            (current - previous) * PERCENT / previous
        },
    )

    private fun graph(
        start: Int,
        series: List<Pair<String, List<Int>>>,
        type: String,
    ): TdApi.StatisticalGraphData {
        val x = DAYS_SERIES.joinToString(",") { ((start + it * DAY).toLong() * MILLIS).toString() }
        val columns = series.mapIndexed { index, (_, values) -> "[\"y$index\",${values.joinToString(",")}]" }
        val types = series.indices.joinToString(",") { "\"y$it\":\"$type\"" }
        val names =
            series
                .mapIndexed {
                    index,
                    (name, _),
                    ->
                    "\"y$index\":\"${name.replace("\"", "\\\"")}\""
                }.joinToString(",")
        val json = "{\"columns\":[[\"x\",$x],${columns.joinToString(
            ",",
        )}],\"types\":{\"x\":\"x\",$types},\"names\":{$names}}"
        return TdApi.StatisticalGraphData(json, "")
    }

    private fun boostStatus(chatId: Long) =
        TdApi.ChatBoostStatus().apply {
            boostUrl = "https://t.me/boost/c/${DemoIds.supergroupId(chatId) ?: 0}"
            appliedSlotIds = intArrayOf()
            level = 1
            boostCount = BOOSTS
            currentLevelBoostCount = 1
            nextLevelBoostCount = NEXT_LEVEL
            premiumMemberCount = PREMIUM_MEMBERS
            premiumMemberPercentage = PREMIUM_MEMBERS * PERCENT / 1_284.0
            prepaidGiveaways = emptyArray()
        }

    private companion object {
        const val DAY = 86_400
        const val DAYS = 30
        val DAYS_SERIES = 0 until DAYS
        const val MILLIS = 1_000L
        const val PERCENT = 100.0
        const val VIEWS = 96.0
        const val SHARES = 4.0
        const val REACTIONS = 12.0
        const val MESSAGES = 140.0
        const val NOTIFICATIONS = 64.0
        const val BOOSTS = 3
        const val NEXT_LEVEL = 7
        const val PREMIUM_MEMBERS = 32
    }
}
