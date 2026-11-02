package dev.kyralabs.pingogram.core.demo

import org.drinkless.tdlib.TdApi

/**
 * Links and joining in the demo (`docs/adr/0034-demo-mode-for-store-review.md`): what kind of link a text is
 * ([DemoLinkParser]), the chats a username or a message link names, and the sample invite links of Ada's message:
 * a private group that opens the join sheet, a group that asks the admins first, and the public channel, whose
 * composer offers "Join". Usernames, invite links and message links that are not the demo's find nothing, as
 * nothing outside the demo exists. Private chats with the sample people are made as TDLib makes them.
 *
 * One branch per function, a helper per kind of link, and guard clauses for links that are not the demo's: hence
 * the suppressions.
 */
@Suppress("CyclomaticComplexMethod", "TooManyFunctions", "ReturnCount")
internal class DemoLinks : DemoArea {
    override fun answer(
        world: DemoWorld,
        function: TdApi.Function<*>,
    ): DemoAnswer? =
        when (function) {
            is TdApi.GetInternalLinkType -> {
                DemoLinkParser.parse(function.link)?.let(::ok) ?: notFound()
            }

            is TdApi.SearchPublicChat -> {
                publicChat(world, function.username)
            }

            is TdApi.CreatePrivateChat -> {
                world.privateChat(function.userId)?.let(::madeChat) ?: notFound()
            }

            is TdApi.SearchUserByToken -> {
                notFound()
            }

            is TdApi.CheckChatInviteLink -> {
                checkInvite(world, function.inviteLink)
            }

            is TdApi.JoinChatByInviteLink -> {
                joinByInvite(world, function.inviteLink)
            }

            is TdApi.JoinChat -> {
                join(world, function.chatId)
            }

            is TdApi.GetMessageLinkInfo -> {
                messageLinkInfo(world, function.url)
            }

            is TdApi.GetChatBoostLinkInfo -> {
                chatOfLink(world, function.url)
                    ?.let { ok(TdApi.ChatBoostLinkInfo(it == DemoData.NEWS_CHAT, it)) }
                    ?: badRequest(LINK_INVALID)
            }

            else -> {
                null
            }
        }

    /** A username: the public channel, or the bot's chat. The sample people have no usernames. */
    private fun publicChat(
        world: DemoWorld,
        username: String,
    ): DemoAnswer {
        val name = username.trim().removePrefix("@")
        if (name.equals(DemoData.NEWS_USERNAME, ignoreCase = true)) {
            return world.chat(DemoData.NEWS_CHAT)?.let(::ok) ?: notFound()
        }
        val user =
            world.users.values.firstOrNull { user ->
                user.usernames
                    ?.activeUsernames
                    .orEmpty()
                    .any { it.equals(name, ignoreCase = true) }
            } ?: return notFound()
        return world.privateChat(user.id)?.let(::madeChat) ?: notFound()
    }

    /** A chat answered; one made just now is a change, so the journal makes it again before what is sent to it. */
    private fun madeChat(made: Pair<TdApi.Chat, List<TdApi.Update>>): DemoAnswer =
        if (made.second.isEmpty()) ok(made.first) else changed(made.first, made.second)

    private fun checkInvite(
        world: DemoWorld,
        link: String,
    ): DemoAnswer {
        val normalized = normalize(link)
        val data = world.data
        val text = data.text
        return when (normalized) {
            DemoData.PHOTO_WALKS_LINK -> {
                val chat = world.chat(DemoData.PHOTO_WALKS_CHAT) ?: return notFound()
                val joined = world.groups.statusOf(chat.id) !is TdApi.ChatMemberStatusLeft
                ok(
                    inviteInfo(
                        chatId = if (joined) chat.id else 0,
                        title = chat.title,
                        description = text(R.string.demo_photo_walks_description),
                        members = DemoData.PHOTO_WALKS_MEMBERS,
                        createsJoinRequest = false,
                    ),
                )
            }

            DemoData.BOOK_SWAP_LINK -> {
                ok(
                    inviteInfo(
                        chatId = 0,
                        title = text(R.string.demo_book_swap_title),
                        description = text(R.string.demo_book_swap_description),
                        members = listOf(DemoData.BEN, DemoData.DEV),
                        createsJoinRequest = true,
                    ),
                )
            }

            else -> {
                // One of the user's own links: they are in its chat, which opens at once.
                val chatId = ownLinkChat(world, normalized) ?: return badRequest(INVITE_INVALID)
                val chat = world.chat(chatId) ?: return notFound()
                ok(inviteInfo(chat.id, chat.title, "", emptyList(), createsJoinRequest = false))
            }
        }
    }

    private fun joinByInvite(
        world: DemoWorld,
        link: String,
    ): DemoAnswer =
        when (val normalized = normalize(link)) {
            DemoData.PHOTO_WALKS_LINK -> join(world, DemoData.PHOTO_WALKS_CHAT)

            // Telegram answers a link that needs the admins' approval with this error once the request is sent.
            DemoData.BOOK_SWAP_LINK -> badRequest("INVITE_REQUEST_SENT")

            else -> ownLinkChat(world, normalized)?.let { world.chat(it) }?.let(::ok) ?: badRequest(INVITE_INVALID)
        }

    private fun join(
        world: DemoWorld,
        chatId: Long,
    ): DemoAnswer {
        val chat = world.chat(chatId) ?: return notFound()
        val updates = world.join(chatId)
        val isGroup = (chat.type as? TdApi.ChatTypeSupergroup)?.isChannel == false
        val joined =
            if (isGroup && updates.isNotEmpty()) {
                world.addOwn(chatId, TdApi.MessageChatJoinByLink())?.second.orEmpty()
            } else {
                emptyList()
            }
        return changed(chat, updates + joined)
    }

    private fun ownLinkChat(
        world: DemoWorld,
        link: String,
    ): Long? =
        world.groups.links.entries
            .firstOrNull { (_, links) -> links.any { normalize(it.inviteLink) == link && !it.isRevoked } }
            ?.key

    private fun messageLinkInfo(
        world: DemoWorld,
        url: String,
    ): DemoAnswer {
        val chatId = chatOfLink(world, url) ?: return notFound()
        val number =
            url
                .substringBefore('?')
                .trimEnd('/')
                .substringAfterLast('/')
                .toLongOrNull()
        val message = number?.let { world.message(chatId, it shl DemoData.MESSAGE_ID_SHIFT) }
        return ok(
            TdApi.MessageLinkInfo(chatId == DemoData.NEWS_CHAT, chatId, message?.topicId, message, 0, 0, "", false),
        )
    }

    /** The chat a `t.me/c/<id>/…` or `t.me/<username>/…` link names, when it is one of the demo's. */
    private fun chatOfLink(
        world: DemoWorld,
        url: String,
    ): Long? {
        val path =
            url
                .substringAfter("://")
                .substringAfter('/')
                .substringBefore('?')
                .split('/')
        val chatId =
            when {
                path.firstOrNull() == "boost" -> chatOfName(path.getOrNull(1))
                path.firstOrNull() == "c" -> path.getOrNull(1)?.toLongOrNull()?.let(DemoIds::supergroupChat)
                else -> chatOfName(path.firstOrNull())
            }
        return chatId?.takeIf { world.chat(it) != null }
    }

    private fun chatOfName(name: String?): Long? =
        DemoData.NEWS_CHAT.takeIf { name.equals(DemoData.NEWS_USERNAME, ignoreCase = true) }

    private fun inviteInfo(
        chatId: Long,
        title: String,
        description: String,
        members: List<Long>,
        createsJoinRequest: Boolean,
    ) = TdApi.ChatInviteLinkInfo().apply {
        this.chatId = chatId
        type = TdApi.InviteLinkChatTypeSupergroup()
        this.title = title
        accentColorId = 0
        this.description = description
        memberCount = members.size
        memberUserIds = members.toLongArray()
        this.createsJoinRequest = createsJoinRequest
    }

    /** `t.me/joinchat/…` and `t.me/+…`, with or without the scheme, as one form. */
    private fun normalize(link: String): String {
        val path =
            link
                .trim()
                .substringAfter("://")
                .substringAfter('/')
                .substringBefore('?')
        return if (path.startsWith(
                "joinchat/",
            )
        ) {
            "https://t.me/+${path.removePrefix("joinchat/")}"
        } else {
            "https://t.me/$path"
        }
    }

    private companion object {
        const val INVITE_INVALID = "INVITE_HASH_INVALID"
        const val LINK_INVALID = "Invalid link"
    }
}
