package dev.kyralabs.pingogram.core.demo

import org.drinkless.tdlib.TdApi

/**
 * The demo's sample chats (`docs/adr/0034-demo-mode-for-store-review.md`), as TDLib would announce them: made-up
 * people with plainly made-up names, a group, a channel, Saved Messages, an archived chat and a "Work" folder, with
 * messages of the kinds the app draws. [text] reads the translatable texts (`res/values/strings.xml`); [now] (Unix
 * seconds) is when the demo first started, which every sample message is dated before.
 */
@Suppress("LargeClass", "LongMethod", "TooManyFunctions") // One place for all the sample data, a few lines a chat.
internal class DemoData(
    val text: (Int) -> String,
    private val now: Int,
) {
    val me = user(ME, R.string.demo_me_first_name, R.string.demo_me_last_name, isContact = false)
    val users =
        listOf(
            me,
            user(ADA, R.string.demo_ada_first_name, R.string.demo_ada_last_name, online = true),
            user(BEN, R.string.demo_ben_first_name, R.string.demo_ben_last_name),
            user(CHLOE, R.string.demo_chloe_first_name, R.string.demo_chloe_last_name),
            user(DEV, R.string.demo_dev_first_name, R.string.demo_dev_last_name),
            user(FINN, R.string.demo_finn_first_name, R.string.demo_finn_last_name, isContact = false),
            bot(LOGIN_BOT, R.string.demo_bot_name, "sample_login_bot"),
        ).associateBy { it.id }

    /** The user made the group and the channel, so their info and admin pages are all there to see. */
    val group = TdApi.BasicGroup(GROUP, GROUP_MEMBERS.size, TdApi.ChatMemberStatusCreator(false, true), true, 0)

    val channel =
        TdApi.Supergroup().apply {
            id = CHANNEL
            usernames = TdApi.Usernames(emptyArray(), emptyArray(), "", emptyArray(), "")
            date = now - DAY * WEEKS
            status = TdApi.ChatMemberStatusCreator(false, true)
            memberCount = CHANNEL_SUBSCRIBERS
            isChannel = true
            joinByRequest = true
            hasLinkedChat = true
        }

    /** The forum the user made: a group with topics, each with a few messages. */
    val forum = supergroup(FORUM).apply { isForum = true }

    /** The channel's discussion group, where its posts get comments; the user made it with the channel. */
    val discussion = supergroup(DISCUSSION).apply { hasLinkedChat = true }

    /** A public channel the user is not in: a sample link opens it, with "Join" in the composer's place. */
    val news =
        supergroup(NEWS).apply {
            usernames = TdApi.Usernames(arrayOf(NEWS_USERNAME), emptyArray(), NEWS_USERNAME, emptyArray(), "")
            status = TdApi.ChatMemberStatusLeft()
            memberCount = NEWS_SUBSCRIBERS
            isChannel = true
        }

    /** A private group the user is not in: its sample invite link opens the join sheet. */
    val photoWalks =
        supergroup(PHOTO_WALKS).apply {
            status = TdApi.ChatMemberStatusLeft()
            memberCount = PHOTO_WALKS_MEMBERS.size
        }

    /** The forum's topics: General, and two that people made. */
    val forumTopics: List<TdApi.ForumTopicInfo> =
        listOf(
            topic(GENERAL_TOPIC, R.string.demo_topic_general, TOPIC_BLUE, ME, isGeneral = true),
            topic(READING_TOPIC, R.string.demo_topic_reading, TOPIC_YELLOW, ADA),
            topic(MEETUPS_TOPIC, R.string.demo_topic_meetups, TOPIC_GREEN, ME),
        )

    private var nextId = 1L

    /** When Ada posted her sample story: two hours before the demo started. */
    val storyDate = now - 2 * HOUR

    /** The id of the sample message in Spanish, which "Translate" turns into English. */
    var spanishMessageId = 0L
        private set

    private fun id(): Long = (nextId++) shl MESSAGE_ID_SHIFT

    /** The channel's posts, oldest first; their copies in the discussion group carry the comments. */
    private val channelPosts =
        listOf(
            post(minutesAgo = DAY_MINUTES * 3, views = 120, content = textOf(R.string.demo_channel_welcome)),
            post(
                minutesAgo = DAY_MINUTES,
                views = 96,
                content = photo(DemoFile.ChannelPhoto, R.string.demo_channel_colors),
            ),
            post(minutesAgo = 45, views = 54, content = textOf(R.string.demo_channel_tip))
                .apply { interactionInfo = reactions(HEART to 12, views = 54) },
        )

    /** The discussion group: each post as Telegram forwards it there, the first with two comments. */
    private val discussionMessages: List<TdApi.Message> =
        buildList {
            val first = discussionCopy(channelPosts[0])
            add(first)
            add(comment(first, ADA, minutesAgo = DAY_MINUTES * 3 - 20, R.string.demo_comment_ada))
            add(comment(first, BEN, minutesAgo = DAY_MINUTES * 3 - 50, R.string.demo_comment_ben))
            channelPosts.drop(1).forEach { add(discussionCopy(it)) }
        }.also { messages -> withReplyInfo(messages) }

    /** Every chat's messages, oldest first. */
    val messages: Map<Long, List<TdApi.Message>> =
        mapOf(
            ADA_CHAT to
                listOf(
                    incoming(ADA_CHAT, ADA, minutesAgo = 300, content = textOf(R.string.demo_ada_hello)),
                    incoming(ADA_CHAT, ADA, minutesAgo = 299, content = textOf(R.string.demo_ada_explain)),
                    outgoing(ADA_CHAT, minutesAgo = 290, content = textOf(R.string.demo_me_reply_ada)),
                    incoming(
                        ADA_CHAT,
                        ADA,
                        minutesAgo = 30,
                        content = photo(DemoFile.Photo, R.string.demo_ada_photo_caption),
                    ),
                    incoming(ADA_CHAT, ADA, minutesAgo = 12, content = voice()),
                    incoming(ADA_CHAT, ADA, minutesAgo = 8, content = textOf(R.string.demo_ada_links)),
                    incoming(ADA_CHAT, ADA, minutesAgo = 5, content = textOf(R.string.demo_ada_formatting))
                        .apply { interactionInfo = reactions(THUMBS_UP to 1, chosen = THUMBS_UP) },
                ),
            BEN_CHAT to
                listOf(
                    incoming(BEN_CHAT, BEN, minutesAgo = 180, content = textOf(R.string.demo_ben_slides)),
                ).let { first ->
                    first +
                        outgoing(BEN_CHAT, minutesAgo = 170, content = textOf(R.string.demo_me_reply_ben))
                            .apply {
                                replyTo =
                                    TdApi.MessageReplyToMessage(BEN_CHAT, first[0].id, null, 0, "", null, 0, null)
                            } +
                        incoming(BEN_CHAT, BEN, minutesAgo = 160, content = textOf(R.string.demo_chloe_agenda))
                            .apply {
                                forwardInfo =
                                    TdApi.MessageForwardInfo(TdApi.MessageOriginUser(CHLOE), now - DAY, null, "")
                            } +
                        incoming(BEN_CHAT, BEN, minutesAgo = 159, content = sticker()) +
                        incoming(BEN_CHAT, BEN, minutesAgo = 150, content = textOf(R.string.demo_ben_folder))
                },
            GROUP_CHAT to
                listOf(
                    incoming(GROUP_CHAT, CHLOE, minutesAgo = 120, content = textOf(R.string.demo_group_morning)),
                    incoming(GROUP_CHAT, ADA, minutesAgo = 100, content = poll()),
                    incoming(GROUP_CHAT, BEN, minutesAgo = 60, content = plainText(PARTY)),
                    incoming(GROUP_CHAT, BEN, minutesAgo = 40, content = textOf(R.string.demo_group_spanish))
                        .also { spanishMessageId = it.id },
                    incoming(GROUP_CHAT, CHLOE, minutesAgo = 20, content = textOf(R.string.demo_group_checklist))
                        .apply { isPinned = true },
                ),
            CHANNEL_CHAT to channelPosts,
            DISCUSSION_CHAT to discussionMessages,
            PHOTO_WALKS_CHAT to
                listOf(
                    incoming(
                        PHOTO_WALKS_CHAT,
                        CHLOE,
                        minutesAgo = 300,
                        content = textOf(R.string.demo_photo_walks_message),
                    ),
                ),
            NEWS_CHAT to
                listOf(
                    message(
                        NEWS_CHAT,
                        TdApi.MessageSenderChat(NEWS_CHAT),
                        minutesAgo = 200,
                        content = textOf(R.string.demo_news_post),
                        outgoing = false,
                    ).apply {
                        isChannelPost = true
                        interactionInfo = TdApi.MessageInteractionInfo(NEWS_SUBSCRIBERS / 2, 0, null, null)
                    },
                ),
            FORUM_CHAT to
                listOf(
                    incoming(FORUM_CHAT, CHLOE, minutesAgo = 600, content = textOf(R.string.demo_forum_welcome))
                        .inTopic(GENERAL_TOPIC),
                    incoming(FORUM_CHAT, ADA, minutesAgo = 400, content = textOf(R.string.demo_forum_reading))
                        .inTopic(READING_TOPIC),
                ).let { first ->
                    first +
                        incoming(FORUM_CHAT, BEN, minutesAgo = 380, content = textOf(R.string.demo_forum_reading_reply))
                            .inTopic(READING_TOPIC)
                            .apply {
                                replyTo =
                                    TdApi.MessageReplyToMessage(FORUM_CHAT, first[1].id, null, 0, "", null, 0, null)
                            } +
                        incoming(FORUM_CHAT, CHLOE, minutesAgo = 90, content = textOf(R.string.demo_forum_meetup))
                            .inTopic(MEETUPS_TOPIC)
                },
            // Saved Messages sorts what it holds by where it came from: the user's notes, and a saved post.
            ME to
                listOf(
                    outgoing(ME, minutesAgo = DAY_MINUTES * 2, content = textOf(R.string.demo_saved_ideas)),
                    outgoing(ME, minutesAgo = DAY_MINUTES * 2 - 1, content = textOf(R.string.demo_saved_note)),
                ).onEach { it.topicId = TdApi.MessageTopicSavedMessages(ME) } +
                outgoing(ME, minutesAgo = DAY_MINUTES - 30, content = channelPosts[1].content).apply {
                    forwardInfo =
                        TdApi.MessageForwardInfo(
                            TdApi.MessageOriginChannel(CHANNEL_CHAT, channelPosts[1].id, ""),
                            channelPosts[1].date,
                            null,
                            "",
                        )
                    topicId = TdApi.MessageTopicSavedMessages(CHANNEL_CHAT)
                },
            DEV_CHAT to
                listOf(
                    incoming(DEV_CHAT, DEV, minutesAgo = DAY_MINUTES * 30, content = textOf(R.string.demo_dev_old)),
                ),
        )

    /** The chats with their positions and unread counts, before what the user did ([DemoEvent]s) is replayed. */
    val chats: List<TdApi.Chat> =
        listOf(
            chat(ADA_CHAT, TdApi.ChatTypePrivate(ADA), name(ADA), unread = 2),
            chat(BEN_CHAT, TdApi.ChatTypePrivate(BEN), name(BEN), unread = 0, folders = listOf(WORK_FOLDER)),
            chat(
                GROUP_CHAT,
                TdApi.ChatTypeBasicGroup(GROUP),
                text(R.string.demo_group_title),
                unread = 3,
                folders = listOf(WORK_FOLDER),
            ),
            chat(CHANNEL_CHAT, TdApi.ChatTypeSupergroup(CHANNEL, true), text(R.string.demo_channel_title), unread = 1),
            chat(FORUM_CHAT, TdApi.ChatTypeSupergroup(FORUM, false), text(R.string.demo_forum_title), unread = 1),
            chat(
                DISCUSSION_CHAT,
                TdApi.ChatTypeSupergroup(DISCUSSION, false),
                text(R.string.demo_discussion_title),
                unread = 0,
            ),
            chat(
                PHOTO_WALKS_CHAT,
                TdApi.ChatTypeSupergroup(PHOTO_WALKS, false),
                text(R.string.demo_photo_walks_title),
                unread = 0,
                listed = false,
            ),
            chat(
                NEWS_CHAT,
                TdApi.ChatTypeSupergroup(NEWS, true),
                text(R.string.demo_news_title),
                unread = 0,
                listed = false,
            ),
            chat(ME, TdApi.ChatTypePrivate(ME), name(ME), unread = 0),
            chat(DEV_CHAT, TdApi.ChatTypePrivate(DEV), name(DEV), unread = 0, archived = true),
        )

    val folder =
        TdApi.ChatFolderInfo(
            WORK_FOLDER,
            TdApi.ChatFolderName(TdApi.FormattedText(text(R.string.demo_folder_work), emptyArray()), false),
            TdApi.ChatFolderIcon("Work"),
            -1,
            false,
            false,
        )

    /** What `getChatFolder` answers for "Work": the two chats it holds, nothing by kind. */
    val folderSettings =
        TdApi.ChatFolder(
            folder.name,
            folder.icon,
            -1,
            false,
            longArrayOf(),
            longArrayOf(BEN_CHAT, GROUP_CHAT),
            longArrayOf(),
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
        )

    fun userFullInfo(userId: Long) =
        TdApi.UserFullInfo().apply {
            bio = TdApi.FormattedText(if (userId == ADA) text(R.string.demo_ada_bio) else "", emptyArray())
        }

    val groupFullInfo =
        TdApi.BasicGroupFullInfo(
            null,
            "",
            ME,
            GROUP_MEMBERS
                .map { member ->
                    val status =
                        when (member) {
                            ME -> TdApi.ChatMemberStatusCreator(false, true)
                            ADA -> TdApi.ChatMemberStatusAdministrator(true, adminRights())
                            else -> TdApi.ChatMemberStatusMember(0)
                        }
                    TdApi.ChatMember(TdApi.MessageSenderUser(member), "", ME, now - DAY * WEEKS, status)
                }.toTypedArray(),
            false,
            false,
            null,
            emptyArray(),
        )

    val channelFullInfo =
        TdApi.SupergroupFullInfo().apply {
            description = text(R.string.demo_channel_description)
            memberCount = CHANNEL_SUBSCRIBERS
            administratorCount = 1
            canGetMembers = true
            canGetStatistics = true
            canSetStickerSet = false
            isAllHistoryAvailable = true
            linkedChatId = DISCUSSION_CHAT
            botCommands = emptyArray()
        }

    /** The other supergroups' full info: the forum and discussion group with the user and Ada administrators. */
    fun groupFullInfo(supergroupId: Long) =
        TdApi.SupergroupFullInfo().apply {
            description =
                when (supergroupId) {
                    NEWS -> text(R.string.demo_news_description)
                    PHOTO_WALKS -> text(R.string.demo_photo_walks_description)
                    else -> ""
                }
            memberCount =
                when (supergroupId) {
                    NEWS -> NEWS_SUBSCRIBERS
                    PHOTO_WALKS -> PHOTO_WALKS_MEMBERS.size
                    else -> SUPERGROUP_MEMBERS.size
                }
            administratorCount = if (supergroupId == NEWS || supergroupId == PHOTO_WALKS) 1 else 2
            canGetMembers = true
            isAllHistoryAvailable = true
            linkedChatId = if (supergroupId == DISCUSSION) CHANNEL_CHAT else 0
            botCommands = emptyArray()
        }

    private fun supergroup(id: Long) =
        TdApi.Supergroup().apply {
            this.id = id
            usernames = TdApi.Usernames(emptyArray(), emptyArray(), "", emptyArray(), "")
            date = now - DAY * WEEKS
            status = TdApi.ChatMemberStatusCreator(false, true)
            memberCount = SUPERGROUP_MEMBERS.size
        }

    private fun topic(
        id: Int,
        name: Int,
        color: Int,
        creator: Long,
        isGeneral: Boolean = false,
    ) = TdApi.ForumTopicInfo().apply {
        chatId = FORUM_CHAT
        forumTopicId = id
        this.name = text(name)
        icon = TdApi.ForumTopicIcon(color, 0)
        creationDate = now - DAY * WEEKS
        creatorId = TdApi.MessageSenderUser(creator)
        this.isGeneral = isGeneral
        isOutgoing = creator == ME
    }

    private fun TdApi.Message.inTopic(topic: Int) = apply { topicId = TdApi.MessageTopicForum(topic) }

    /** A post as Telegram forwards it into the discussion group, where its comments reply to it. */
    private fun discussionCopy(post: TdApi.Message) =
        message(DISCUSSION_CHAT, TdApi.MessageSenderChat(CHANNEL_CHAT), 0, post.content, outgoing = false).apply {
            date = post.date
            forwardInfo =
                TdApi.MessageForwardInfo(TdApi.MessageOriginChannel(CHANNEL_CHAT, post.id, ""), post.date, null, "")
        }

    private fun comment(
        copy: TdApi.Message,
        from: Long,
        minutesAgo: Int,
        resource: Int,
    ) = incoming(DISCUSSION_CHAT, from, minutesAgo, textOf(resource)).apply {
        topicId = TdApi.MessageTopicThread(copy.id)
        replyTo = TdApi.MessageReplyToMessage(DISCUSSION_CHAT, copy.id, null, 0, "", null, 0, null)
    }

    /** Each post and its copy get the count of the comments on it, which the "comments" link shows. */
    private fun withReplyInfo(discussion: List<TdApi.Message>) {
        channelPosts.forEach { post ->
            val copy =
                discussion.first { (it.forwardInfo?.origin as? TdApi.MessageOriginChannel)?.messageId == post.id }
            val replies = discussion.filter { (it.topicId as? TdApi.MessageTopicThread)?.messageThreadId == copy.id }
            val info =
                TdApi.MessageReplyInfo(
                    replies.size,
                    replies.map { it.senderId }.distinctBy { (it as TdApi.MessageSenderUser).userId }.toTypedArray(),
                    replies.lastOrNull()?.id ?: 0,
                    0,
                    replies.lastOrNull()?.id ?: 0,
                )
            val views = post.interactionInfo
            val viewCount = views?.viewCount ?: 0
            post.interactionInfo = TdApi.MessageInteractionInfo(viewCount, 0, info, views?.reactions)
            copy.interactionInfo = TdApi.MessageInteractionInfo(viewCount, 0, info, null)
        }
    }

    /** Every right an administrator can have, as the group's Ada has them. */
    fun adminRights() =
        TdApi.ChatAdministratorRights().apply {
            canManageChat = true
            canChangeInfo = true
            canPostMessages = true
            canEditMessages = true
            canDeleteMessages = true
            canInviteUsers = true
            canRestrictMembers = true
            canPinMessages = true
            canManageTopics = true
            canPromoteMembers = true
            canManageVideoChats = true
        }

    private fun name(userId: Long) = users.getValue(userId).let { "${it.firstName} ${it.lastName}".trim() }

    private fun user(
        id: Long,
        firstName: Int,
        lastName: Int,
        online: Boolean = false,
        isContact: Boolean = true,
    ) = TdApi.User().apply {
        this.id = id
        this.firstName = text(firstName)
        this.lastName = text(lastName)
        usernames = TdApi.Usernames(emptyArray(), emptyArray(), "", emptyArray(), "")
        // Numbers of the UK range Ofcom keeps for fiction (07700 900000–900999): they reach no one.
        phoneNumber = FICTION_NUMBERS + (id - ME).toString().padStart(FICTION_DIGITS, '0')
        status = if (online) TdApi.UserStatusOnline(now + ONLINE_FOR) else TdApi.UserStatusRecently(false)
        this.isContact = isContact
        isMutualContact = isContact
        haveAccess = true
        type = TdApi.UserTypeRegular()
        languageCode = "en"
        accentColorId = (id % ACCENT_COLORS).toInt()
        profileAccentColorId = -1
    }

    private fun bot(
        id: Long,
        name: Int,
        username: String,
    ) = TdApi.User().apply {
        this.id = id
        firstName = text(name)
        lastName = ""
        usernames = TdApi.Usernames(arrayOf(username), emptyArray(), username, emptyArray(), "")
        phoneNumber = ""
        status = TdApi.UserStatusEmpty()
        haveAccess = true
        type = TdApi.UserTypeBot().apply { inlineQueryPlaceholder = "" }
        languageCode = "en"
        accentColorId = (id % ACCENT_COLORS).toInt()
        profileAccentColorId = -1
    }

    private fun chat(
        id: Long,
        type: TdApi.ChatType,
        title: String,
        unread: Int,
        folders: List<Int> = emptyList(),
        archived: Boolean = false,
        listed: Boolean = true,
    ): TdApi.Chat {
        val history = messages.getValue(id)
        val last = history.last()
        val order = last.date.toLong() shl ORDER_SHIFT
        val lists =
            if (listed) {
                listOf(if (archived) TdApi.ChatListArchive() else TdApi.ChatListMain()) +
                    folders.map(TdApi::ChatListFolder)
            } else {
                emptyList()
            }
        return TdApi.Chat().apply {
            this.id = id
            this.type = type
            this.title = title
            lastMessage = last
            positions = lists.map { TdApi.ChatPosition(it, order, false, null) }.toTypedArray()
            chatLists = lists.toTypedArray()
            unreadCount = unread
            lastReadInboxMessageId = history.dropLast(unread).lastOrNull { !it.isOutgoing }?.id ?: 0
            lastReadOutboxMessageId = history.last().id
            notificationSettings = DemoNotifications.chatSettings(muteFor = 0)
            canBeDeletedOnlyForSelf = true
            permissions = permissionsFor(type)
            accentColorId = (id % ACCENT_COLORS).toInt().let { if (it < 0) -it else it }
            profileAccentColorId = -1
            clientData = ""
        }
    }

    /** Everything a member may send, except in the channel, where only its admins post. */
    private fun permissionsFor(type: TdApi.ChatType): TdApi.ChatPermissions {
        val canSend = type !is TdApi.ChatTypeSupergroup || !type.isChannel
        return TdApi.ChatPermissions().apply {
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
        }
    }

    private fun message(
        chatId: Long,
        sender: TdApi.MessageSender,
        minutesAgo: Int,
        content: TdApi.MessageContent,
        outgoing: Boolean,
    ) = TdApi.Message().apply {
        id = id()
        this.chatId = chatId
        senderId = sender
        date = now - minutesAgo * MINUTE
        this.content = content
        isOutgoing = outgoing
        unreadReactions = emptyArray()
        senderTag = ""
        summaryLanguageCode = ""
        authorSignature = ""
    }

    private fun incoming(
        chatId: Long,
        from: Long,
        minutesAgo: Int,
        content: TdApi.MessageContent,
    ) = message(chatId, TdApi.MessageSenderUser(from), minutesAgo, content, outgoing = false)

    private fun outgoing(
        chatId: Long,
        minutesAgo: Int,
        content: TdApi.MessageContent,
    ) = message(chatId, TdApi.MessageSenderUser(ME), minutesAgo, content, outgoing = true)

    private fun post(
        minutesAgo: Int,
        views: Int,
        content: TdApi.MessageContent,
    ) = message(CHANNEL_CHAT, TdApi.MessageSenderChat(CHANNEL_CHAT), minutesAgo, content, outgoing = false).apply {
        isChannelPost = true
        interactionInfo = TdApi.MessageInteractionInfo(views, 0, null, null)
    }

    private fun textOf(resource: Int) = TdApi.MessageText(formattedText(text(resource)), null, null)

    private fun plainText(literal: String) = TdApi.MessageText(TdApi.FormattedText(literal, emptyArray()), null, null)

    private fun photo(
        file: DemoFile,
        caption: Int,
    ) = TdApi.MessagePhoto(
        TdApi.Photo(
            false,
            null,
            arrayOf(TdApi.PhotoSize("x", DemoFiles.remote(file), PHOTO_WIDTH, PHOTO_HEIGHT, intArrayOf())),
        ),
        null,
        formattedText(text(caption)),
        false,
        false,
        false,
    )

    private fun voice() =
        TdApi.MessageVoiceNote(
            TdApi.VoiceNote(VOICE_SECONDS, WAVEFORM, "audio/wav", null, DemoFiles.remote(DemoFile.Voice)),
            TdApi.FormattedText("", emptyArray()),
            false,
        )

    /** The English of the message in Spanish; the demo's other messages are in the app's language already. */
    val spanishTranslation: String get() = text(R.string.demo_group_spanish_translation)

    /** The sample sticker sets: "Pings", installed, and "Ring shapes", trending. */
    val stickerSets: List<TdApi.StickerSet> =
        listOf(
            stickerSet(
                PINGS_SET,
                R.string.demo_sticker_set_pings,
                "DemoPings",
                listOf(
                    DemoFile.Sticker to WAVE,
                    DemoFile.StickerTeal to "👍",
                    DemoFile.StickerWarm to "🎉",
                    DemoFile.StickerPink to "❤",
                ),
            ),
            stickerSet(
                SHAPES_SET,
                R.string.demo_sticker_set_shapes,
                "DemoRingShapes",
                listOf(DemoFile.StickerGreen to "😮", DemoFile.StickerPurple to "🔥", DemoFile.StickerGold to "🙏"),
            ),
        )

    /** A sticker the demo has, by its file's id (the panel sends stickers by file id). */
    fun stickerByFileId(fileId: Int): TdApi.Sticker? =
        stickerSets.flatMap { it.stickers.toList() }.firstOrNull { it.sticker.id == fileId }

    private fun stickerSet(
        id: Long,
        title: Int,
        name: String,
        stickers: List<Pair<DemoFile, String>>,
    ) = TdApi.StickerSet().apply {
        this.id = id
        this.title = text(title)
        this.name = name
        stickerType = TdApi.StickerTypeRegular()
        isInstalled = id == PINGS_SET
        isViewed = id == PINGS_SET
        this.stickers = stickers.map { (file, emoji) -> stickerOf(file, emoji, id) }.toTypedArray()
        emojis = stickers.map { TdApi.Emojis(arrayOf(it.second)) }.toTypedArray()
    }

    private fun stickerOf(
        file: DemoFile,
        emoji: String,
        setId: Long = PINGS_SET,
    ) = TdApi.Sticker(
        file.id.toLong(),
        setId,
        STICKER_SIZE,
        STICKER_SIZE,
        emoji,
        TdApi.StickerFormatWebp(),
        TdApi.StickerFullTypeRegular(null),
        null,
        DemoFiles.remote(file),
    )

    private fun sticker() = TdApi.MessageSticker(stickerOf(DemoFile.Sticker, WAVE), false)

    private fun poll(): TdApi.MessagePoll {
        val options =
            listOf(
                R.string.demo_group_poll_monday to 1,
                R.string.demo_group_poll_wednesday to 2,
                R.string.demo_group_poll_friday to 0,
            )
        return TdApi.MessagePoll(
            DemoPolls
                .poll(
                    POLL_ID,
                    text(R.string.demo_group_poll_question),
                    options.map {
                        text(it.first) to
                            it.second
                    },
                ).apply {
                    // A public poll, so "View votes" has voters to list (POLL_VOTERS).
                    isAnonymous = false
                    canGetVoters = true
                },
            TdApi.FormattedText("", emptyArray()),
            null,
            false,
        )
    }

    private fun reactions(
        vararg counts: Pair<String, Int>,
        chosen: String? = null,
        views: Int = 0,
    ) = TdApi.MessageInteractionInfo(
        views,
        0,
        null,
        TdApi.MessageReactions(
            counts
                .map { (emoji, count) ->
                    TdApi.MessageReaction(TdApi.ReactionTypeEmoji(emoji), count, emoji == chosen, null, emptyArray())
                }.toTypedArray(),
            false,
            emptyArray(),
            true,
        ),
    )

    companion object {
        const val ME = 100_001L
        const val ADA = 100_002L
        const val BEN = 100_003L
        const val CHLOE = 100_004L
        const val DEV = 100_005L
        const val FINN = 100_006L
        const val LOGIN_BOT = 100_007L

        /** The start of the UK numbers kept for fiction, and the digits that follow it. */
        private const val FICTION_NUMBERS = "4477009"
        private const val FICTION_DIGITS = 5
        const val GROUP = 200_001L
        const val CHANNEL = 300_001L
        const val FORUM = 300_002L
        const val DISCUSSION = 300_003L
        val SUPERGROUP_MEMBERS = listOf(ME, ADA, BEN, CHLOE)
        const val NEWS = 300_004L
        const val PHOTO_WALKS = 300_005L
        val PHOTO_WALKS_MEMBERS = listOf(CHLOE, ADA, DEV)
        const val NEWS_USERNAME = "sample_news"
        const val NEWS_SUBSCRIBERS = 2_400

        // The sample links in Ada's and Ben's messages: made up, opening nothing outside the demo.
        const val PHOTO_WALKS_LINK = "https://t.me/+DemoPhotoWalks"
        const val BOOK_SWAP_LINK = "https://t.me/+DemoBookSwap"
        const val HOBBIES_LINK = "https://t.me/addlist/DemoHobbies"
        const val GENERAL_TOPIC = 1
        const val READING_TOPIC = 2
        const val MEETUPS_TOPIC = 3

        // Telegram's topic colors: blue, yellow and green.
        private const val TOPIC_BLUE = 0x6FB9F0
        private const val TOPIC_YELLOW = 0xFFD67E
        private const val TOPIC_GREEN = 0x8EEE98

        // TDLib's chat ids: a private chat's is its user's, a basic group's its negative id, a supergroup's -100…
        // then its id.
        const val ADA_CHAT = ADA
        const val BEN_CHAT = BEN
        const val DEV_CHAT = DEV
        const val GROUP_CHAT = -GROUP
        const val CHANNEL_CHAT = -1_000_000_000_000L - CHANNEL
        const val FORUM_CHAT = -1_000_000_000_000L - FORUM
        const val DISCUSSION_CHAT = -1_000_000_000_000L - DISCUSSION
        const val NEWS_CHAT = -1_000_000_000_000L - NEWS
        const val PHOTO_WALKS_CHAT = -1_000_000_000_000L - PHOTO_WALKS

        /** Telegram's id of the Saved Messages topic of messages whose authors hid their accounts. */
        const val AUTHOR_HIDDEN_TOPIC = 2_666_000L

        /** TDLib's chat ids of secret chats: this, then the secret chat's id. */
        const val SECRET_CHAT_BASE = -2_000_000_000_000L

        const val WORK_FOLDER = 1
        const val POLL_ID = 1L

        val GROUP_MEMBERS = listOf(ME, ADA, BEN, CHLOE)

        /** Who voted for each answer of the sample poll, as the vote counts it starts with say. */
        val POLL_VOTERS = listOf(listOf(BEN), listOf(ADA, CHLOE), emptyList())

        const val PINGS_SET = 1L
        const val SHAPES_SET = 2L

        /** The emoji the demo offers as reactions, as Telegram's active emoji reactions. */
        val REACTIONS = listOf("👍", "❤", "🔥", "🎉", "😂", "😮", "😢", "🙏")

        /** Server message ids are multiples of 2^20 in TDLib; the demo keeps to that. */
        const val MESSAGE_ID_SHIFT = 20

        private const val THUMBS_UP = "👍"
        private const val HEART = "❤"
        private const val PARTY = "🎉"
        private const val WAVE = "👋"
        private const val MINUTE = 60
        private const val HOUR = 3_600
        private const val DAY = 86_400
        private const val DAY_MINUTES = 1_440
        private const val WEEKS = 30
        private const val ONLINE_FOR = 365 * DAY
        private const val CHANNEL_SUBSCRIBERS = 1_284
        private const val ACCENT_COLORS = 7
        private const val ORDER_SHIFT = 20
        private const val PHOTO_WIDTH = 1280
        private const val PHOTO_HEIGHT = 960
        private const val STICKER_SIZE = 512
        private val WAVEFORM = ByteArray(63) { ((it * 37) % 31).toByte() }
    }
}
