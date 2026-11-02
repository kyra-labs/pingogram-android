package dev.kyralabs.pingogram.core.demo

import org.drinkless.tdlib.TdApi

/**
 * The sample bot (`docs/adr/0034-demo-mode-for-store-review.md`), which lives on the phone: "Start", its commands, a
 * message with inline buttons (a callback answered in a snackbar, one answered as an alert, a login button and a link,
 * both to `example.org`), a reply keyboard whose buttons ask for the user's number, location, a poll, a person and a
 * group (shared with nobody: the bot only thanks the user), and inline results when "@sample_login_bot" is typed in a
 * chat. It answers what the user sends it ([answer]), so its replies are replayed with the user's messages.
 *
 * One branch per function: hence the suppressions.
 */
@Suppress("CyclomaticComplexMethod", "ReturnCount")
internal class DemoBots : DemoArea {
    override fun answer(
        world: DemoWorld,
        function: TdApi.Function<*>,
    ): DemoAnswer? =
        when (function) {
            is TdApi.SendBotStartMessage -> {
                start(world, function.botUserId)
            }

            is TdApi.GetCallbackQueryAnswer -> {
                val data = (function.payload as? TdApi.CallbackQueryPayloadData)?.data?.decodeToString()
                val text = world.data.text
                when (data) {
                    HELLO -> ok(TdApi.CallbackQueryAnswer(text(R.string.demo_bot_hello_answer), false, ""))
                    ALERT -> ok(TdApi.CallbackQueryAnswer(text(R.string.demo_bot_alert_answer), true, ""))
                    else -> ok(TdApi.CallbackQueryAnswer("", false, ""))
                }
            }

            is TdApi.GetLoginUrlInfo -> {
                ok(TdApi.LoginUrlInfoRequestConfirmation(LOGIN_URL, LOGIN_DOMAIN, DemoData.LOGIN_BOT, true))
            }

            is TdApi.GetLoginUrl -> {
                ok(TdApi.HttpUrl(LOGIN_URL))
            }

            is TdApi.ShareUsersWithBot -> {
                val names =
                    function.sharedUserIds
                        .toList()
                        .mapNotNull { world.users[it] }
                        .map { user -> "${user.firstName} ${user.lastName}".trim() }
                shared(world, function.onlyCheck, names.joinToString())
            }

            is TdApi.ShareChatWithBot -> {
                shared(world, function.onlyCheck, world.chat(function.sharedChatId)?.title.orEmpty())
            }

            is TdApi.GetInlineQueryResults -> {
                if (function.botUserId != DemoData.LOGIN_BOT) notFound() else ok(results(world, function.query))
            }

            is TdApi.SendInlineQueryResultMessage -> {
                sendResult(world, function)
            }

            else -> {
                null
            }
        }

    private fun start(
        world: DemoWorld,
        botUserId: Long,
    ): DemoAnswer {
        if (botUserId != DemoData.LOGIN_BOT) return notFound()
        val (chat, made) = world.privateChat(botUserId) ?: return notFound()
        val sent = world.addOwn(chat.id, textContent("/start")) ?: return notFound()
        return okChanged(made + sent.second)
    }

    private fun shared(
        world: DemoWorld,
        onlyCheck: Boolean,
        what: String,
    ): DemoAnswer {
        if (onlyCheck) return ok(TdApi.Ok())
        val thanks = world.data.text(R.string.demo_bot_thanks_shared).format(what)
        val answer = world.addIncoming(DemoData.LOGIN_BOT, DemoData.LOGIN_BOT, textContent(thanks))
        return okChanged(answer?.second.orEmpty())
    }

    private fun results(
        world: DemoWorld,
        query: String,
    ): TdApi.InlineQueryResults {
        val found = articles(world).filter { query.isBlank() || it.title.contains(query.trim(), ignoreCase = true) }
        return TdApi.InlineQueryResults(INLINE_QUERY_ID, null, found.toTypedArray(), "")
    }

    private fun articles(world: DemoWorld): List<TdApi.InlineQueryResultArticle> {
        val text = world.data.text
        return listOf(
            R.string.demo_bot_result_harbour to R.string.demo_bot_result_harbour_description,
            R.string.demo_bot_result_lighthouse to R.string.demo_bot_result_lighthouse_description,
            R.string.demo_bot_result_market to R.string.demo_bot_result_market_description,
        ).mapIndexed { index, (title, description) ->
            TdApi.InlineQueryResultArticle("result$index", "", text(title), text(description), null)
        }
    }

    private fun sendResult(
        world: DemoWorld,
        function: TdApi.SendInlineQueryResultMessage,
    ): DemoAnswer {
        val result =
            articles(world).firstOrNull { it.id == function.resultId } ?: return badRequest("RESULT_ID_INVALID")
        val (message, updates) =
            world.addOwn(
                function.chatId,
                textContent("${result.title}\n${result.description}"),
                schedulingState = function.options?.schedulingState,
                topic = function.topicId,
            ) ?: return notFound()
        if (!function.hideViaBot) message.viaBotUserId = DemoData.LOGIN_BOT
        return changed(message, updates)
    }

    companion object {
        private const val HELLO = "hello"
        private const val ALERT = "alert"
        private const val LOGIN_URL = "https://example.org/login"
        private const val LOGIN_DOMAIN = "example.org"
        private const val OPEN_URL = "https://example.org"
        private const val INLINE_QUERY_ID = 1L
        private const val LOGIN_BUTTON = 1L
        private const val PERSON_BUTTON = 1
        private const val GROUP_BUTTON = 2

        /** What the bot answers a message the user sent it: its commands, an echo, thanks for what was shared. */
        fun answer(
            world: DemoWorld,
            message: TdApi.Message,
        ): List<TdApi.Update> {
            val text = world.data.text
            val bot = DemoData.LOGIN_BOT
            val replies: List<Pair<String, TdApi.ReplyMarkup?>> =
                when (val content = message.content) {
                    is TdApi.MessageText -> {
                        val said = content.text.text.trim()
                        when (said.substringBefore(' ').substringBefore('@')) {
                            "/start" -> {
                                listOf(
                                    text(R.string.demo_bot_welcome) to buttons(world),
                                    text(R.string.demo_bot_keyboard) to keyboard(world),
                                )
                            }

                            "/help" -> {
                                listOf(text(R.string.demo_bot_help) to null)
                            }

                            "/keyboard" -> {
                                listOf(text(R.string.demo_bot_keyboard) to keyboard(world))
                            }

                            else -> {
                                listOf(text(R.string.demo_bot_echo).format(said) to null)
                            }
                        }
                    }

                    is TdApi.MessageContact -> {
                        listOf(text(R.string.demo_bot_thanks_contact) to null)
                    }

                    is TdApi.MessageLocation -> {
                        listOf(text(R.string.demo_bot_thanks_location) to null)
                    }

                    is TdApi.MessagePoll -> {
                        listOf(text(R.string.demo_bot_thanks_poll) to null)
                    }

                    else -> {
                        emptyList()
                    }
                }
            return replies.flatMap { (reply, markup) ->
                world.addIncoming(message.chatId, bot, textContent(reply), markup)?.second.orEmpty()
            }
        }

        private fun buttons(world: DemoWorld): TdApi.ReplyMarkup {
            val text = world.data.text

            fun button(
                label: Int,
                type: TdApi.InlineKeyboardButtonType,
            ) = TdApi.InlineKeyboardButton(text(label), 0, TdApi.ButtonStyleDefault(), type)
            return TdApi.ReplyMarkupInlineKeyboard(
                arrayOf(
                    arrayOf(
                        button(
                            R.string.demo_bot_button_hello,
                            TdApi.InlineKeyboardButtonTypeCallback(HELLO.encodeToByteArray()),
                        ),
                        button(
                            R.string.demo_bot_button_alert,
                            TdApi.InlineKeyboardButtonTypeCallback(ALERT.encodeToByteArray()),
                        ),
                    ),
                    arrayOf(
                        button(
                            R.string.demo_bot_button_login,
                            TdApi.InlineKeyboardButtonTypeLoginUrl(LOGIN_URL, LOGIN_BUTTON, ""),
                        ),
                    ),
                    arrayOf(button(R.string.demo_bot_button_open, TdApi.InlineKeyboardButtonTypeUrl(OPEN_URL))),
                ),
                false,
            )
        }

        private fun keyboard(world: DemoWorld): TdApi.ReplyMarkup {
            val text = world.data.text

            fun key(
                label: Int,
                type: TdApi.KeyboardButtonType,
            ) = TdApi.KeyboardButton(text(label), 0, TdApi.ButtonStyleDefault(), type)
            val person =
                TdApi.KeyboardButtonTypeRequestUsers().apply {
                    id = PERSON_BUTTON
                    maxQuantity = 1
                    requestName = true
                }
            val group = TdApi.KeyboardButtonTypeRequestChat().apply { id = GROUP_BUTTON }
            return TdApi.ReplyMarkupShowKeyboard(
                arrayOf(
                    arrayOf(key(R.string.demo_bot_key_hello, TdApi.KeyboardButtonTypeText())),
                    arrayOf(
                        key(R.string.demo_bot_key_phone, TdApi.KeyboardButtonTypeRequestPhoneNumber()),
                        key(R.string.demo_bot_key_location, TdApi.KeyboardButtonTypeRequestLocation()),
                    ),
                    arrayOf(
                        key(R.string.demo_bot_key_poll, TdApi.KeyboardButtonTypeRequestPoll(false, false)),
                        key(R.string.demo_bot_key_person, person),
                        key(R.string.demo_bot_key_group, group),
                    ),
                ),
                false,
                true,
                false,
                false,
                false,
                text(R.string.demo_bot_placeholder),
            )
        }

        private fun textContent(text: String) = TdApi.MessageText(TdApi.FormattedText(text, emptyArray()), null, null)
    }
}
