package dev.kyralabs.pingogram.core.demo

import org.drinkless.tdlib.TdApi

/**
 * The demo's chat folders (`docs/adr/0034-demo-mode-for-store-review.md`): "Work" and the folders the user makes,
 * edits, reorders and deletes, two of Telegram's suggestions, and folder invite links (made up, opening nothing
 * outside the demo). A folder's chats are its rules applied to the demo's chats, as TDLib applies them: each change
 * moves chats in and out of the folder's list with their positions.
 *
 * One branch per function: hence the suppressions.
 */
@Suppress("CyclomaticComplexMethod", "TooManyFunctions", "ReturnCount")
internal class DemoFolders : DemoArea {
    private val folders = linkedMapOf<Int, TdApi.ChatFolder>()
    private val links = mutableMapOf<Int, MutableList<TdApi.ChatFolderInviteLink>>()
    private var mainListPosition = 0
    private var nextId = DemoData.WORK_FOLDER + 1
    private var nextLink = 1
    private var seeded = false

    override fun answer(
        world: DemoWorld,
        function: TdApi.Function<*>,
    ): DemoAnswer? {
        seed(world)
        return when (function) {
            is TdApi.GetChatFolder -> {
                folders[function.chatFolderId]?.let(::ok) ?: notFound()
            }

            is TdApi.CreateChatFolder -> {
                create(world, function.folder)
            }

            is TdApi.EditChatFolder -> {
                edit(world, function.chatFolderId, function.folder)
            }

            is TdApi.DeleteChatFolder -> {
                delete(world, function.chatFolderId)
            }

            is TdApi.ReorderChatFolders -> {
                reorder(function.chatFolderIds.toList(), function.mainChatListPosition)
            }

            is TdApi.GetRecommendedChatFolders -> {
                ok(recommended(world))
            }

            is TdApi.GetChatFolderChatsToLeave -> {
                ok(TdApi.Chats(0, longArrayOf()))
            }

            is TdApi.GetChatFolderInviteLinks -> {
                ok(
                    TdApi.ChatFolderInviteLinks(links[function.chatFolderId].orEmpty().toTypedArray()),
                )
            }

            is TdApi.CreateChatFolderInviteLink -> {
                createLink(function)
            }

            is TdApi.DeleteChatFolderInviteLink -> {
                deleteLink(function.chatFolderId, function.inviteLink)
            }

            // The groups and channels a folder holds, which its link may share.
            is TdApi.GetChatsForChatFolderInviteLink -> {
                val folder = folders[function.chatFolderId]
                if (folder == null) {
                    notFound()
                } else {
                    val shareable =
                        world.allChats().filter { chat ->
                            chat.type !is TdApi.ChatTypePrivate && chat.type !is TdApi.ChatTypeSecret &&
                                holds(world, folder, chat)
                        }
                    ok(TdApi.Chats(shareable.size, shareable.map { it.id }.toLongArray()))
                }
            }

            is TdApi.CheckChatFolderInviteLink -> {
                checkInvite(world, function.inviteLink)
            }

            is TdApi.AddChatFolderByInviteLink -> {
                addByInvite(world, function.inviteLink, function.chatIds.toList())
            }

            else -> {
                null
            }
        }
    }

    /** The folders as TDLib announces them after it starts: "Work" and what the user made, in their order. */
    fun announce(world: DemoWorld): TdApi.Update {
        seed(world)
        return foldersUpdate()
    }

    /** "Work" once, so a user who deleted it does not get it back. */
    private fun seed(world: DemoWorld) {
        if (seeded) return
        seeded = true
        folders[DemoData.WORK_FOLDER] = world.data.folderSettings
    }

    private fun create(
        world: DemoWorld,
        folder: TdApi.ChatFolder,
    ): DemoAnswer {
        if (!includesSomething(folder)) return badRequest("FILTER_INCLUDE_EMPTY")
        val id = nextId++
        folders[id] = folder
        val updates = world.placeInFolder(id) { chat -> holds(world, folder, chat) } + foldersUpdate()
        return changed(info(id, folder), updates)
    }

    private fun edit(
        world: DemoWorld,
        id: Int,
        folder: TdApi.ChatFolder,
    ): DemoAnswer {
        if (!folders.containsKey(id)) return notFound()
        if (!includesSomething(folder)) return badRequest("FILTER_INCLUDE_EMPTY")
        folders[id] = folder
        val updates = world.placeInFolder(id) { chat -> holds(world, folder, chat) } + foldersUpdate()
        return changed(info(id, folder), updates)
    }

    private fun delete(
        world: DemoWorld,
        id: Int,
    ): DemoAnswer {
        folders.remove(id) ?: return notFound()
        links.remove(id)
        return okChanged(world.placeInFolder(id) { false } + foldersUpdate())
    }

    private fun reorder(
        ids: List<Int>,
        mainPosition: Int,
    ): DemoAnswer {
        val ordered = ids.filter(folders::containsKey) + folders.keys.filter { it !in ids }
        val copy = ordered.associateWith { folders.getValue(it) }
        folders.clear()
        folders.putAll(copy)
        mainListPosition = mainPosition.coerceIn(0, folders.size)
        return okChanged(listOf(foldersUpdate()))
    }

    private fun createLink(function: TdApi.CreateChatFolderInviteLink): DemoAnswer {
        val folder = folders[function.chatFolderId] ?: return notFound()
        val link =
            TdApi.ChatFolderInviteLink(
                "https://t.me/addlist/demo${nextLink++}",
                function.name.orEmpty(),
                function.chatIds,
            )
        links.getOrPut(function.chatFolderId) { mutableListOf() }.add(link)
        folder.isShareable = true
        return changed(link, listOf(foldersUpdate()))
    }

    private fun deleteLink(
        id: Int,
        inviteLink: String,
    ): DemoAnswer {
        links[id]?.removeAll { it.inviteLink == inviteLink }
        return okChanged(listOf(foldersUpdate()))
    }

    /**
     * A shared folder's link: Ben's sample "Hobbies" (the forum, which the user is in, and the photo walks group and
     * the news channel, which they are not), or one of the user's own folders' links.
     */
    private fun checkInvite(
        world: DemoWorld,
        link: String,
    ): DemoAnswer {
        val slug = slugOf(link)
        if (slug == slugOf(DemoData.HOBBIES_LINK)) {
            val chats = hobbyChats(world)
            val (added, missing) = chats.partition { world.groups.statusOf(it) !is TdApi.ChatMemberStatusLeft }
            val existing = folders.entries.firstOrNull { it.value.name.text.text == hobbiesName(world) }
            val info =
                existing?.let { info(it.key, it.value) }
                    ?: TdApi.ChatFolderInfo(
                        0,
                        hobbiesFolder(world).name,
                        TdApi.ChatFolderIcon("Favorite"),
                        -1,
                        true,
                        false,
                    )
            return ok(
                TdApi.ChatFolderInviteLinkInfo(
                    info,
                    missing.toLongArray(),
                    added.toLongArray(),
                ),
            )
        }
        val own = links.entries.firstOrNull { (_, list) -> list.any { slugOf(it.inviteLink) == slug } }
        val folder = own?.let { folders[it.key] } ?: return badRequest("INVITE_SLUG_INVALID")
        return ok(TdApi.ChatFolderInviteLinkInfo(info(own.key, folder), longArrayOf(), folder.includedChatIds))
    }

    private fun addByInvite(
        world: DemoWorld,
        link: String,
        chatIds: List<Long>,
    ): DemoAnswer {
        if (slugOf(link) != slugOf(DemoData.HOBBIES_LINK)) return badRequest("INVITE_SLUG_INVALID")
        val chats = hobbyChats(world)
        val updates = mutableListOf<TdApi.Update>()
        chatIds.filter { it in chats }.forEach { updates += world.join(it) }
        val existing = folders.entries.firstOrNull { it.value.name.text.text == hobbiesName(world) }
        val id = existing?.key ?: nextId++
        val folder =
            (existing?.value ?: hobbiesFolder(world)).apply {
                includedChatIds =
                    chats.filter { world.groups.statusOf(it) !is TdApi.ChatMemberStatusLeft }.toLongArray()
            }
        folders[id] = folder
        updates += world.placeInFolder(id) { chat -> holds(world, folder, chat) }
        updates += foldersUpdate()
        return okChanged(updates)
    }

    private fun hobbyChats(world: DemoWorld) =
        listOf(DemoData.FORUM_CHAT, DemoData.PHOTO_WALKS_CHAT, DemoData.NEWS_CHAT).filter { world.chat(it) != null }

    private fun hobbiesName(world: DemoWorld) = world.data.text(R.string.demo_folder_hobbies)

    private fun hobbiesFolder(world: DemoWorld) =
        emptyFolder(hobbiesName(world), "Favorite").apply { isShareable = true }

    private fun slugOf(link: String) =
        link
            .trim()
            .substringAfter("addlist/")
            .substringBefore('?')
            .trimEnd('/')

    /** Two of Telegram's usual suggestions: unread chats, and people. */
    private fun recommended(world: DemoWorld): TdApi.RecommendedChatFolders {
        val text = world.data.text
        val unread =
            emptyFolder(text(R.string.demo_folder_unread), "Unread").apply {
                includeContacts = true
                includeNonContacts = true
                includeGroups = true
                includeChannels = true
                includeBots = true
                excludeRead = true
            }
        val personal =
            emptyFolder(text(R.string.demo_folder_personal), "Private").apply {
                includeContacts = true
                includeNonContacts = true
            }
        return TdApi.RecommendedChatFolders(
            arrayOf(
                TdApi.RecommendedChatFolder(unread, text(R.string.demo_folder_unread_description)),
                TdApi.RecommendedChatFolder(personal, text(R.string.demo_folder_personal_description)),
            ),
        )
    }

    private fun emptyFolder(
        name: String,
        icon: String,
    ) = TdApi.ChatFolder().apply {
        this.name = TdApi.ChatFolderName(TdApi.FormattedText(name, emptyArray()), false)
        this.icon = TdApi.ChatFolderIcon(icon)
        colorId = -1
        pinnedChatIds = longArrayOf()
        includedChatIds = longArrayOf()
        excludedChatIds = longArrayOf()
    }

    private fun foldersUpdate(): TdApi.Update =
        TdApi.UpdateChatFolders(
            folders
                .map { (id, folder) ->
                    info(id, folder)
                }.toTypedArray(),
            mainListPosition,
            false,
        )

    private fun info(
        id: Int,
        folder: TdApi.ChatFolder,
    ) = TdApi.ChatFolderInfo(
        id,
        folder.name,
        folder.icon,
        folder.colorId,
        folder.isShareable,
        links[id].orEmpty().isNotEmpty(),
    )

    private fun includesSomething(folder: TdApi.ChatFolder) =
        folder.includedChatIds.isNotEmpty() || folder.pinnedChatIds.isNotEmpty() ||
            folder.includeContacts || folder.includeNonContacts || folder.includeGroups || folder.includeChannels ||
            folder.includeBots

    /** Whether a folder holds a chat, as TDLib applies a folder's rules. */
    private fun holds(
        world: DemoWorld,
        folder: TdApi.ChatFolder,
        chat: TdApi.Chat,
    ): Boolean {
        if (chat.positions.none { it.list is TdApi.ChatListMain || it.list is TdApi.ChatListArchive }) return false
        if (chat.id in folder.excludedChatIds) return false
        if (chat.id in folder.includedChatIds || chat.id in folder.pinnedChatIds) return true
        val archived = chat.positions.any { it.list is TdApi.ChatListArchive }
        if (folder.excludeArchived && archived) return false
        if (folder.excludeMuted && chat.notificationSettings.muteFor > 0) return false
        if (folder.excludeRead && chat.unreadCount == 0 && !chat.isMarkedAsUnread) return false
        return when (val type = chat.type) {
            is TdApi.ChatTypePrivate, is TdApi.ChatTypeSecret -> {
                val userId = (type as? TdApi.ChatTypePrivate)?.userId ?: (type as TdApi.ChatTypeSecret).userId
                val user = world.users[userId]
                when {
                    user?.type is TdApi.UserTypeBot -> folder.includeBots
                    user?.isContact == true -> folder.includeContacts
                    else -> folder.includeNonContacts
                }
            }

            is TdApi.ChatTypeBasicGroup -> {
                folder.includeGroups
            }

            is TdApi.ChatTypeSupergroup -> {
                if (type.isChannel) folder.includeChannels else folder.includeGroups
            }

            else -> {
                false
            }
        }
    }
}
