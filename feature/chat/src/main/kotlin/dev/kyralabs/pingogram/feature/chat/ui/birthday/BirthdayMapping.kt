package dev.kyralabs.pingogram.feature.chat.ui.birthday

import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.ServiceAction
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.ui.R
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.shortName
import dev.kyralabs.pingogram.feature.chat.ui.ChatItem
import dev.kyralabs.pingogram.feature.chat.ui.ItemInputs

// What a birthday suggestion's card (td_api.tl `messageSuggestBirthdate`, the owner's board 16b) shows of its message.

/**
 * A birthday suggestion's card: the other person is the sender of one received, and the private chat's user, looked up
 * with the item's members ([birthdayRecipientOf]), of one the user sent.
 */
internal fun ItemInputs.birthday(action: ServiceAction.BirthdateSuggested): ChatItem.ServiceBirthday {
    val other = if (message.isOutgoing) members.firstOrNull() else sender as? User
    return ChatItem.ServiceBirthday(
        birthdate = action.birthdate,
        name = other?.shortName() ?: UiText.resource(R.string.preview_someone),
        outgoing = message.isOutgoing,
    )
}

/** The user a birthday suggestion the user sent went to: the private chat's own user, whose id is the chat's. */
internal fun birthdayRecipientOf(message: Message): List<Long> =
    listOfNotNull(
        message.chatId.takeIf {
            message.isOutgoing &&
                (message.content as? MessageContent.Service)?.action is ServiceAction.BirthdateSuggested
        },
    )
