package dev.kyralabs.pingogram.feature.chat.ui.preview

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.Photo
import dev.kyralabs.pingogram.core.domain.model.StickerFormat
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatContent
import dev.kyralabs.pingogram.feature.chat.ui.ChatHeader
import dev.kyralabs.pingogram.feature.chat.ui.ChatItem
import dev.kyralabs.pingogram.feature.chat.ui.ChatSubtitle
import dev.kyralabs.pingogram.feature.chat.ui.ChatUiState
import dev.kyralabs.pingogram.feature.chat.ui.DeleteMessagesDialog
import dev.kyralabs.pingogram.feature.chat.ui.DeleteScope
import dev.kyralabs.pingogram.feature.chat.ui.GroupPosition
import dev.kyralabs.pingogram.feature.chat.ui.LinkPreviewCard
import dev.kyralabs.pingogram.feature.chat.ui.MessageAction
import dev.kyralabs.pingogram.feature.chat.ui.MessageActionsSheet
import dev.kyralabs.pingogram.feature.chat.ui.MessageAlbum
import dev.kyralabs.pingogram.feature.chat.ui.MessagePlayback
import dev.kyralabs.pingogram.feature.chat.ui.OutgoingStatus
import dev.kyralabs.pingogram.feature.chat.ui.ReactionChip
import dev.kyralabs.pingogram.feature.chat.ui.ReactionLabel
import dev.kyralabs.pingogram.feature.chat.ui.ReplyQuote
import dev.kyralabs.pingogram.feature.chat.ui.SenderAvatar
import dev.kyralabs.pingogram.feature.chat.ui.attach.AttachOption
import dev.kyralabs.pingogram.feature.chat.ui.attach.MessageUpload
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerBar
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerUiState
import dev.kyralabs.pingogram.feature.chat.ui.content.ContentPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.content.Playback
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * Sample conversations for previews and screenshot tests: a private chat with grouping, every outgoing state and the
 * unread divider; a group with sender names, avatars, replies, forwards, reactions and a link preview; a channel;
 * service messages; a message with every kind of formatting; media of every frame (photos, an album, a sticker, a
 * voice message, a document, a poll, a venue), with the voice message also playing; a group where the user may not
 * write; the actions sheets of an own and an incoming message, the delete confirmation, and the composer replying and
 * editing; and the loading, empty, error and not-found states. The composer shows where the user can write.
 * Items are listed newest first, as the reverse layout shows them.
 */
internal object ChatPreviewData {
    val today = UiText.resource(R.string.chat_date_today)
    private val yesterday = UiText.resource(R.string.chat_date_yesterday)
    private val you = UiText.resource(UiR.string.preview_sender_you)

    /** The composer, where the user can write, with the attach options of a private chat. */
    val canWrite =
        ComposerUiState.Available(
            canSchedule = true,
            attachOptions = AttachOption.entries.filter { it != AttachOption.Poll }.toPersistentList(),
        )

    /** A photo without sizes, which no image loads for. Declared before the samples, which use it. */
    private val photo =
        MessageContent.Photo(
            photo = Photo(sizes = persistentListOf(), minithumbnail = null),
            caption = FormattedText.Empty,
            hasSpoiler = false,
            isSecret = false,
            showCaptionAboveMedia = false,
        )

    const val ADA = 101L
    private const val GRACE = 102L
    private const val LINUS = 103L

    val privateChat =
        ChatUiState(
            composer = canWrite,
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
                    ),
                    bubble(
                        7,
                        "Delivered, not read yet",
                        time = "9:40 AM",
                        isOutgoing = true,
                        status = OutgoingStatus.Sent,
                        position = GroupPosition.Last,
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
            composer = canWrite,
            header =
                ChatHeader(
                    chatId = -100L,
                    title = UiText.Plain("Compose Developers"),
                    photo = null,
                    placeholderIcon = null,
                    subtitle =
                        ChatSubtitle.Action(
                            UiText.resource(UiR.string.named_action_typing, isolated("Grace")),
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
                                messageId = 25,
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
                                ReactionChip(ReactionLabel.CustomEmoji(customEmojiId = 5), 1, isChosen = false),
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
                        forwardedFrom = UiText.resource(R.string.chat_forwarded_from, isolated("Material Design")),
                    ),
                    ChatItem.Service(22, UiText.resource(UiR.string.preview_joined, isolated("Linus")), today),
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
            composer = canWrite,
            header = groupChat.header,
            content =
                content(
                    ChatItem.Service(44, UiText.resource(UiR.string.preview_you_pinned), today),
                    ChatItem.Service(
                        43,
                        UiText.resource(UiR.string.preview_added_member, isolated("Ada"), isolated("Grace")),
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
                        UiText.resource(UiR.string.preview_created_group, isolated("Ada"), isolated("Compose")),
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
                composer = canWrite,
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

    val ada = UiText.Plain("Ada")
    private val albumItems = ContentPreviewData.album(count = 4, minithumbnail = null)

    /** Photos: one without a caption, one with, and an album of four, edge to edge in their bubbles. */
    val mediaPhotos =
        ChatUiState(
            composer = canWrite,
            header = privateChat.header,
            content =
                content(
                    bubble(
                        53,
                        "",
                        time = "9:44 AM",
                        sender = ada,
                        senderId = ADA,
                        content = albumItems.first(),
                        contentLabel = UiText.plural(R.plurals.content_album_media, albumItems.size, albumItems.size),
                        album = MessageAlbum(id = 77, messageIds = (50L..53L).toList().toImmutableList(), albumItems),
                    ),
                    bubble(
                        49,
                        "Sunset from the ferry 🌅",
                        time = "9:41 AM",
                        isOutgoing = true,
                        status = OutgoingStatus.Read,
                        content =
                            ContentPreviewData
                                .photo(minithumbnail = null, width = PORTRAIT_WIDTH, height = PORTRAIT_HEIGHT)
                                .copy(caption = FormattedText("Sunset from the ferry 🌅", persistentListOf())),
                        contentLabel = UiText.resource(UiR.string.preview_photo),
                    ),
                    bubble(
                        48,
                        "",
                        time = "9:40 AM",
                        sender = ada,
                        senderId = ADA,
                        content = ContentPreviewData.photo(minithumbnail = null, firstFileId = SECOND_PHOTO_FILE_ID),
                        contentLabel = UiText.resource(UiR.string.preview_photo),
                    ),
                    ChatItem.DateSeparator(epochDay = 2, date = today),
                ),
        )

    private val sampleDocument = ContentPreviewData.document("photos-from-the-trip.zip", "application/zip")

    private val uploadAlbum = albumItems.take(2).toImmutableList()

    /**
     * Photos and a file being sent from this device: an album whose first photo uploads and whose second has not
     * started, and a document that uploads.
     */
    val uploading =
        ChatUiState(
            composer = canWrite,
            header = privateChat.header,
            content =
                content(
                    bubble(
                        73,
                        "",
                        time = "10:12 AM",
                        isOutgoing = true,
                        status = OutgoingStatus.Pending,
                        content = sampleDocument,
                        contentLabel = UiText.Plain(sampleDocument.document.fileName),
                    ),
                    bubble(
                        72,
                        "",
                        time = "10:11 AM",
                        isOutgoing = true,
                        status = OutgoingStatus.Pending,
                        content = uploadAlbum.first(),
                        contentLabel = UiText.plural(R.plurals.content_album_media, uploadAlbum.size, uploadAlbum.size),
                        album = MessageAlbum(id = 78, messageIds = persistentListOf(71L, 72L), uploadAlbum),
                    ),
                    ChatItem.DateSeparator(epochDay = 2, date = today),
                ),
            uploads =
                persistentMapOf(
                    71L to MessageUpload(progress = 0.4f),
                    72L to MessageUpload(progress = null),
                    73L to MessageUpload(progress = 0.65f),
                ),
        )

    /** A sticker, a voice message, a document as it downloads, a poll and a venue. */
    val mediaFiles =
        ChatUiState(
            composer = canWrite,
            header = privateChat.header,
            content =
                content(
                    bubble(
                        66,
                        "",
                        time = "10:05 AM",
                        isOutgoing = true,
                        status = OutgoingStatus.Sent,
                        content = ContentPreviewData.venue,
                        contentLabel = UiText.resource(UiR.string.preview_location),
                    ),
                    bubble(
                        65,
                        "",
                        time = "10:04 AM",
                        sender = ada,
                        senderId = ADA,
                        content = ContentPreviewData.poll(voted = true),
                        contentLabel = UiText.Plain("Best season?"),
                    ),
                    bubble(
                        64,
                        "",
                        time = "10:02 AM",
                        sender = ada,
                        senderId = ADA,
                        content = sampleDocument,
                        contentLabel = UiText.Plain(sampleDocument.document.fileName),
                    ),
                    bubble(
                        63,
                        "",
                        time = "10:01 AM",
                        isOutgoing = true,
                        status = OutgoingStatus.Read,
                        content = ContentPreviewData.voiceNote(isListened = true),
                        contentLabel = UiText.resource(UiR.string.preview_voice_message),
                    ),
                    bubble(
                        62,
                        "",
                        time = "10:00 AM",
                        sender = ada,
                        senderId = ADA,
                        content = ContentPreviewData.sticker(StickerFormat.Webp),
                        contentLabel = UiText.resource(UiR.string.preview_sticker),
                    ),
                    ChatItem.DateSeparator(epochDay = 2, date = today),
                ),
            fileStates =
                persistentMapOf(
                    sampleDocument.document.file.id to
                        FileState.Downloading(DOWNLOADED_BYTES, sampleDocument.document.file.size, isActive = true),
                ),
        )

    /** [mediaFiles] while its voice message plays, two fifths in. */
    val voicePlaying =
        mediaFiles.copy(
            playback = MessagePlayback(messageId = 63, playback = Playback(isPlaying = true, progress = { PLAYED })),
        )

    val loading = ChatUiState(header = privateChat.header, content = ChatContent.Loading, composer = canWrite)

    val empty = ChatUiState(header = privateChat.header, content = ChatContent.Empty, composer = canWrite)

    val error =
        ChatUiState(header = privateChat.header, content = ChatContent.Error(DomainError.Network), composer = canWrite)

    /** The actions sheet of the user's own message 6 in [privateChat]: everything, with pinning for oneself. */
    val ownMessageActions =
        MessageActionsSheet(
            messageId = 6,
            reactionsMessageId = 6,
            reactions = ReactionPreviewData.reactionOptions(ReactionPreviewData.topReactions),
            actions =
                persistentListOf(
                    MessageAction.Reply,
                    MessageAction.Copy,
                    MessageAction.Forward,
                    MessageAction.Edit,
                    MessageAction.Pin,
                    MessageAction.PinForSelf,
                    MessageAction.Delete,
                ),
            picker = ReactionPreviewData.reactionPicker(onMessage = persistentListOf(), chosen = null),
        )

    /**
     * The actions sheet of Grace's message 24 in [groupChat], whose fire the user chose: no editing. Its picker shows
     * the message's reactions, one a custom emoji, and a recent custom emoji, both without a picture.
     */
    val incomingMessageActions =
        MessageActionsSheet(
            messageId = 24,
            reactionsMessageId = 24,
            reactions = ReactionPreviewData.reactionOptions(ReactionPreviewData.topReactions, chosen = "🔥"),
            actions =
                persistentListOf(
                    MessageAction.Reply,
                    MessageAction.Copy,
                    MessageAction.Forward,
                    MessageAction.Pin,
                    MessageAction.Select,
                    MessageAction.Report,
                    MessageAction.Delete,
                ),
            picker =
                ReactionPreviewData.reactionPicker(
                    onMessage =
                        persistentListOf(
                            ReactionChip(ReactionLabel.Emoji("🔥"), 5, isChosen = true),
                            ReactionChip(ReactionLabel.Emoji("❤️"), 3, isChosen = false),
                            ReactionChip(ReactionLabel.CustomEmoji(customEmojiId = 5), 1, isChosen = false),
                        ),
                    chosen = "🔥",
                ),
        )

    /** Deleting message 6 of [privateChat], for Ada too if the user checks it. */
    val deleteForBoth =
        DeleteMessagesDialog(
            messageIds = persistentListOf(6),
            scope = DeleteScope.Choice(UiText.resource(R.string.message_delete_also_for, UiText.Plain("Ada Lovelace"))),
        )

    /** Deleting the album of [mediaPhotos], for everyone. */
    val deleteAlbumForEveryone =
        DeleteMessagesDialog(messageIds = (50L..53L).toList().toImmutableList(), scope = DeleteScope.ForEveryone)

    /** [privateChat] with the actions sheet of the user's own message. */
    val ownActionsSheet = privateChat.copy(messageActions = ownMessageActions)

    /** [groupChat] with the actions sheet of someone else's message. */
    val incomingActionsSheet = groupChat.copy(messageActions = incomingMessageActions)

    /** [incomingActionsSheet] at its reactions picker, as a bubble's "+N" or the sheet's expand button opens it. */
    val reactionPickerSheet = groupChat.copy(messageActions = incomingMessageActions.copy(showsPicker = true))

    /** [privateChat] asking before message 6 is deleted. */
    val deleteConfirmation = privateChat.copy(deleteDialog = deleteForBoth)

    /** [privateChat] replying to Ada's question. */
    val replying =
        privateChat.copy(
            composer =
                canWrite.copy(
                    bar =
                        ComposerBar.Reply(
                            messageId = 10,
                            sender = UiText.Plain("Ada Lovelace"),
                            preview = UiText.Plain("Is the meetup still on for tonight?"),
                        ),
                ),
        )

    /** [privateChat] editing the user's message 6, whose text fills the composer. */
    val editing =
        privateChat.copy(
            composer =
                canWrite.copy(
                    bar =
                        ComposerBar.Edit(
                            messageId = 6,
                            preview = UiText.Plain(EDITED_TEXT),
                            canBeEmpty = false,
                        ),
                    // An edit sends no attachments.
                    attachOptions = persistentListOf(),
                ),
        )

    /** The text of the message [editing] edits. */
    const val EDITED_TEXT = "Morning! I finished the draft of the talk."

    /** [groupChat] for a member the admins restricted: a label instead of the composer. */
    val cannotWrite =
        groupChat.copy(
            composer = ComposerUiState.Unavailable(UiText.resource(R.string.composer_restricted)),
        )

    val notFound = ChatUiState(content = ChatContent.NotFound)

    private const val PORTRAIT_WIDTH = 960
    private const val PORTRAIT_HEIGHT = 1280
    const val SECOND_PHOTO_FILE_ID = 40
    private const val DOWNLOADED_BYTES = 1_700_000L
    private const val PLAYED = 0.4f

    private const val LINK = "https://developer.android.com/compose"
    private const val LINK_TEXT = "Here's the write-up on the new lazy layouts: $LINK"
    private const val LINK_DESCRIPTION =
        "Jetpack Compose is Android's recommended modern toolkit for building native UI."

    private const val FORMATTED_TEXT =
        "Text can be bold, italic, underline, strikethrough or inline code, hide a spoiler, and hold a link, " +
            "example.org, @pingogram, #compose, \$USD, /start, ada@example.org and +1 555 0100.\n" +
            "A quoted line.\n" +
            "val answer = 42"

    fun content(vararg items: ChatItem) =
        ChatContent.Content(
            items = items.toList().toImmutableList(),
            reachedStart = true,
            isLoadingOlder = false,
            olderError = null,
        )

    fun reactions(vararg chips: ReactionChip): ImmutableList<ReactionChip> = chips.toList().toImmutableList()

    @Suppress("LongParameterList") // One parameter per thing a sample bubble can show, all with defaults.
    fun bubble(
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
        album: MessageAlbum? = null,
        isEmojiOnly: Boolean = false,
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
            showsTime = !position.joinsNext,
            canResend = status == OutgoingStatus.Failed,
            forwardedFrom = forwardedFrom,
            reply = reply,
            content = content ?: MessageContent.Text(formatted, linkPreview = null),
            contentLabel = contentLabel,
            text = formatted,
            spoilersRevealed = false,
            linkPreview = linkPreview,
            reactions = reactions,
            date = date,
            album = album,
            isEmojiOnly = isEmojiOnly,
        )
    }
}

/** A name as the mappers put it into a string: isolated (`bidiIsolated`). */
private fun isolated(name: String): UiText = UiText.Plain(name).bidiIsolated()
