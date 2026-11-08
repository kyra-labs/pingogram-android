package dev.kyralabs.pingogram.feature.chats.ui

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.ui.R
import dev.kyralabs.pingogram.core.ui.component.AvatarShape

// What kind of chat and of message a row shows, which the experimental look marks with symbols
// (docs/DESIGN_SYSTEM.md §13).

/**
 * A row's title: [ChatTitle] with, in the [experimental] look, the kind of chat before the name (unless Appearance →
 * Chat list → "Chat type icons" is off, [ChatSwipes.chatTypeIcons]) and the mute icon only while nothing is unread.
 */
@Composable
internal fun ChatRowTitle(
    item: ChatListItemModel,
    title: String,
    experimental: Boolean,
) {
    ChatTitle(
        title = title,
        isSecret = item.isSecret,
        badge = item.badge,
        isMuted = item.isMuted && !(experimental && item.isUnread),
        isUnread = item.isUnread,
        kindIcon = item.avatarShape.kindIcon().takeIf { experimental && LocalChatSwipes.current.chatTypeIcons },
    )
}

/**
 * The experimental look's symbol of what a chat is, before its name (`docs/DESIGN_SYSTEM.md` §13): people for a group
 * or a forum, a megaphone for a channel; null for people, bots and Saved Messages.
 */
@DrawableRes
internal fun AvatarShape.kindIcon(): Int? =
    when (this) {
        AvatarShape.Group, AvatarShape.RoundedSquare -> R.drawable.ic_group
        AvatarShape.Channel -> R.drawable.ic_campaign
        AvatarShape.Circle, AvatarShape.SavedMessages -> null
    }

/**
 * What kind of message [content] is, as an outlined symbol the experimental look shows before the preview
 * (`docs/DESIGN_SYSTEM.md` §13); null for plain texts, service messages and what has no symbol of its own.
 */
@DrawableRes
internal fun contentIcon(content: MessageContent): Int? =
    when (content) {
        is MessageContent.Photo -> R.drawable.ic_image
        is MessageContent.Video, is MessageContent.VideoNote -> R.drawable.ic_videocam
        is MessageContent.Animation -> R.drawable.ic_gif_box
        is MessageContent.VoiceNote -> R.drawable.ic_mic
        is MessageContent.Audio -> R.drawable.ic_music_note
        is MessageContent.Document -> R.drawable.ic_description
        is MessageContent.Location, is MessageContent.Venue -> R.drawable.ic_location_on
        is MessageContent.Poll -> R.drawable.ic_poll_filled
        is MessageContent.Text -> if (content.linkPreview != null) R.drawable.ic_link else null
        else -> null
    }
