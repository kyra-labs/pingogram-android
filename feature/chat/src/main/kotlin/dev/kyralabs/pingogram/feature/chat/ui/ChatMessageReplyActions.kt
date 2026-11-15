package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageProperties

/**
 * What the actions sheet offers for answering [message] later or elsewhere, in its order: replying where the user can
 * write, replying in another chat where TDLib allows it, and a reminder (the owner's board 29) once it is sent, never
 * in a secret chat, as the reminder's notification shows the message outside the chat.
 */
internal fun ActionContext.replyActions(
    message: Message,
    properties: MessageProperties,
): List<MessageAction> =
    listOfNotNull(
        MessageAction.Reply.takeIf { properties.canBeReplied && canWrite },
        MessageAction.ReplyElsewhere.takeIf { properties.canBeRepliedInAnotherChat },
        MessageAction.RemindMe.takeIf { message.sendingState == null && chat.type !is ChatType.Secret },
    )
