package dev.kyralabs.pingogram.core.demo

import org.drinkless.tdlib.TdApi

/**
 * The demo's groups and channels (`docs/adr/0034-demo-mode-for-store-review.md`): their info, members and settings,
 * which the user (their creator) changes; new groups and channels; upgrading the group to a supergroup; leaving,
 * deleting and clearing chats. On [DemoWorld.groups]; every change is recorded in the recent actions of a supergroup
 * or channel, as Telegram records it.
 *
 * One branch per function, each starting with guard clauses for chats that are not the demo's: hence the suppressions.
 */
@Suppress("CyclomaticComplexMethod", "LongMethod", "TooManyFunctions", "ReturnCount", "LargeClass")
internal class DemoGroups : DemoArea {
    override fun answer(
        world: DemoWorld,
        function: TdApi.Function<*>,
    ): DemoAnswer? {
        val groups = world.groups
        return when (function) {
            is TdApi.GetBasicGroup -> {
                groups.basicGroups[function.basicGroupId]?.let(::ok) ?: notFound()
            }

            is TdApi.GetSupergroup -> {
                groups.supergroups[function.supergroupId]?.let(::ok) ?: notFound()
            }

            is TdApi.GetBasicGroupFullInfo -> {
                groups.basicFullInfo[function.basicGroupId]?.let(::ok) ?: notFound()
            }

            is TdApi.GetSupergroupFullInfo -> {
                groups.supergroupFullInfo[function.supergroupId]?.let(::ok) ?: notFound()
            }

            is TdApi.GetSupergroupMembers -> {
                supergroupMembers(world, function)
            }

            is TdApi.SearchChatMembers -> {
                searchMembers(world, function)
            }

            is TdApi.GetChatMember -> {
                member(world, function.chatId, function.memberId)
            }

            is TdApi.SetChatMemberStatus -> {
                setStatus(world, function.chatId, function.memberId, function.status)
            }

            is TdApi.AddChatMembers -> {
                addMembers(world, function.chatId, function.userIds.toList())
            }

            is TdApi.SetChatTitle -> {
                setTitle(world, function.chatId, function.title)
            }

            is TdApi.SetChatDescription -> {
                setDescription(world, function.chatId, function.description)
            }

            is TdApi.SetChatPhoto -> {
                setPhoto(world, function.chatId, function.photo)
            }

            is TdApi.SetChatPermissions -> {
                setPermissions(world, function.chatId, function.permissions)
            }

            is TdApi.SetChatSlowModeDelay -> {
                setSlowMode(world, function.chatId, function.slowModeDelay)
            }

            is TdApi.SetChatAvailableReactions -> {
                setReactions(world, function.chatId, function.availableReactions)
            }

            is TdApi.ToggleChatHasProtectedContent -> {
                setProtected(world, function.chatId, function.hasProtectedContent)
            }

            is TdApi.ToggleSupergroupSignMessages -> {
                setSignatures(world, function)
            }

            is TdApi.SetChatMessageAutoDeleteTime -> {
                setAutoDelete(
                    world,
                    function.chatId,
                    function.messageAutoDeleteTime,
                )
            }

            is TdApi.GetChatAvailableMessageSenders -> {
                senders(world, function.chatId)
            }

            is TdApi.SetChatMessageSender -> {
                ok(TdApi.Ok())
            }

            // The demo's groups are basic groups or channels, which cannot be a channel's discussion group.
            is TdApi.GetSuitableDiscussionChats -> {
                ok(TdApi.Chats(0, longArrayOf()))
            }

            is TdApi.CheckChatUsername -> {
                ok(checkUsername(function.username))
            }

            is TdApi.SetSupergroupUsername -> {
                setUsername(world, function.supergroupId, function.username)
            }

            is TdApi.CreateNewBasicGroupChat -> {
                createGroup(world, function)
            }

            is TdApi.CreateNewSupergroupChat -> {
                createSupergroup(world, function)
            }

            is TdApi.UpgradeBasicGroupChatToSupergroupChat -> {
                upgrade(world, function.chatId)
            }

            is TdApi.DeleteChat -> {
                okChanged(world.removeChat(function.chatId))
            }

            is TdApi.LeaveChat -> {
                okChanged(world.removeChat(function.chatId))
            }

            is TdApi.DeleteChatHistory -> {
                deleteHistory(world, function)
            }

            // The demo has no 2-step verification password, which Telegram asks for before a transfer.
            is TdApi.CanTransferOwnership -> {
                ok(TdApi.CanTransferOwnershipResultPasswordNeeded())
            }

            else -> {
                null
            }
        }
    }

    private fun supergroupMembers(
        world: DemoWorld,
        function: TdApi.GetSupergroupMembers,
    ): DemoAnswer {
        val chatId = DemoIds.supergroupChat(function.supergroupId)
        val all = world.groups.membersOf(chatId)
        val filtered =
            when (val filter = function.filter) {
                is TdApi.SupergroupMembersFilterAdministrators -> {
                    all.filter { it.status.isAdmin() }
                }

                is TdApi.SupergroupMembersFilterRestricted -> {
                    all.filter { it.status is TdApi.ChatMemberStatusRestricted }
                }

                is TdApi.SupergroupMembersFilterBanned -> {
                    all.filter { it.status is TdApi.ChatMemberStatusBanned }
                }

                is TdApi.SupergroupMembersFilterSearch -> {
                    present(all).filter { matches(world, it, filter.query) }
                }

                is TdApi.SupergroupMembersFilterContacts -> {
                    present(all).filter {
                        world.users[userOf(it)]?.isContact ==
                            true
                    }
                }

                is TdApi.SupergroupMembersFilterBots -> {
                    emptyList()
                }

                else -> {
                    present(all)
                }
            }
        return ok(members(filtered.drop(function.offset).take(function.limit), filtered.size))
    }

    private fun searchMembers(
        world: DemoWorld,
        function: TdApi.SearchChatMembers,
    ): DemoAnswer {
        val all = world.groups.membersOf(function.chatId)
        val filtered =
            when (function.filter) {
                is TdApi.ChatMembersFilterAdministrators -> all.filter { it.status.isAdmin() }
                is TdApi.ChatMembersFilterRestricted -> all.filter { it.status is TdApi.ChatMemberStatusRestricted }
                is TdApi.ChatMembersFilterBanned -> all.filter { it.status is TdApi.ChatMemberStatusBanned }
                is TdApi.ChatMembersFilterBots -> emptyList()
                else -> present(all)
            }.filter { matches(world, it, function.query) }
        return ok(members(filtered.take(function.limit), filtered.size))
    }

    private fun member(
        world: DemoWorld,
        chatId: Long,
        memberId: TdApi.MessageSender,
    ): DemoAnswer {
        val userId = (memberId as? TdApi.MessageSenderUser)?.userId ?: return notFound()
        val found = world.groups.membersOf(chatId).firstOrNull { userOf(it) == userId }
        return ok(found ?: TdApi.ChatMember(memberId, "", 0, 0, TdApi.ChatMemberStatusLeft()))
    }

    private fun setStatus(
        world: DemoWorld,
        chatId: Long,
        memberId: TdApi.MessageSender,
        status: TdApi.ChatMemberStatus,
    ): DemoAnswer {
        val userId = (memberId as? TdApi.MessageSenderUser)?.userId ?: return needsAccount()
        if (userId == DemoData.ME || world.chat(chatId) == null) return badRequest("CHAT_ADMIN_REQUIRED")
        val list = world.groups.membersOf(chatId).toMutableList()
        val index = list.indexOfFirst { userOf(it) == userId }
        val old = list.getOrNull(index)?.status ?: TdApi.ChatMemberStatusLeft()
        val member =
            TdApi.ChatMember(
                memberId,
                "",
                DemoData.ME,
                list.getOrNull(index)?.joinedChatDate ?: world.now,
                status,
            )
        if (index >= 0) list[index] = member else list += member
        val action =
            when {
                status.isAdmin() || old.isAdmin() -> TdApi.ChatEventMemberPromoted(userId, old, status)
                else -> TdApi.ChatEventMemberRestricted(memberId, old, status)
            }
        world.groups.record(chatId, action)
        return okChanged(world.groups.setMembers(chatId, list))
    }

    private fun addMembers(
        world: DemoWorld,
        chatId: Long,
        userIds: List<Long>,
    ): DemoAnswer {
        if (world.chat(chatId) == null) return notFound()
        val list = world.groups.membersOf(chatId).toMutableList()
        userIds
            .filter { id ->
                world.users.containsKey(id) && list.none { userOf(it) == id && it.status.isPresent() }
            }.forEach { id ->
                list.removeAll { userOf(it) == id }
                list +=
                    TdApi.ChatMember(
                        TdApi.MessageSenderUser(id),
                        "",
                        DemoData.ME,
                        world.now,
                        TdApi.ChatMemberStatusMember(0),
                    )
                world.groups.record(chatId, TdApi.ChatEventMemberInvited(id, TdApi.ChatMemberStatusMember(0)))
            }
        return changed(TdApi.FailedToAddMembers(emptyArray()), world.groups.setMembers(chatId, list))
    }

    private fun setTitle(
        world: DemoWorld,
        chatId: Long,
        title: String,
    ): DemoAnswer {
        val chat = world.chat(chatId) ?: return notFound()
        if (title.isBlank()) return badRequest("CHAT_TITLE_EMPTY")
        val old = chat.title
        chat.title = title.trim()
        world.groups.record(chatId, TdApi.ChatEventTitleChanged(old, chat.title))
        return okChanged(listOf(TdApi.UpdateChatTitle(chatId, chat.title)))
    }

    private fun setDescription(
        world: DemoWorld,
        chatId: Long,
        description: String,
    ): DemoAnswer {
        val groups = world.groups
        DemoIds.basicGroupId(chatId)?.let { id ->
            val info = groups.basicFullInfo[id] ?: return notFound()
            info.description = description
            return okChanged(listOf(TdApi.UpdateBasicGroupFullInfo(id, info)))
        }
        val id = DemoIds.supergroupId(chatId) ?: return notFound()
        val info = groups.supergroupFullInfo[id] ?: return notFound()
        val old = info.description
        info.description = description
        groups.record(chatId, TdApi.ChatEventDescriptionChanged(old, description))
        return okChanged(listOf(TdApi.UpdateSupergroupFullInfo(id, info)))
    }

    private fun setPhoto(
        world: DemoWorld,
        chatId: Long,
        input: TdApi.InputChatPhoto?,
    ): DemoAnswer {
        val chat = world.chat(chatId) ?: return notFound()
        val path = ((input as? TdApi.InputChatPhotoStatic)?.photo as? TdApi.InputFileLocal)?.path
        if (input != null && path == null) return needsAccount()
        chat.photo =
            path?.let {
                val file = world.sentFile(it)
                TdApi.ChatPhotoInfo(file, file, null, false, false)
            }
        world.groups.record(chatId, TdApi.ChatEventPhotoChanged(null, null))
        return okChanged(listOf(TdApi.UpdateChatPhoto(chatId, chat.photo)))
    }

    private fun setPermissions(
        world: DemoWorld,
        chatId: Long,
        permissions: TdApi.ChatPermissions,
    ): DemoAnswer {
        val chat = world.chat(chatId) ?: return notFound()
        val old = chat.permissions
        chat.permissions = permissions
        world.groups.record(chatId, TdApi.ChatEventPermissionsChanged(old, permissions))
        return okChanged(listOf(TdApi.UpdateChatPermissions(chatId, permissions)))
    }

    private fun setSlowMode(
        world: DemoWorld,
        chatId: Long,
        delay: Int,
    ): DemoAnswer {
        val id = DemoIds.supergroupId(chatId) ?: return badRequest("CHAT_NOT_MODIFIED")
        val group = world.groups.supergroups[id] ?: return notFound()
        val info = world.groups.supergroupFullInfo.getValue(id)
        val old = info.slowModeDelay
        info.slowModeDelay = delay
        group.isSlowModeEnabled = delay > 0
        world.groups.record(chatId, TdApi.ChatEventSlowModeDelayChanged(old, delay))
        return okChanged(listOf(TdApi.UpdateSupergroup(group), TdApi.UpdateSupergroupFullInfo(id, info)))
    }

    private fun setReactions(
        world: DemoWorld,
        chatId: Long,
        reactions: TdApi.ChatAvailableReactions,
    ): DemoAnswer {
        val chat = world.chat(chatId) ?: return notFound()
        val old = chat.availableReactions
        chat.availableReactions = reactions
        world.groups.record(chatId, TdApi.ChatEventAvailableReactionsChanged(old, reactions))
        return okChanged(listOf(TdApi.UpdateChatAvailableReactions(chatId, reactions)))
    }

    private fun setProtected(
        world: DemoWorld,
        chatId: Long,
        value: Boolean,
    ): DemoAnswer {
        val chat = world.chat(chatId) ?: return notFound()
        chat.hasProtectedContent = value
        world.groups.record(chatId, TdApi.ChatEventHasProtectedContentToggled(value))
        return okChanged(listOf(TdApi.UpdateChatHasProtectedContent(chatId, value)))
    }

    private fun setSignatures(
        world: DemoWorld,
        function: TdApi.ToggleSupergroupSignMessages,
    ): DemoAnswer {
        val group = world.groups.supergroups[function.supergroupId] ?: return notFound()
        group.signMessages = function.signMessages
        group.showMessageSender = function.signMessages && function.showMessageSender
        world.groups.record(DemoIds.supergroupChat(group.id), TdApi.ChatEventSignMessagesToggled(function.signMessages))
        return okChanged(listOf(TdApi.UpdateSupergroup(group)))
    }

    private fun setAutoDelete(
        world: DemoWorld,
        chatId: Long,
        seconds: Int,
    ): DemoAnswer {
        val chat = world.chat(chatId) ?: return notFound()
        val old = chat.messageAutoDeleteTime
        chat.messageAutoDeleteTime = seconds
        world.groups.record(chatId, TdApi.ChatEventMessageAutoDeleteTimeChanged(old, seconds))
        return okChanged(listOf(TdApi.UpdateChatMessageAutoDeleteTime(chatId, seconds)))
    }

    private fun senders(
        world: DemoWorld,
        chatId: Long,
    ): DemoAnswer {
        world.chat(chatId) ?: return notFound()
        return ok(
            TdApi.ChatMessageSenders(arrayOf(TdApi.ChatMessageSender(TdApi.MessageSenderUser(DemoData.ME), false))),
        )
    }

    private fun checkUsername(username: String): TdApi.CheckChatUsernameResult =
        when {
            !USERNAME.matches(username) -> TdApi.CheckChatUsernameResultUsernameInvalid()
            else -> TdApi.CheckChatUsernameResultOk()
        }

    private fun setUsername(
        world: DemoWorld,
        supergroupId: Long,
        username: String,
    ): DemoAnswer {
        val group = world.groups.supergroups[supergroupId] ?: return notFound()
        if (username.isNotEmpty() && !USERNAME.matches(username)) return badRequest("USERNAME_INVALID")
        val old = group.usernames?.editableUsername.orEmpty()
        val active = if (username.isEmpty()) emptyArray() else arrayOf(username)
        group.usernames = TdApi.Usernames(active, emptyArray(), username, emptyArray(), "")
        world.groups.record(DemoIds.supergroupChat(supergroupId), TdApi.ChatEventUsernameChanged(old, username))
        return okChanged(listOf(TdApi.UpdateSupergroup(group)))
    }

    private fun createGroup(
        world: DemoWorld,
        function: TdApi.CreateNewBasicGroupChat,
    ): DemoAnswer {
        if (function.title.isBlank()) return badRequest("CHAT_TITLE_EMPTY")
        val groups = world.groups
        val id = groups.nextBasicGroupId()
        val chatId = DemoIds.basicGroupChat(id)
        val userIds = function.userIds.toList().filter { world.users.containsKey(it) && it != DemoData.ME }
        val members =
            (listOf(DemoData.ME) + userIds).map { user ->
                val status: TdApi.ChatMemberStatus =
                    if (user ==
                        DemoData.ME
                    ) {
                        TdApi.ChatMemberStatusCreator(false, true)
                    } else {
                        TdApi.ChatMemberStatusMember(0)
                    }
                TdApi.ChatMember(TdApi.MessageSenderUser(user), "", DemoData.ME, world.now, status)
            }
        val group = TdApi.BasicGroup(id, members.size, TdApi.ChatMemberStatusCreator(false, true), true, 0)
        groups.basicGroups[id] = group
        groups.basicFullInfo[id] =
            TdApi.BasicGroupFullInfo(
                null,
                "",
                DemoData.ME,
                members.toTypedArray(),
                false,
                false,
                groups.link(primary = true),
                emptyArray(),
            )
        val chat = newChat(chatId, TdApi.ChatTypeBasicGroup(id), function.title.trim(), canSend = true)
        chat.messageAutoDeleteTime = function.messageAutoDeleteTime
        val updates =
            listOf(
                TdApi.UpdateBasicGroup(group),
                TdApi.UpdateBasicGroupFullInfo(id, groups.basicFullInfo.getValue(id)),
            ) +
                world.addChat(
                    chat,
                    listOf(TdApi.MessageBasicGroupChatCreate(chat.title, members.map { userOf(it) }.toLongArray())),
                )
        return changed(TdApi.CreatedBasicGroupChat(chatId, TdApi.FailedToAddMembers(emptyArray())), updates)
    }

    private fun createSupergroup(
        world: DemoWorld,
        function: TdApi.CreateNewSupergroupChat,
    ): DemoAnswer {
        if (function.title.isBlank()) return badRequest("CHAT_TITLE_EMPTY")
        val groups = world.groups
        val id = groups.nextSupergroupId()
        val chatId = DemoIds.supergroupChat(id)
        val group =
            TdApi.Supergroup().apply {
                this.id = id
                usernames = TdApi.Usernames(emptyArray(), emptyArray(), "", emptyArray(), "")
                date = world.now
                status = TdApi.ChatMemberStatusCreator(false, true)
                memberCount = 1
                isChannel = function.isChannel
                isForum = function.isForum
            }
        groups.supergroups[id] = group
        val primary = groups.link(primary = true)
        groups.supergroupFullInfo[id] =
            TdApi.SupergroupFullInfo().apply {
                description = function.description.orEmpty()
                memberCount = 1
                administratorCount = 1
                canGetMembers = true
                isAllHistoryAvailable = true
                inviteLink = primary
                botCommands = emptyArray()
            }
        groups.links[chatId] = mutableListOf(primary)
        groups.members[chatId] =
            mutableListOf(
                TdApi.ChatMember(
                    TdApi.MessageSenderUser(DemoData.ME),
                    "",
                    0,
                    world.now,
                    TdApi.ChatMemberStatusCreator(false, true),
                ),
            )
        val chat =
            newChat(
                chatId,
                TdApi.ChatTypeSupergroup(id, function.isChannel),
                function.title.trim(),
                canSend = !function.isChannel,
            )
        chat.messageAutoDeleteTime = function.messageAutoDeleteTime
        val updates =
            listOf(
                TdApi.UpdateSupergroup(group),
                TdApi.UpdateSupergroupFullInfo(id, groups.supergroupFullInfo.getValue(id)),
            ) +
                world.addChat(chat, listOf(TdApi.MessageSupergroupChatCreate(chat.title)))
        return changed(chat, updates)
    }

    /** The group becomes a supergroup with its members and messages; the basic group leaves the chat list. */
    private fun upgrade(
        world: DemoWorld,
        chatId: Long,
    ): DemoAnswer {
        val groups = world.groups
        val basicId = DemoIds.basicGroupId(chatId) ?: return badRequest("CHAT_INVALID")
        val old = world.chat(chatId) ?: return notFound()
        val info = groups.basicFullInfo[basicId] ?: return notFound()
        val id = groups.nextSupergroupId()
        val newChatId = DemoIds.supergroupChat(id)
        val group =
            TdApi.Supergroup().apply {
                this.id = id
                usernames = TdApi.Usernames(emptyArray(), emptyArray(), "", emptyArray(), "")
                date = world.now
                status = TdApi.ChatMemberStatusCreator(false, true)
                memberCount = info.members.size
            }
        groups.supergroups[id] = group
        groups.supergroupFullInfo[id] =
            TdApi.SupergroupFullInfo().apply {
                description = info.description
                memberCount = info.members.size
                administratorCount = info.members.count { it.status.isAdmin() }
                canGetMembers = true
                isAllHistoryAvailable = true
                inviteLink = info.inviteLink
                upgradedFromBasicGroupId = basicId
                botCommands = emptyArray()
            }
        groups.members[newChatId] = info.members.toMutableList()
        groups.links[newChatId] = groups.links[chatId] ?: mutableListOf()
        groups.basicGroups.getValue(basicId).apply {
            isActive = false
            upgradedToSupergroupId = id
        }
        val chat = newChat(newChatId, TdApi.ChatTypeSupergroup(id, false), old.title, canSend = true)
        chat.permissions = old.permissions
        chat.photo = old.photo
        val updates = mutableListOf<TdApi.Update>()
        updates += TdApi.UpdateSupergroup(group)
        updates += TdApi.UpdateSupergroupFullInfo(id, groups.supergroupFullInfo.getValue(id))
        updates += TdApi.UpdateBasicGroup(groups.basicGroups.getValue(basicId))
        updates += world.addChat(chat)
        world.moveHistory(chatId, newChatId)
        updates += world.removeChat(chatId)
        updates += TdApi.UpdateChatLastMessage(newChatId, chat.lastMessage, chat.positions)
        return changed(chat, updates)
    }

    private fun deleteHistory(
        world: DemoWorld,
        function: TdApi.DeleteChatHistory,
    ): DemoAnswer =
        if (function.removeFromChatList) {
            okChanged(world.removeChat(function.chatId))
        } else {
            okChanged(world.clearHistory(function.chatId))
        }

    private fun newChat(
        id: Long,
        type: TdApi.ChatType,
        title: String,
        canSend: Boolean,
    ) = TdApi.Chat().apply {
        this.id = id
        this.type = type
        this.title = title
        permissions =
            TdApi.ChatPermissions().apply {
                canSendBasicMessages = canSend
                canSendAudios = canSend
                canSendDocuments = canSend
                canSendPhotos = canSend
                canSendVideos = canSend
                canSendVideoNotes = canSend
                canSendVoiceNotes = canSend
                canSendPolls = canSend
                canSendOtherMessages = canSend
                canAddLinkPreviews = canSend
                canReactToMessages = true
                canInviteUsers = true
            }
        notificationSettings = DemoNotifications.chatSettings(muteFor = 0)
        canBeDeletedOnlyForSelf = true
        canBeDeletedForAllUsers = true
        accentColorId = (id % ACCENT_COLORS).toInt().let { if (it < 0) -it else it }
        profileAccentColorId = -1
        clientData = ""
    }

    private fun present(list: List<TdApi.ChatMember>) = list.filter { it.status.isPresent() }

    private fun members(
        list: List<TdApi.ChatMember>,
        total: Int,
    ) = TdApi.ChatMembers(total, list.toTypedArray())

    private fun matches(
        world: DemoWorld,
        member: TdApi.ChatMember,
        query: String,
    ): Boolean {
        val user = world.users[userOf(member)] ?: return false
        return query.isBlank() || "${user.firstName} ${user.lastName}".contains(query.trim(), ignoreCase = true)
    }

    private fun userOf(member: TdApi.ChatMember) = (member.memberId as? TdApi.MessageSenderUser)?.userId ?: 0

    private fun TdApi.ChatMemberStatus.isAdmin() =
        this is TdApi.ChatMemberStatusAdministrator || this is TdApi.ChatMemberStatusCreator

    private fun TdApi.ChatMemberStatus.isPresent() =
        this !is TdApi.ChatMemberStatusBanned && this !is TdApi.ChatMemberStatusLeft &&
            (this as? TdApi.ChatMemberStatusRestricted)?.isMember != false

    private companion object {
        val USERNAME = Regex("[A-Za-z][A-Za-z0-9_]{4,31}")
        const val ACCENT_COLORS = 7
    }
}
