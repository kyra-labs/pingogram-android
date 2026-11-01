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
    private val text: (Int) -> String,
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
        ).associateBy { it.id }

    val group = TdApi.BasicGroup(GROUP, GROUP_MEMBERS.size, TdApi.ChatMemberStatusMember(0), true, 0)

    val channel =
        TdApi.Supergroup().apply {
            id = CHANNEL
            usernames = TdApi.Usernames(emptyArray(), emptyArray(), "", emptyArray(), "")
            date = now - DAY * WEEKS
            status = TdApi.ChatMemberStatusMember(0)
            memberCount = CHANNEL_SUBSCRIBERS
            isChannel = true
        }

    private var nextId = 1L

    private fun id(): Long = (nextId++) shl MESSAGE_ID_SHIFT

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
                        incoming(BEN_CHAT, BEN, minutesAgo = 159, content = sticker())
                },
            GROUP_CHAT to
                listOf(
                    incoming(GROUP_CHAT, CHLOE, minutesAgo = 120, content = textOf(R.string.demo_group_morning)),
                    incoming(GROUP_CHAT, ADA, minutesAgo = 100, content = poll()),
                    incoming(GROUP_CHAT, BEN, minutesAgo = 60, content = plainText(PARTY)),
                    incoming(GROUP_CHAT, CHLOE, minutesAgo = 20, content = textOf(R.string.demo_group_checklist))
                        .apply { isPinned = true },
                ),
            CHANNEL_CHAT to
                listOf(
                    post(minutesAgo = DAY_MINUTES * 3, views = 120, content = textOf(R.string.demo_channel_welcome)),
                    post(
                        minutesAgo = DAY_MINUTES,
                        views = 96,
                        content = photo(DemoFile.ChannelPhoto, R.string.demo_channel_colors),
                    ),
                    post(minutesAgo = 45, views = 54, content = textOf(R.string.demo_channel_tip))
                        .apply { interactionInfo = reactions(HEART to 12, views = 54) },
                ),
            ME to
                listOf(
                    outgoing(ME, minutesAgo = DAY_MINUTES * 2, content = textOf(R.string.demo_saved_ideas)),
                    outgoing(ME, minutesAgo = DAY_MINUTES * 2 - 1, content = textOf(R.string.demo_saved_note)),
                ),
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
            ADA,
            GROUP_MEMBERS
                .map { member ->
                    val status =
                        if (member ==
                            ADA
                        ) {
                            TdApi.ChatMemberStatusCreator(false, true)
                        } else {
                            TdApi.ChatMemberStatusMember(0)
                        }
                    TdApi.ChatMember(TdApi.MessageSenderUser(member), "", ADA, now - DAY * WEEKS, status)
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
        phoneNumber = ""
        status = if (online) TdApi.UserStatusOnline(now + ONLINE_FOR) else TdApi.UserStatusRecently(false)
        this.isContact = isContact
        isMutualContact = isContact
        haveAccess = true
        type = TdApi.UserTypeRegular()
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
    ): TdApi.Chat {
        val history = messages.getValue(id)
        val last = history.last()
        val order = last.date.toLong() shl ORDER_SHIFT
        val lists =
            listOf(if (archived) TdApi.ChatListArchive() else TdApi.ChatListMain()) + folders.map(TdApi::ChatListFolder)
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
            permissions =
                TdApi.ChatPermissions().apply {
                    canSendBasicMessages = type !is TdApi.ChatTypeSupergroup
                }
            accentColorId = (id % ACCENT_COLORS).toInt().let { if (it < 0) -it else it }
            profileAccentColorId = -1
            clientData = ""
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

    private fun sticker() =
        TdApi.MessageSticker(
            TdApi.Sticker(
                DemoFile.Sticker.id.toLong(),
                0,
                STICKER_SIZE,
                STICKER_SIZE,
                WAVE,
                TdApi.StickerFormatWebp(),
                TdApi.StickerFullTypeRegular(null),
                null,
                DemoFiles.remote(DemoFile.Sticker),
            ),
            false,
        )

    private fun poll(): TdApi.MessagePoll {
        val options =
            listOf(
                R.string.demo_group_poll_monday to 1,
                R.string.demo_group_poll_wednesday to 2,
                R.string.demo_group_poll_friday to 0,
            )
        return TdApi.MessagePoll(
            DemoPolls.poll(
                POLL_ID,
                text(R.string.demo_group_poll_question),
                options.map {
                    text(it.first) to
                        it.second
                },
            ),
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
            true,
            emptyArray(),
            false,
        ),
    )

    companion object {
        const val ME = 100_001L
        const val ADA = 100_002L
        const val BEN = 100_003L
        const val CHLOE = 100_004L
        const val DEV = 100_005L
        const val GROUP = 200_001L
        const val CHANNEL = 300_001L

        // TDLib's chat ids: a private chat's is its user's, a basic group's its negative id, a supergroup's -100…
        // then its id.
        const val ADA_CHAT = ADA
        const val BEN_CHAT = BEN
        const val DEV_CHAT = DEV
        const val GROUP_CHAT = -GROUP
        const val CHANNEL_CHAT = -1_000_000_000_000L - CHANNEL

        const val WORK_FOLDER = 1
        const val POLL_ID = 1L

        val GROUP_MEMBERS = listOf(ME, ADA, BEN, CHLOE)

        /** The emoji the demo offers as reactions, as Telegram's active emoji reactions. */
        val REACTIONS = listOf("👍", "❤", "🔥", "🎉", "😂", "😮", "😢", "🙏")

        /** Server message ids are multiples of 2^20 in TDLib; the demo keeps to that. */
        const val MESSAGE_ID_SHIFT = 20

        private const val THUMBS_UP = "👍"
        private const val HEART = "❤"
        private const val PARTY = "🎉"
        private const val WAVE = "👋"
        private const val MINUTE = 60
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
