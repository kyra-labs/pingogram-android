package dev.kyralabs.pingogram.feature.chat.ui.preview

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.Photo
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatContent
import dev.kyralabs.pingogram.feature.chat.ui.ChatHeader
import dev.kyralabs.pingogram.feature.chat.ui.ChatItem
import dev.kyralabs.pingogram.feature.chat.ui.ChatSubtitle
import dev.kyralabs.pingogram.feature.chat.ui.ChatUiState
import dev.kyralabs.pingogram.feature.chat.ui.GroupPosition
import dev.kyralabs.pingogram.feature.chat.ui.LinkPreviewCard
import dev.kyralabs.pingogram.feature.chat.ui.OutgoingStatus
import dev.kyralabs.pingogram.feature.chat.ui.ReactionChip
import dev.kyralabs.pingogram.feature.chat.ui.ReactionLabel
import dev.kyralabs.pingogram.feature.chat.ui.ReplyQuote
import dev.kyralabs.pingogram.feature.chat.ui.SenderAvatar
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * Sample conversations for previews and screenshot tests: a private chat with grouping, every outgoing state and the
 * unread divider; a group with sender names, avatars, replies, forwards, reactions and a link preview; a channel;
 * service messages; a message with every kind of formatting; and the loading, empty, error and not-found states.
 * Items are listed newest first, as the reverse layout shows them.
 */
internal object ChatPreviewData {
    private val today = UiText.resource(R.string.chat_date_today)
    private val yesterday = UiText.resource(R.string.chat_date_yesterday)
    private val you = UiText.resource(UiR.string.preview_sender_you)

    /** A photo without sizes, which no image loads for. Declared before the samples, which use it. */
    private val photo =
        MessageContent.Photo(
            photo = Photo(sizes = persistentListOf(), minithumbnail = null),
            caption = FormattedText.Empty,
            hasSpoiler = false,
            isSecret = false,
            showCaptionAboveMedia = false,
        )

    private const val ADA = 101L
    private const val GRACE = 102L
    private const val LINUS = 103L

    val privateChat =
        ChatUiState(
            header =
                ChatHeader(
                    chatId = ADA,
                    title = UiText.Plain("Ada Lovelace"),
                    photo = null,
                    placeholderIcon = null,
                    subtitle = ChatSubtitle.Status(UiText.resource(R.string.chat_subtitle_online)),
                ),
            content =
                content(
                    bubble(
                        11,
                        "See you there!",
                        time = "9:46 AM",
                        sender = UiText.Plain("Ada"),
                        senderId = ADA,
                        position = GroupPosition.Last,
                    ),
                    bubble(
                        10,
                        "Is the meetup still on for tonight?",
                        time = "9:45 AM",
                        sender = UiText.Plain("Ada"),
                        senderId = ADA,
                        position = GroupPosition.First,
                    ),
                    ChatItem.UnreadDivider(today),
                    bubble(
                        9,
                        "Sending the slides now",
                        time = "9:41 AM",
                        isOutgoing = true,
                        status = OutgoingStatus.Pending,
                    ),
                    bubble(
                        8,
                        "This one didn't go through",
                        time = "9:40 AM",
                        isOutgoing = true,
                        status = OutgoingStatus.Failed,
                        position = GroupPosition.Last,
                    ),
                    bubble(
                        7,
                        "Delivered, not read yet",
                        time = "9:40 AM",
                        isOutgoing = true,
                        status = OutgoingStatus.Sent,
                        position = GroupPosition.Middle,
                    ),
                    bubble(
                        6,
                        "Morning! I finished the draft of the talk.",
                        time = "9:39 AM",
                        isOutgoing = true,
                        status = OutgoingStatus.Read,
                        position = GroupPosition.First,
                        isEdited = true,
                    ),
                    ChatItem.DateSeparator(epochDay = 2, date = today),
                    bubble(
                        5,
                        "Good night 🌙",
                        time = "11:02 PM",
                        sender = UiText.Plain("Ada"),
                        senderId = ADA,
                        date = yesterday,
                    ),
                    bubble(
                        4,
                        "Thanks, that helps a lot!",
                        time = "10:58 PM",
                        isOutgoing = true,
                        status = OutgoingStatus.Read,
                        date = yesterday,
                    ),
                    ChatItem.DateSeparator(epochDay = 1, date = yesterday),
                ),
            unreadCount = 2,
        )

    val groupChat =
        ChatUiState(
            header =
                ChatHeader(
                    chatId = -100L,
                    title = UiText.Plain("Compose Developers"),
                    photo = null,
                    placeholderIcon = null,
                    subtitle =
                        ChatSubtitle.Action(
                            UiText.resource(UiR.string.named_action_typing, UiText.Plain("Grace")),
                        ),
                ),
            content =
                content(
                    bubble(
                        26,
                        "Thanks! Reading it now.",
                        time = "10:21 AM",
                        isOutgoing = true,
                        status = OutgoingStatus.Read,
                        reply =
                            ReplyQuote(
                                UiText.Plain("Linus Torvalds"),
                                LINUS,
                                UiText.Plain("Here's the write-up on the new lazy layouts"),
                            ),
                        reactions = reactions(ReactionChip(ReactionLabel.Emoji("👍"), 2, isChosen = false)),
                    ),
                    bubble(
                        25,
                        LINK_TEXT,
                        time = "10:20 AM",
                        sender = UiText.Plain("Linus Torvalds"),
                        senderId = LINUS,
                        groupChat = true,
                        entities = listOf(TextEntity(LINK_TEXT.indexOf(LINK), LINK.length, TextEntityType.Url)),
                        linkPreview =
                            LinkPreviewCard(
                                siteName = "Android Developers",
                                title = "Jetpack Compose UI App Development Toolkit",
                                description = LINK_DESCRIPTION,
                            ),
                    ),
                    bubble(
                        24,
                        "Has anyone tried the expressive motion scheme yet?",
                        time = "10:12 AM",
                        sender = UiText.Plain("Grace Hopper"),
                        senderId = GRACE,
                        groupChat = true,
                        position = GroupPosition.Last,
                        reply = ReplyQuote(null, null, UiText.resource(R.string.chat_reply_deleted)),
                        reactions =
                            reactions(
                                ReactionChip(ReactionLabel.Emoji("🔥"), 5, isChosen = true),
                                ReactionChip(ReactionLabel.Emoji("❤️"), 3, isChosen = false),
                                ReactionChip(ReactionLabel.CustomEmoji, 1, isChosen = false),
                            ),
                    ),
                    bubble(
                        23,
                        "The springs feel great on the FAB menu.",
                        time = "10:11 AM",
                        sender = UiText.Plain("Grace Hopper"),
                        senderId = GRACE,
                        groupChat = true,
                        position = GroupPosition.First,
                        forwardedFrom = UiText.resource(R.string.chat_forwarded_from, UiText.Plain("Material Design")),
                    ),
                    ChatItem.Service(22, UiText.resource(UiR.string.preview_joined, UiText.Plain("Linus")), today),
                    ChatItem.DateSeparator(epochDay = 2, date = today),
                ),
        )

    val channel =
        ChatUiState(
            header =
                ChatHeader(
                    chatId = -200L,
                    title = UiText.Plain("Material Design"),
                    photo = null,
                    placeholderIcon = null,
                    subtitle = ChatSubtitle.Status(UiText.plural(R.plurals.chat_subtitle_subscribers, 12_480, 12_480)),
                ),
            content =
                content(
                    bubble(
                        32,
                        "Material 3 Expressive brings new shapes, motion and type to every component.",
                        time = "8:00 AM",
                        sender = UiText.Plain("Material Design"),
                        senderId = -200L,
                        reactions =
                            reactions(
                                ReactionChip(ReactionLabel.Emoji("🎉"), 1_204, isChosen = false),
                                ReactionChip(ReactionLabel.Paid, 12, isChosen = false),
                            ),
                    ),
                    bubble(
                        31,
                        "",
                        time = "7:30 AM",
                        sender = UiText.Plain("Material Design"),
                        senderId = -200L,
                        content = photo,
                        contentLabel = UiText.resource(UiR.string.preview_photo),
                    ),
                    ChatItem.DateSeparator(epochDay = 2, date = today),
                ),
        )

    val serviceMessages =
        ChatUiState(
            header = groupChat.header,
            content =
                content(
                    ChatItem.Service(44, UiText.resource(UiR.string.preview_you_pinned), today),
                    ChatItem.Service(
                        43,
                        UiText.resource(UiR.string.preview_added_member, UiText.Plain("Ada"), UiText.Plain("Grace")),
                        today,
                    ),
                    ChatItem.DateSeparator(epochDay = 2, date = today),
                    ChatItem.Service(
                        42,
                        UiText.resource(
                            UiR.string.preview_changed_title,
                            UiText.Plain("Ada"),
                            UiText.Plain("Compose Developers"),
                        ),
                        yesterday,
                    ),
                    ChatItem.Service(
                        41,
                        UiText.resource(UiR.string.preview_created_group, UiText.Plain("Ada"), UiText.Plain("Compose")),
                        yesterday,
                    ),
                    ChatItem.DateSeparator(epochDay = 1, date = yesterday),
                ),
        )

    /** Every kind of entity, in one message each for incoming and outgoing bubbles. */
    val formattedText: ChatUiState
        get() {
            val text = FORMATTED_TEXT
            val entities =
                listOf(
                    "bold" to TextEntityType.Bold,
                    "italic" to TextEntityType.Italic,
                    "underline" to TextEntityType.Underline,
                    "strikethrough" to TextEntityType.Strikethrough,
                    "inline code" to TextEntityType.Code,
                    "a spoiler" to TextEntityType.Spoiler,
                    "a link" to TextEntityType.TextUrl("https://example.org"),
                    "example.org" to TextEntityType.Url,
                    "@pingogram" to TextEntityType.Mention,
                    "#compose" to TextEntityType.Hashtag,
                    "\$USD" to TextEntityType.Cashtag,
                    "/start" to TextEntityType.BotCommand,
                    "ada@example.org" to TextEntityType.EmailAddress,
                    "+1 555 0100" to TextEntityType.PhoneNumber,
                    "A quoted line." to TextEntityType.BlockQuote,
                    "val answer = 42" to TextEntityType.PreCode("kotlin"),
                ).map { (part, type) -> TextEntity(text.indexOf(part), part.length, type) }
            return ChatUiState(
                header = privateChat.header,
                content =
                    content(
                        bubble(
                            52,
                            text,
                            time = "9:41 AM",
                            isOutgoing = true,
                            status = OutgoingStatus.Read,
                            entities = entities,
                        ),
                        bubble(
                            51,
                            text,
                            time = "9:40 AM",
                            sender = UiText.Plain("Ada"),
                            senderId = ADA,
                            entities = entities,
                        ),
                        ChatItem.DateSeparator(epochDay = 2, date = today),
                    ),
            )
        }

    val loading = ChatUiState(header = privateChat.header, content = ChatContent.Loading)

    val empty = ChatUiState(header = privateChat.header, content = ChatContent.Empty)

    val error = ChatUiState(header = privateChat.header, content = ChatContent.Error(DomainError.Network))

    val notFound = ChatUiState(content = ChatContent.NotFound)

    private const val LINK = "https://developer.android.com/compose"
    private const val LINK_TEXT = "Here's the write-up on the new lazy layouts: $LINK"
    private const val LINK_DESCRIPTION =
        "Jetpack Compose is Android's recommended modern toolkit for building native UI."

    private const val FORMATTED_TEXT =
        "Text can be bold, italic, underline, strikethrough or inline code, hide a spoiler, and hold a link, " +
            "example.org, @pingogram, #compose, \$USD, /start, ada@example.org and +1 555 0100.\n" +
            "A quoted line.\n" +
            "val answer = 42"

    private fun content(vararg items: ChatItem) =
        ChatContent.Content(
            items = items.toList().toImmutableList(),
            reachedStart = true,
            isLoadingOlder = false,
            olderError = null,
        )

    private fun reactions(vararg chips: ReactionChip): ImmutableList<ReactionChip> = chips.toList().toImmutableList()

    @Suppress("LongParameterList") // One parameter per thing a sample bubble can show, all with defaults.
    private fun bubble(
        id: Long,
        text: String,
        time: String,
        isOutgoing: Boolean = false,
        sender: UiText = you,
        senderId: Long = 0,
        groupChat: Boolean = false,
        position: GroupPosition = GroupPosition.Single,
        status: OutgoingStatus? = null,
        isEdited: Boolean = false,
        forwardedFrom: UiText? = null,
        reply: ReplyQuote? = null,
        entities: List<TextEntity> = emptyList(),
        content: MessageContent? = null,
        contentLabel: UiText? = null,
        linkPreview: LinkPreviewCard? = null,
        reactions: ImmutableList<ReactionChip> = persistentListOf(),
        date: UiText = today,
    ): ChatItem.Bubble {
        val formatted = FormattedText(text, entities.toImmutableList())
        val showsSender = groupChat && !isOutgoing
        return ChatItem.Bubble(
            id = id,
            isOutgoing = isOutgoing,
            position = position,
            sender = sender,
            senderId = senderId,
            showsSenderName = showsSender && !position.joinsPrevious,
            avatar = if (showsSender && !position.joinsNext) SenderAvatar(senderId, sender, null, null) else null,
            hasAvatarColumn = showsSender,
            time = time,
            isEdited = isEdited,
            status = status,
            forwardedFrom = forwardedFrom,
            reply = reply,
            content = content ?: MessageContent.Text(formatted, linkPreview = null),
            contentLabel = contentLabel,
            text = formatted,
            spoilersRevealed = false,
            linkPreview = linkPreview,
            reactions = reactions,
            date = date,
        )
    }
}
