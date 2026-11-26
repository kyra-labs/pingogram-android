package dev.kyralabs.pingogram.feature.chats.ui.unanswered

import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.ui.text.messagePreviewText
import dev.kyralabs.pingogram.feature.chats.ui.SearchMessageItem
import dev.kyralabs.pingogram.feature.chats.ui.SearchRowSources
import dev.kyralabs.pingogram.feature.chats.ui.chatTitle
import dev.kyralabs.pingogram.feature.chats.ui.placeholderIcon
import dev.kyralabs.pingogram.feature.chats.ui.titleBadge
import dev.kyralabs.pingogram.feature.chats.ui.userId

/**
 * The Unanswered page's row of this chat, as global search shows a message: the person's avatar and name (the name
 * on this phone where they have one), their last message as the chat list previews it and when it came; null without
 * a last message.
 */
internal fun Chat.toUnansweredItem(sources: SearchRowSources): SearchMessageItem? {
    val message = lastMessage ?: return null
    val user = userId?.let(sources.users::get)
    return SearchMessageItem(
        chatId = id,
        messageId = message.id,
        chatTitle = chatTitle(this, isSavedMessages = false, isDeletedAccount = false, sources.localNameOf(this)),
        photo = photo,
        placeholderIcon = placeholderIcon(isSavedMessages = false, isDeletedAccount = false),
        badge = titleBadge(user, supergroup = null),
        sender = null,
        preview = messagePreviewText(message, sources.myUserId) { null },
        date = sources.timeFormatter.format(message.date, sources.now),
    )
}
