package dev.kyralabs.pingogram.feature.chats.ui.drafts

import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.ui.component.AvatarShape
import dev.kyralabs.pingogram.core.ui.text.draftPreviewText
import dev.kyralabs.pingogram.core.ui.text.isDeleted
import dev.kyralabs.pingogram.feature.chats.ui.SearchMessageItem
import dev.kyralabs.pingogram.feature.chats.ui.SearchRowSources
import dev.kyralabs.pingogram.feature.chats.ui.chatTitle
import dev.kyralabs.pingogram.feature.chats.ui.isChannel
import dev.kyralabs.pingogram.feature.chats.ui.isGroup
import dev.kyralabs.pingogram.feature.chats.ui.placeholderIcon
import dev.kyralabs.pingogram.feature.chats.ui.shownDraft
import dev.kyralabs.pingogram.feature.chats.ui.titleBadge
import dev.kyralabs.pingogram.feature.chats.ui.userId

/**
 * The Drafts page's row of this chat, as global search shows a message: the chat's avatar and name, the draft's text
 * as the chat list previews it and when it was saved. A draft has no message id, so it is [NO_MESSAGE]; null without
 * a draft the chat list shows.
 */
internal fun Chat.toDraftItem(sources: SearchRowSources): SearchMessageItem? {
    val draft = shownDraft ?: return null
    val user = userId?.let(sources.users::get)
    val supergroup = (type as? ChatType.Supergroup)?.let { sources.supergroups[it.supergroupId] }
    val isSavedMessages = type is ChatType.Private && userId == sources.myUserId
    val isDeletedAccount = user?.isDeleted == true
    return SearchMessageItem(
        chatId = id,
        messageId = NO_MESSAGE,
        chatTitle = chatTitle(this, isSavedMessages, isDeletedAccount, sources.localNameOf(this)),
        photo = if (isSavedMessages) null else photo,
        placeholderIcon = placeholderIcon(isSavedMessages, isDeletedAccount),
        badge = titleBadge(user, supergroup),
        sender = null,
        preview = draftPreviewText(draft.content),
        date = sources.timeFormatter.format(draft.date, sources.now),
        avatarShape =
            AvatarShape.ofChat(
                isForum = supergroup?.isForum == true,
                isGroup = isGroup,
                isChannel = isChannel,
                isSavedMessages = isSavedMessages,
            ),
    )
}

/** The message id of a draft's row: none. */
internal const val NO_MESSAGE = 0L
