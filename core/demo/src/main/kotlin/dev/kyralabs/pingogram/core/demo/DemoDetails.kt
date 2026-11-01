package dev.kyralabs.pingogram.core.demo

import org.drinkless.tdlib.TdApi

/**
 * The details of the demo's messages (`docs/adr/0034-demo-mode-for-store-review.md`): who saw a message of the user's
 * in the group, who added each reaction, who voted for each answer of the public poll, and translations (the sample
 * message in Spanish turns into English; the others are in the app's language already). Made up from the sample
 * people, as Telegram would list them.
 *
 * Each function starts with guard clauses for what it does not know: hence the suppression.
 */
@Suppress("ReturnCount")
internal class DemoDetails : DemoArea {
    override fun answer(
        world: DemoWorld,
        function: TdApi.Function<*>,
    ): DemoAnswer? =
        when (function) {
            is TdApi.GetMessageViewers -> viewers(world, function.chatId, function.messageId)

            is TdApi.GetMessageAddedReactions -> addedReactions(world, function)

            is TdApi.GetPollVoters -> pollVoters(world, function)

            is TdApi.TranslateMessageText -> translate(world, function)

            // The demo's texts have no custom emoji.
            is TdApi.GetCustomEmojiStickers -> ok(TdApi.Stickers(emptyArray()))

            else -> null
        }

    /** The group's other members saw the user's message a minute apart, the latest first. */
    private fun viewers(
        world: DemoWorld,
        chatId: Long,
        messageId: Long,
    ): DemoAnswer {
        val message = world.message(chatId, messageId) ?: return notFound()
        if (chatId != DemoData.GROUP_CHAT || !message.isOutgoing) return badRequest("MSG_TOO_OLD")
        val others = DemoData.GROUP_MEMBERS.filter { it != DemoData.ME }
        val viewers =
            others.mapIndexed {
                index,
                user,
                ->
                TdApi.MessageViewer(user, message.date + (index + 1) * MINUTE)
            }
        return ok(TdApi.MessageViewers(viewers.asReversed().toTypedArray()))
    }

    /** Each reaction's senders: the user for the chosen one, then sample people, as many as its count. */
    private fun addedReactions(
        world: DemoWorld,
        function: TdApi.GetMessageAddedReactions,
    ): DemoAnswer {
        val message = world.message(function.chatId, function.messageId) ?: return notFound()
        val reactions =
            message.interactionInfo
                ?.reactions
                ?.reactions
                .orEmpty()
                .filterNotNull()
        val wanted = (function.reactionType as? TdApi.ReactionTypeEmoji)?.emoji
        val others = world.users.keys.filter { it != DemoData.ME }
        val added =
            reactions
                .filter { wanted == null || (it.type as? TdApi.ReactionTypeEmoji)?.emoji == wanted }
                .flatMap { reaction ->
                    val senders = (if (reaction.isChosen) listOf(DemoData.ME) else emptyList()) + others
                    senders.take(reaction.totalCount).mapIndexed { index, user ->
                        TdApi.AddedReaction(
                            reaction.type,
                            TdApi.MessageSenderUser(user),
                            user == DemoData.ME,
                            message.date + (index + 1) * MINUTE,
                        )
                    }
                }.sortedByDescending { it.date }
        val start = function.offset.toIntOrNull() ?: 0
        val page = added.drop(start).take(function.limit)
        val next = if (start + page.size < added.size) (start + page.size).toString() else ""
        return ok(TdApi.AddedReactions(added.size, page.toTypedArray(), next))
    }

    /** The sample poll's voters ([DemoData.POLL_VOTERS]) and the user, for the answer they chose. */
    private fun pollVoters(
        world: DemoWorld,
        function: TdApi.GetPollVoters,
    ): DemoAnswer {
        val message = world.message(function.chatId, function.messageId) ?: return notFound()
        val poll = (message.content as? TdApi.MessagePoll)?.poll ?: return notFound()
        if (poll.isAnonymous) return badRequest("POLL_ANONYMOUS")
        val option = poll.options.getOrNull(function.optionId) ?: return notFound()
        val sample =
            if (poll.id ==
                DemoData.POLL_ID
            ) {
                DemoData.POLL_VOTERS.getOrNull(function.optionId).orEmpty()
            } else {
                emptyList()
            }
        val voters = (if (option.isChosen) listOf(DemoData.ME) else emptyList()) + sample
        val page =
            voters.drop(function.offset).take(function.limit).mapIndexed { index, user ->
                TdApi.PollVoter(TdApi.MessageSenderUser(user), message.date + (index + 1) * MINUTE)
            }
        return ok(TdApi.PollVoters(voters.size, page.toTypedArray()))
    }

    private fun translate(
        world: DemoWorld,
        function: TdApi.TranslateMessageText,
    ): DemoAnswer {
        val message = world.message(function.chatId, function.messageId) ?: return notFound()
        val original =
            when (val content = message.content) {
                is TdApi.MessageText -> {
                    content.text
                }

                else -> {
                    runCatching {
                        content.javaClass
                            .getField(
                                "caption",
                            ).get(content) as? TdApi.FormattedText
                    }.getOrNull()
                }
            } ?: return badRequest("MESSAGE_EMPTY")
        val toSpanish = function.toLanguageCode.startsWith("es")
        return if (message.id == world.data.spanishMessageId && !toSpanish) {
            ok(TdApi.FormattedText(world.data.spanishTranslation, emptyArray()))
        } else {
            ok(original)
        }
    }

    private companion object {
        const val MINUTE = 60
    }
}
