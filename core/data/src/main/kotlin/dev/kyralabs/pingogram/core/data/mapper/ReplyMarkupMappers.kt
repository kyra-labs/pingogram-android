package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.BotButtonStyle
import dev.kyralabs.pingogram.core.domain.model.CallbackData
import dev.kyralabs.pingogram.core.domain.model.CallbackPayload
import dev.kyralabs.pingogram.core.domain.model.InlineKeyboardButton
import dev.kyralabs.pingogram.core.domain.model.InlineKeyboardButtonType
import dev.kyralabs.pingogram.core.domain.model.KeyboardButton
import dev.kyralabs.pingogram.core.domain.model.KeyboardButtonType
import dev.kyralabs.pingogram.core.domain.model.ReplyMarkup
import dev.kyralabs.pingogram.core.domain.model.SwitchInlineTarget
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.drinkless.tdlib.TdApi

/**
 * The [ReplyMarkup] of a message (td_api.tl `message.reply_markup`, `updateMessageEdited.reply_markup`); null for a
 * kind a newer TDLib adds, and for a keyboard without buttons, which shows nothing. Empty rows are left out. A button
 * of a kind a newer TDLib adds stays, disabled, so the rows keep their shape.
 */
internal fun TdApi.ReplyMarkup.toReplyMarkup(): ReplyMarkup? =
    when (this) {
        is TdApi.ReplyMarkupInlineKeyboard -> {
            rows
                .rowsOf { it.toInlineKeyboardButton() }
                .takeIf { it.isNotEmpty() }
                ?.let(ReplyMarkup::InlineKeyboard)
        }

        is TdApi.ReplyMarkupShowKeyboard -> {
            rows.rowsOf { it.toKeyboardButton() }.takeIf { it.isNotEmpty() }?.let { buttons ->
                ReplyMarkup.ShowKeyboard(
                    rows = buttons,
                    resize = resizeKeyboard,
                    oneTime = oneTime,
                    persistent = isPersistent,
                    personal = isPersonal,
                    placeholder = inputFieldPlaceholder.orEmpty(),
                )
            }
        }

        is TdApi.ReplyMarkupRemoveKeyboard -> {
            ReplyMarkup.RemoveKeyboard(isPersonal)
        }

        is TdApi.ReplyMarkupForceReply -> {
            ReplyMarkup.ForceReply(isPersonal, inputFieldPlaceholder.orEmpty())
        }

        else -> {
            null
        }
    }

/** These rows of buttons mapped by [map], without empty rows. */
private inline fun <T, R> Array<Array<T>>.rowsOf(map: (T) -> R): ImmutableList<ImmutableList<R>> =
    filter { it.isNotEmpty() }.map { row -> row.map(map).toImmutableList() }.toImmutableList()

private fun TdApi.InlineKeyboardButton.toInlineKeyboardButton(): InlineKeyboardButton =
    InlineKeyboardButton(text = text.orEmpty(), style = style.toBotButtonStyle(), type = type.toButtonType())

private fun TdApi.InlineKeyboardButtonType?.toButtonType(): InlineKeyboardButtonType =
    when (this) {
        is TdApi.InlineKeyboardButtonTypeUrl -> {
            InlineKeyboardButtonType.Url(url)
        }

        is TdApi.InlineKeyboardButtonTypeLoginUrl -> {
            InlineKeyboardButtonType.LoginUrl(url, id, forwardText.orEmpty())
        }

        is TdApi.InlineKeyboardButtonTypeCallback -> {
            InlineKeyboardButtonType.Callback(CallbackData(data ?: EMPTY))
        }

        is TdApi.InlineKeyboardButtonTypeCallbackWithPassword -> {
            InlineKeyboardButtonType.CallbackWithPassword(CallbackData(data ?: EMPTY))
        }

        is TdApi.InlineKeyboardButtonTypeSwitchInline -> {
            InlineKeyboardButtonType.SwitchInline(query.orEmpty(), targetChat.toSwitchInlineTarget())
        }

        is TdApi.InlineKeyboardButtonTypeCopyText -> {
            InlineKeyboardButtonType.CopyText(text.orEmpty())
        }

        is TdApi.InlineKeyboardButtonTypeUser -> {
            InlineKeyboardButtonType.User(userId)
        }

        is TdApi.InlineKeyboardButtonTypeWebApp -> {
            InlineKeyboardButtonType.WebApp(url)
        }

        is TdApi.InlineKeyboardButtonTypeCallbackGame -> {
            InlineKeyboardButtonType.CallbackGame
        }

        is TdApi.InlineKeyboardButtonTypeBuy -> {
            InlineKeyboardButtonType.Buy
        }

        // Disabled, and a kind a newer TDLib adds.
        else -> {
            InlineKeyboardButtonType.Disabled
        }
    }

private fun TdApi.TargetChat?.toSwitchInlineTarget(): SwitchInlineTarget =
    when (this) {
        is TdApi.TargetChatCurrent -> {
            SwitchInlineTarget.CurrentChat
        }

        is TdApi.TargetChatChosen -> {
            val types = types
            SwitchInlineTarget.ChosenChat(
                users = types?.allowUserChats == true,
                bots = types?.allowBotChats == true,
                groups = types?.allowGroupChats == true,
                channels = types?.allowChannelChats == true,
            )
        }

        // An internal link, and a target a newer TDLib adds.
        else -> {
            SwitchInlineTarget.Link
        }
    }

private fun TdApi.KeyboardButton.toKeyboardButton(): KeyboardButton =
    KeyboardButton(text = text.orEmpty(), style = style.toBotButtonStyle(), type = type.toKeyboardButtonType())

private fun TdApi.KeyboardButtonType?.toKeyboardButtonType(): KeyboardButtonType =
    when (this) {
        is TdApi.KeyboardButtonTypeText -> {
            KeyboardButtonType.Text
        }

        is TdApi.KeyboardButtonTypeRequestPhoneNumber -> {
            KeyboardButtonType.RequestPhoneNumber
        }

        is TdApi.KeyboardButtonTypeRequestLocation -> {
            KeyboardButtonType.RequestLocation
        }

        is TdApi.KeyboardButtonTypeRequestPoll -> {
            KeyboardButtonType.RequestPoll(
                quiz =
                    when {
                        forceQuiz -> true
                        forceRegular -> false
                        else -> null
                    },
            )
        }

        is TdApi.KeyboardButtonTypeRequestUsers -> {
            KeyboardButtonType.RequestUsers(
                id = id,
                isBot = userIsBot.takeIf { restrictUserIsBot },
                isPremium = userIsPremium.takeIf { restrictUserIsPremium },
                maxQuantity = maxQuantity,
                requestName = requestName,
                requestUsername = requestUsername,
                requestPhoto = requestPhoto,
            )
        }

        is TdApi.KeyboardButtonTypeRequestChat -> {
            KeyboardButtonType.RequestChat(
                id = id,
                isChannel = chatIsChannel,
                isForum = chatIsForum.takeIf { restrictChatIsForum },
                hasUsername = chatHasUsername.takeIf { restrictChatHasUsername },
                isCreated = chatIsCreated,
                userRights = userAdministratorRights?.toAdministratorRights(),
                botRights = botAdministratorRights?.toAdministratorRights(),
                botIsMember = botIsMember,
                requestTitle = requestTitle,
                requestUsername = requestUsername,
                requestPhoto = requestPhoto,
            )
        }

        is TdApi.KeyboardButtonTypeRequestManagedBot -> {
            KeyboardButtonType.RequestManagedBot(id, suggestedName.orEmpty(), suggestedUsername.orEmpty())
        }

        is TdApi.KeyboardButtonTypeWebApp -> {
            KeyboardButtonType.WebApp(url)
        }

        else -> {
            KeyboardButtonType.Unsupported
        }
    }

/** The style a bot asked for; [BotButtonStyle.Default] without one, and for a style a newer TDLib adds. */
private fun TdApi.ButtonStyle?.toBotButtonStyle(): BotButtonStyle =
    when (this) {
        is TdApi.ButtonStylePrimary -> BotButtonStyle.Primary
        is TdApi.ButtonStyleDanger -> BotButtonStyle.Danger
        is TdApi.ButtonStyleSuccess -> BotButtonStyle.Success
        is TdApi.ButtonStyleLink -> BotButtonStyle.Link
        else -> BotButtonStyle.Default
    }

/** TDLib's payload for [this]. */
internal fun CallbackPayload.toTdPayload(): TdApi.CallbackQueryPayload =
    when (this) {
        is CallbackPayload.Data -> TdApi.CallbackQueryPayloadData(data.bytes)
        is CallbackPayload.DataWithPassword -> TdApi.CallbackQueryPayloadDataWithPassword(password, data.bytes)
        is CallbackPayload.Game -> TdApi.CallbackQueryPayloadGame(shortName)
    }

private val EMPTY = ByteArray(0)
