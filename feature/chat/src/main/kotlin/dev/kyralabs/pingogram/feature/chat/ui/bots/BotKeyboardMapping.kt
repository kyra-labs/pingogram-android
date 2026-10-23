package dev.kyralabs.pingogram.feature.chat.ui.bots

import android.content.res.Resources
import dev.kyralabs.pingogram.core.domain.model.BotButtonStyle
import dev.kyralabs.pingogram.core.domain.model.InlineKeyboardButton
import dev.kyralabs.pingogram.core.domain.model.InlineKeyboardButtonType
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.ReplyMarkup
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.BotButtonEffect
import dev.kyralabs.pingogram.feature.chat.ui.ChatContent
import dev.kyralabs.pingogram.feature.chat.ui.ChatItem
import dev.kyralabs.pingogram.feature.chat.ui.ChatUiState
import dev.kyralabs.pingogram.feature.chat.ui.UnsupportedButton
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.toImmutableList

// The inline keyboard of a message as its bubble shows it, the progress of its buttons, and their snackbars.

/** The inline keyboard under this message; null for none, and for the other kinds of reply markup. */
internal fun Message.botKeyboard(): BotKeyboard? {
    val keyboard = replyMarkup as? ReplyMarkup.InlineKeyboard ?: return null
    return BotKeyboard(
        messageId = id,
        rows = keyboard.rows.map { row -> row.map { it.toKeyboardButton() }.toImmutableList() }.toImmutableList(),
    )
}

/** The button of [keyboard] at [position]; null where there is none (any more). */
internal fun ReplyMarkup.InlineKeyboard.buttonAt(position: BotButtonPosition): InlineKeyboardButton? =
    rows.getOrNull(position.row)?.getOrNull(position.column)

private fun InlineKeyboardButton.toKeyboardButton(): BotKeyboardButton =
    BotKeyboardButton(
        text = text,
        mark =
            when (type) {
                is InlineKeyboardButtonType.Url, is InlineKeyboardButtonType.LoginUrl -> BotButtonMark.Link
                is InlineKeyboardButtonType.SwitchInline -> BotButtonMark.SwitchInline
                else -> BotButtonMark.None
            },
        tone = style.tone(),
        isEnabled = type != InlineKeyboardButtonType.Disabled,
    )

/** The colors of a button of this style; also a reply keyboard's (`BotReplyKeyboard`). */
internal fun BotButtonStyle.tone(): BotButtonTone =
    when (this) {
        BotButtonStyle.Primary -> BotButtonTone.Primary

        BotButtonStyle.Danger -> BotButtonTone.Danger

        // Success has no green in the scheme; a link's style already shows in its mark.
        BotButtonStyle.Default, BotButtonStyle.Success, BotButtonStyle.Link -> BotButtonTone.Tonal
    }

/**
 * The state with the [pending] buttons (by message id) showing their progress in their bubbles. Only bubbles whose
 * keyboard changes are copied, so the others stay equal and the list skips them; the same instance when none changes.
 */
internal fun ChatUiState.withPendingButtons(
    pending: PersistentMap<Long, PersistentSet<BotButtonPosition>>,
): ChatUiState {
    val content = content as? ChatContent.Content
    if (pending.isEmpty() || content == null) return this
    var changed = false
    val items =
        content.items.map { item ->
            val keyboard = (item as? ChatItem.Bubble)?.keyboard
            val buttons = keyboard?.let { pending[it.messageId] }
            if (keyboard == null || buttons.isNullOrEmpty()) {
                item
            } else {
                changed = true
                item.copy(keyboard = keyboard.copy(pending = buttons))
            }
        }
    return if (changed) copy(content = content.copy(items = items.toImmutableList())) else this
}

/** The snackbar of [effect]: the bot's answer, or what Pingogram does not offer yet. */
internal fun botButtonNotice(
    resources: Resources,
    effect: BotButtonEffect,
): String =
    when (effect) {
        is BotButtonEffect.Answer -> {
            effect.text
        }

        is BotButtonEffect.Unsupported -> {
            resources.getString(
                when (effect.what) {
                    UnsupportedButton.MiniApp -> R.string.bot_unsupported_mini_app
                    UnsupportedButton.Game -> R.string.bot_unsupported_game
                    UnsupportedButton.Payment -> R.string.bot_unsupported_payment
                    UnsupportedButton.InlineLink -> R.string.bot_unsupported_inline_link
                    UnsupportedButton.ManagedBot -> R.string.bot_unsupported_managed_bot
                },
            )
        }
    }
