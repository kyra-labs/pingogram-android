package dev.kyralabs.pingogram.core.demo

import org.drinkless.tdlib.TdApi

/**
 * The demo's chats and people (`docs/adr/0034-demo-mode-for-store-review.md`): options, users and contacts, the chat
 * lists and the folder, the group and the channel, and the changes to a chat in the list (pinning, marking unread,
 * muting, archiving), as TDLib answers them.
 *
 * One branch per function: hence the suppression.
 */
@Suppress("CyclomaticComplexMethod", "LongMethod")
internal class DemoChats : DemoArea {
    override fun answer(
        world: DemoWorld,
        function: TdApi.Function<*>,
    ): DemoAnswer? =
        when (function) {
            is TdApi.GetOption -> {
                ok(option(function.name))
            }

            // What TDLib takes without an answer to show: the demo has nothing to do for them.
            is TdApi.SetOption, is TdApi.OpenChat, is TdApi.CloseChat, is TdApi.SetChatDraftMessage,
            is TdApi.ReadAllChatMentions, is TdApi.ReadAllChatReactions, is TdApi.CancelDownloadFile,
            is TdApi.SetNetworkType,
            -> {
                ok(TdApi.Ok())
            }

            is TdApi.GetMe -> {
                ok(world.users.getValue(DemoData.ME))
            }

            is TdApi.GetUser -> {
                world.users[function.userId]?.let(::ok) ?: notFound()
            }

            is TdApi.GetUserFullInfo -> {
                ok(world.data.userFullInfo(function.userId))
            }

            is TdApi.GetContacts -> {
                ok(users(world.users.values.filter { it.isContact }))
            }

            is TdApi.SearchContacts -> {
                ok(
                    users(
                        world.users.values
                            .filter { it.isContact && it.matches(function.query) }
                            .take(function.limit),
                    ),
                )
            }

            is TdApi.GetChat -> {
                world.chat(function.chatId)?.let(::ok) ?: notFound()
            }

            is TdApi.CreatePrivateChat -> {
                world.chat(function.userId)?.let(::ok) ?: needsAccount()
            }

            is TdApi.GetChats -> {
                ok(
                    TdApi.Chats(
                        0,
                        world
                            .chatsIn(function.chatList)
                            .take(function.limit)
                            .map { it.id }
                            .toLongArray(),
                    ),
                )
            }

            // Every chat was announced when the demo started: there is nothing more to load.
            is TdApi.LoadChats, is TdApi.LoadSavedMessagesTopics, is TdApi.LoadActiveStories -> {
                notFound()
            }

            is TdApi.GetChatFolder -> {
                if (function.chatFolderId == DemoData.WORK_FOLDER) ok(world.data.folderSettings) else notFound()
            }

            is TdApi.GetBasicGroup -> {
                ok(world.data.group)
            }

            is TdApi.GetSupergroup -> {
                ok(world.data.channel)
            }

            is TdApi.GetBasicGroupFullInfo -> {
                ok(world.data.groupFullInfo)
            }

            is TdApi.GetSupergroupFullInfo -> {
                ok(world.data.channelFullInfo)
            }

            is TdApi.GetScopeNotificationSettings -> {
                ok(DemoNotifications.scopeSettings())
            }

            is TdApi.GetChatSponsoredMessages -> {
                ok(TdApi.SponsoredMessages(emptyArray(), 0))
            }

            is TdApi.GetChatActiveStories -> {
                ok(TdApi.ChatActiveStories(function.chatId, null, 0, false, 0, emptyArray()))
            }

            is TdApi.ToggleChatIsPinned -> {
                okChanged(world.pinChat(function.chatId, function.isPinned))
            }

            is TdApi.ToggleChatIsMarkedAsUnread -> {
                okChanged(world.markUnread(function.chatId, function.isMarkedAsUnread))
            }

            is TdApi.SetChatNotificationSettings -> {
                okChanged(world.mute(function.chatId, function.notificationSettings.muteFor))
            }

            is TdApi.AddChatToList -> {
                okChanged(world.archive(function.chatId, function.chatList is TdApi.ChatListArchive))
            }

            else -> {
                null
            }
        }

    private fun option(name: String): TdApi.OptionValue =
        when (name) {
            "my_id" -> TdApi.OptionValueInteger(DemoData.ME)
            "version" -> TdApi.OptionValueString("demo")
            else -> TdApi.OptionValueEmpty()
        }

    private fun users(users: Collection<TdApi.User>) = TdApi.Users(users.size, users.map { it.id }.toLongArray())

    private fun TdApi.User.matches(query: String) =
        query.isBlank() || "$firstName $lastName".contains(query.trim(), ignoreCase = true)
}
