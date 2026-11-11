package dev.kyralabs.pingogram.core.demo

import org.drinkless.tdlib.TdApi
import java.io.File
import java.net.URLConnection

/**
 * The demo's messages (`docs/adr/0034-demo-mode-for-store-review.md`): history, sending every kind of content the
 * composer sends (the files are the demo's copies, kept on the phone), albums, forwarding between demo chats, edits,
 * pins, scheduled messages, reactions, votes and deletions, as TDLib answers them.
 *
 * One branch per function and one builder per kind of content: hence the suppressions.
 */
@Suppress("TooManyFunctions", "CyclomaticComplexMethod", "LongMethod", "ReturnCount")
internal class DemoMessages : DemoArea {
    override fun answer(
        world: DemoWorld,
        function: TdApi.Function<*>,
    ): DemoAnswer? =
        when (function) {
            is TdApi.GetChatHistory -> {
                ok(
                    messages(
                        world.history(function.chatId, function.fromMessageId, function.offset, function.limit),
                    ),
                )
            }

            is TdApi.GetMessage -> {
                world.message(function.chatId, function.messageId)?.let(::ok) ?: notFound()
            }

            is TdApi.GetMessages -> {
                ok(
                    TdApi.Messages(
                        function.messageIds.size,
                        function.messageIds
                            .toList()
                            .map { world.message(function.chatId, it) }
                            .toTypedArray(),
                    ),
                )
            }

            is TdApi.GetRepliedMessage -> {
                val reply = world.message(function.chatId, function.messageId)?.replyTo as? TdApi.MessageReplyToMessage
                reply?.let { world.message(it.chatId, it.messageId) }?.let(::ok) ?: notFound()
            }

            is TdApi.GetMessageProperties -> {
                val chat = world.chat(function.chatId)
                val message = world.message(function.chatId, function.messageId)
                if (chat == null || message == null) notFound() else ok(properties(world, chat, message))
            }

            is TdApi.GetMessageAvailableReactions -> {
                ok(availableReactions())
            }

            is TdApi.GetChatScheduledMessages -> {
                ok(messages(world.scheduledIn(function.chatId).sortedBy(::sendDate)))
            }

            is TdApi.GetMessageLink -> {
                messageLink(world, function.chatId, function.messageId)
            }

            is TdApi.SendMessage -> {
                send(world, function)
            }

            is TdApi.SendMessageAlbum -> {
                album(world, function)
            }

            is TdApi.ForwardMessages -> {
                forward(world, function)
            }

            is TdApi.ResendMessages -> {
                ok(messages(function.messageIds.toList().mapNotNull { world.message(function.chatId, it) }))
            }

            is TdApi.EditMessageText -> {
                editText(world, function)
            }

            is TdApi.EditMessageCaption -> {
                editCaption(world, function)
            }

            is TdApi.EditMessageMedia -> {
                editMedia(world, function)
            }

            is TdApi.EditMessageSchedulingState -> {
                reschedule(world, function)
            }

            is TdApi.PinChatMessage -> {
                okChanged(world.pinMessage(function.chatId, function.messageId, true))
            }

            is TdApi.UnpinChatMessage -> {
                okChanged(world.pinMessage(function.chatId, function.messageId, false))
            }

            is TdApi.UnpinAllChatMessages -> {
                okChanged(world.unpinAll(function.chatId))
            }

            is TdApi.ViewMessages -> {
                okChanged(world.read(function.chatId, function.messageIds.maxOrNull() ?: 0))
            }

            is TdApi.AddMessageReaction -> {
                reaction(world, function.chatId, function.messageId, function.reactionType, added = true)
            }

            is TdApi.RemoveMessageReaction -> {
                reaction(world, function.chatId, function.messageId, function.reactionType, added = false)
            }

            is TdApi.SetPollAnswer -> {
                okChanged(world.vote(function.chatId, function.messageId, function.optionIds.toList()))
            }

            is TdApi.StopPoll -> {
                okChanged(world.stopPoll(function.chatId, function.messageId))
            }

            is TdApi.DeleteMessages -> {
                okChanged(world.delete(function.chatId, function.messageIds.toList()))
            }

            else -> {
                null
            }
        }

    private fun send(
        world: DemoWorld,
        function: TdApi.SendMessage,
    ): DemoAnswer {
        val chat = world.chat(function.chatId) ?: return notFound()
        if (!canSend(world, chat)) return needsAccount()
        val content = content(world, function.inputMessageContent) ?: return needsAccount()
        val (message, updates) =
            world.addOwn(
                chat.id,
                content,
                replyTo(world, chat.id, function.replyTo),
                forwardInfo = forwarded(world, function.inputMessageContent),
                schedulingState = function.options?.schedulingState,
                topic = function.topicId,
            ) ?: return notFound()
        return changed(message, updates)
    }

    private fun album(
        world: DemoWorld,
        function: TdApi.SendMessageAlbum,
    ): DemoAnswer {
        val chat = world.chat(function.chatId) ?: return notFound()
        if (!canSend(world, chat)) return needsAccount()
        val contents = function.inputMessageContents.map { content(world, it) ?: return needsAccount() }
        val albumId = world.nextAlbumId()
        val replyTo = replyTo(world, chat.id, function.replyTo)
        val sent =
            contents.mapNotNull { content ->
                world.addOwn(
                    chat.id,
                    content,
                    replyTo,
                    schedulingState = function.options?.schedulingState,
                    albumId = albumId,
                    topic = function.topicId,
                )
            }
        return changed(messages(sent.map { it.first }), sent.flatMap { it.second })
    }

    private fun forward(
        world: DemoWorld,
        function: TdApi.ForwardMessages,
    ): DemoAnswer {
        val chat = world.chat(function.chatId) ?: return notFound()
        if (!canSend(world, chat)) return needsAccount()
        val sent =
            function.messageIds.toList().mapNotNull { world.message(function.fromChatId, it) }.mapNotNull { source ->
                val content = copyOf(source.content)
                if (function.removeCaption) setCaption(content, TdApi.FormattedText("", emptyArray()))
                val info = if (function.sendCopy) null else forwardInfoOf(source)
                world.addOwn(
                    chat.id,
                    content,
                    forwardInfo = info,
                    schedulingState = function.options?.schedulingState,
                    topic = function.topicId,
                )
            }
        return changed(messages(sent.map { it.first }), sent.flatMap { it.second })
    }

    private fun editText(
        world: DemoWorld,
        function: TdApi.EditMessageText,
    ): DemoAnswer {
        val message = ownMessage(world, function.chatId, function.messageId) ?: return notFound()
        val input = function.inputMessageContent as? TdApi.InputMessageText ?: return needsAccount()
        if (message.content !is TdApi.MessageText) return badRequest("MESSAGE_NOT_MODIFIED")
        val updates =
            world.edit(
                message.chatId,
                message.id,
                TdApi.MessageText(input.text, null, input.linkPreviewOptions),
            )
        return changed(message, updates)
    }

    private fun editCaption(
        world: DemoWorld,
        function: TdApi.EditMessageCaption,
    ): DemoAnswer {
        val message = ownMessage(world, function.chatId, function.messageId) ?: return notFound()
        val content = copyOf(message.content)
        if (!setCaption(content, function.caption ?: TdApi.FormattedText("", emptyArray()))) {
            return badRequest("MESSAGE_NOT_MODIFIED")
        }
        return changed(message, world.edit(message.chatId, message.id, content))
    }

    private fun editMedia(
        world: DemoWorld,
        function: TdApi.EditMessageMedia,
    ): DemoAnswer {
        val message = ownMessage(world, function.chatId, function.messageId) ?: return notFound()
        val content = content(world, function.inputMessageContent) ?: return needsAccount()
        return changed(message, world.edit(message.chatId, message.id, content))
    }

    private fun reschedule(
        world: DemoWorld,
        function: TdApi.EditMessageSchedulingState,
    ): DemoAnswer {
        val state = function.schedulingState
        if (state == null) return okChanged(world.sendScheduledNow(function.chatId, function.messageId))
        return if (world.reschedule(function.chatId, function.messageId, state)) okChanged(emptyList()) else notFound()
    }

    private fun reaction(
        world: DemoWorld,
        chatId: Long,
        messageId: Long,
        type: TdApi.ReactionType,
        added: Boolean,
    ): DemoAnswer {
        val emoji = (type as? TdApi.ReactionTypeEmoji)?.emoji ?: return needsAccount()
        return okChanged(world.react(chatId, messageId, emoji, added))
    }

    private fun messageLink(
        world: DemoWorld,
        chatId: Long,
        messageId: Long,
    ): DemoAnswer {
        val type = world.chat(chatId)?.type as? TdApi.ChatTypeSupergroup ?: return needsAccount()
        world.message(chatId, messageId) ?: return notFound()
        val number = messageId shr DemoData.MESSAGE_ID_SHIFT
        val username =
            world.groups.supergroups[type.supergroupId]
                ?.usernames
                ?.editableUsername
                .orEmpty()
        return if (username.isNotEmpty()) {
            ok(TdApi.MessageLink("https://t.me/$username/$number", true))
        } else {
            ok(TdApi.MessageLink("https://t.me/c/${type.supergroupId}/$number", false))
        }
    }

    /** What a message lets the user do, as TDLib tells it: the demo's own messages can be edited, pinned, deleted. */
    private fun properties(
        world: DemoWorld,
        chat: TdApi.Chat,
        message: TdApi.Message,
    ) = TdApi.MessageProperties().apply {
        val content = message.content
        val inChannel = (chat.type as? TdApi.ChatTypeSupergroup)?.isChannel == true
        val owner = world.groups.statusOf(chat.id) is TdApi.ChatMemberStatusCreator
        canBeCopied = true
        canBeCopiedToSecretChat = true
        canBeForwarded = true
        canBeSaved = true
        canBeReplied = canSend(world, chat) && message.schedulingState == null
        canBeRepliedInAnotherChat = message.schedulingState == null
        canBeDeletedOnlyForSelf = !inChannel
        canBeDeletedForAllUsers = message.isOutgoing || owner
        canBeEdited = message.isOutgoing && (content is TdApi.MessageText || captionOf(content) != null)
        canEditMedia = message.isOutgoing && isReplaceableMedia(content)
        canEditSchedulingState = message.schedulingState != null
        canBePinned = (!inChannel || owner) && message.schedulingState == null
        canGetLink = chat.type is TdApi.ChatTypeSupergroup
        canReportChat = !message.isOutgoing
        canGetViewers = chat.id == DemoData.GROUP_CHAT && message.isOutgoing && message.schedulingState == null
        customEmojiIds = longArrayOf()
    }

    /** Whether the user may post: where the chat lets members write, in Saved Messages, and as a channel's owner. */
    private fun canSend(
        world: DemoWorld,
        chat: TdApi.Chat,
    ) = chat.permissions.canSendBasicMessages ||
        chat.id == DemoData.ME ||
        world.groups.statusOf(chat.id) is TdApi.ChatMemberStatusCreator

    private fun ownMessage(
        world: DemoWorld,
        chatId: Long,
        messageId: Long,
    ): TdApi.Message? = world.message(chatId, messageId)?.takeIf { it.isOutgoing }

    private fun replyTo(
        world: DemoWorld,
        chatId: Long,
        input: TdApi.InputMessageReplyTo?,
    ): TdApi.MessageReplyTo? =
        when (input) {
            is TdApi.InputMessageReplyToMessage -> {
                world.message(chatId, input.messageId)?.let {
                    TdApi.MessageReplyToMessage(chatId, it.id, quoteOf(input.quote), 0, "", null, 0, null)
                }
            }

            is TdApi.InputMessageReplyToExternalMessage -> {
                world.message(input.chatId, input.messageId)?.let {
                    TdApi.MessageReplyToMessage(
                        it.chatId,
                        it.id,
                        quoteOf(input.quote),
                        0,
                        "",
                        originOf(it),
                        it.date,
                        copyOf(it.content),
                    )
                }
            }

            else -> {
                null
            }
        }

    private fun quoteOf(quote: TdApi.InputTextQuote?) = quote?.let { TdApi.TextQuote(it.text, it.richTexts, it.position, true) }

    /** A message "forwarded" through `inputMessageForwarded` (a forward of one message with a caption kept or not). */
    private fun forwarded(
        world: DemoWorld,
        input: TdApi.InputMessageContent,
    ): TdApi.MessageForwardInfo? {
        val forwarded = input as? TdApi.InputMessageForwarded ?: return null
        if (forwarded.copyOptions?.sendCopy == true) return null
        return world.message(forwarded.fromChatId, forwarded.messageId)?.let(::forwardInfoOf)
    }

    private fun forwardInfoOf(source: TdApi.Message) =
        TdApi.MessageForwardInfo(
            source.forwardInfo?.origin ?: originOf(source),
            source.forwardInfo?.date ?: source.date,
            null,
            "",
        )

    private fun originOf(message: TdApi.Message): TdApi.MessageOrigin =
        when (val sender = message.senderId) {
            is TdApi.MessageSenderChat -> TdApi.MessageOriginChannel(sender.chatId, message.id, "")
            is TdApi.MessageSenderUser -> TdApi.MessageOriginUser(sender.userId)
            else -> TdApi.MessageOriginHiddenUser("")
        }

    /** The content a message gets from what the composer sent; null for what the demo cannot show. */
    private fun content(
        world: DemoWorld,
        input: TdApi.InputMessageContent,
    ): TdApi.MessageContent? =
        when (input) {
            is TdApi.InputMessageText -> {
                TdApi.MessageText(input.text, null, input.linkPreviewOptions)
            }

            is TdApi.InputMessagePhoto -> {
                val file = file(world, input.photo.photo) ?: return null
                val size = TdApi.PhotoSize("x", file, input.photo.width, input.photo.height, intArrayOf())
                TdApi.MessagePhoto(
                    TdApi.Photo(false, null, arrayOf(size)),
                    null,
                    caption(input.caption),
                    input.showCaptionAboveMedia,
                    input.hasSpoiler,
                    false,
                )
            }

            is TdApi.InputMessageVideo -> {
                val video = input.video
                val file = file(world, video.video) ?: return null
                TdApi.MessageVideo(
                    TdApi.Video(
                        video.duration,
                        video.width,
                        video.height,
                        nameOf(file),
                        mimeOf(file, "video/mp4"),
                        false,
                        video.supportsStreaming,
                        null,
                        thumbnail(world, video.thumbnail),
                        file,
                    ),
                    emptyArray(),
                    emptyArray(),
                    null,
                    0,
                    caption(input.caption),
                    input.showCaptionAboveMedia,
                    input.hasSpoiler,
                    false,
                )
            }

            is TdApi.InputMessageDocument -> {
                val file = file(world, input.document.document) ?: return null
                TdApi.MessageDocument(
                    TdApi.Document(
                        nameOf(file),
                        mimeOf(file, "application/octet-stream"),
                        null,
                        thumbnail(world, input.document.thumbnail),
                        file,
                    ),
                    caption(input.caption),
                )
            }

            is TdApi.InputMessageVoiceNote -> {
                val note = input.voiceNote
                val file = file(world, note.voiceNote) ?: return null
                TdApi.MessageVoiceNote(
                    TdApi.VoiceNote(
                        note.duration,
                        note.waveform ?: ByteArray(0),
                        mimeOf(file, "audio/ogg"),
                        null,
                        file,
                    ),
                    caption(input.caption),
                    true,
                )
            }

            is TdApi.InputMessageVideoNote -> {
                val note = input.videoNote
                val file = file(world, note.videoNote) ?: return null
                TdApi.MessageVideoNote(
                    TdApi.VideoNote(
                        note.duration,
                        ByteArray(0),
                        note.length,
                        null,
                        thumbnail(world, note.thumbnail),
                        null,
                        file,
                    ),
                    true,
                    false,
                )
            }

            is TdApi.InputMessageAnimation -> {
                val animation = input.animation
                val file = file(world, animation.animation) ?: return null
                TdApi.MessageAnimation(
                    TdApi.Animation(
                        animation.duration,
                        animation.width,
                        animation.height,
                        nameOf(file),
                        mimeOf(file, "video/mp4"),
                        false,
                        null,
                        thumbnail(world, animation.thumbnail),
                        file,
                    ),
                    caption(input.caption),
                    input.showCaptionAboveMedia,
                    input.hasSpoiler,
                    false,
                )
            }

            is TdApi.InputMessageAudio -> {
                val audio = input.audio
                val file = file(world, audio.audio) ?: return null
                TdApi.MessageAudio(
                    TdApi.Audio(
                        audio.duration,
                        audio.title.orEmpty(),
                        audio.performer.orEmpty(),
                        nameOf(file),
                        mimeOf(file, "audio/mpeg"),
                        null,
                        null,
                        emptyArray(),
                        file,
                    ),
                    caption(input.caption),
                )
            }

            is TdApi.InputMessageSticker -> {
                val id = (input.sticker.sticker as? TdApi.InputFileId)?.id ?: return null
                world.data.stickerByFileId(id)?.let { TdApi.MessageSticker(it, false) }
            }

            is TdApi.InputMessageLocation -> {
                TdApi.MessageLocation(input.location)
            }

            is TdApi.InputMessageContact -> {
                TdApi.MessageContact(input.contact)
            }

            is TdApi.InputMessagePoll -> {
                poll(world, input)
            }

            is TdApi.InputMessageForwarded -> {
                world.message(input.fromChatId, input.messageId)?.let { copyOf(it.content) }
            }

            else -> {
                null
            }
        }

    private fun poll(
        world: DemoWorld,
        input: TdApi.InputMessagePoll,
    ): TdApi.MessagePoll {
        val poll =
            DemoPolls.poll(world.nextPollId(), input.question.text, input.options.map { it.text.text to 0 }).apply {
                isAnonymous = input.isAnonymous
                allowsMultipleAnswers = input.allowsMultipleAnswers
                allowsRevoting = input.allowsRevoting
                type =
                    when (val kind = input.type) {
                        is TdApi.InputPollTypeQuiz -> TdApi.PollTypeQuiz(kind.correctOptionIds, kind.explanation, null)
                        else -> TdApi.PollTypeRegular()
                    }
            }
        return TdApi.MessagePoll(poll, input.description ?: TdApi.FormattedText("", emptyArray()), null, false)
    }

    private fun file(
        world: DemoWorld,
        input: TdApi.InputFile?,
    ): TdApi.File? =
        when (input) {
            is TdApi.InputFileLocal -> world.sentFile(input.path)
            is TdApi.InputFileId -> world.knownFile(input.id)
            else -> null
        }

    private fun thumbnail(
        world: DemoWorld,
        input: TdApi.InputThumbnail?,
    ): TdApi.Thumbnail? {
        val file = file(world, input?.thumbnail) ?: return null
        return TdApi.Thumbnail(TdApi.ThumbnailFormatJpeg(), input!!.width, input.height, file)
    }

    private fun caption(text: TdApi.FormattedText?) = text ?: TdApi.FormattedText("", emptyArray())

    private fun nameOf(file: TdApi.File) = File(file.local.path).name

    private fun mimeOf(
        file: TdApi.File,
        fallback: String,
    ): String = URLConnection.guessContentTypeFromName(nameOf(file)) ?: fallback

    private fun sendDate(message: TdApi.Message): Int =
        (message.schedulingState as? TdApi.MessageSchedulingStateSendAtDate)?.sendDate ?: Int.MAX_VALUE

    private fun messages(messages: List<TdApi.Message>) = TdApi.Messages(messages.size, messages.toTypedArray())

    private fun availableReactions(): TdApi.AvailableReactions {
        val reactions =
            DemoData.REACTIONS.map { TdApi.AvailableReaction(TdApi.ReactionTypeEmoji(it), false) }.toTypedArray()
        return TdApi.AvailableReactions(reactions, emptyArray(), emptyArray(), false, false, null)
    }

    private fun isReplaceableMedia(content: TdApi.MessageContent) =
        content is TdApi.MessagePhoto ||
            content is TdApi.MessageVideo ||
            content is TdApi.MessageDocument ||
            content is TdApi.MessageAudio ||
            content is TdApi.MessageAnimation

    private companion object {
        fun <T : TdApi.Object> copyOf(value: T): T = DemoCodec.decode(DemoCodec.encode(value))

        /** A content's caption, for the kinds that have one. */
        fun captionOf(content: TdApi.MessageContent): TdApi.FormattedText? =
            runCatching { content.javaClass.getField("caption").get(content) as? TdApi.FormattedText }.getOrNull()

        fun setCaption(
            content: TdApi.MessageContent,
            caption: TdApi.FormattedText,
        ): Boolean =
            runCatching {
                content.javaClass.getField("caption").set(content, caption)
                true
            }.getOrDefault(false)
    }
}
