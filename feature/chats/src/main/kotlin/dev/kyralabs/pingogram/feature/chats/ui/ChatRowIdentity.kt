package dev.kyralabs.pingogram.feature.chats.ui

import dev.kyralabs.pingogram.core.domain.model.Supergroup
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.model.UserType
import dev.kyralabs.pingogram.core.domain.model.isOnlineAt
import java.time.Instant

// Who a chat is, as the rows of the chat list (ChatListItemMapper) and of search (SearchRowSources.kt) show it.

/** Telegram's verification of the private chat's [user] or of the [supergroup] or channel; warnings first. */
internal fun titleBadge(
    user: User?,
    supergroup: Supergroup?,
): TitleBadge? {
    val isScam = user?.isScam ?: supergroup?.isScam ?: false
    val isFake = user?.isFake ?: supergroup?.isFake ?: false
    val isVerified = user?.isVerified ?: supergroup?.isVerified ?: false
    return when {
        isScam -> TitleBadge.Scam
        isFake -> TitleBadge.Fake
        isVerified -> TitleBadge.Verified
        else -> null
    }
}

/** The user of a private or secret chat is online at the minute [now] shows; bots never are. */
internal fun User.showsOnlineAt(now: Instant): Boolean = type != UserType.Bot && status.isOnlineAt(now.epochSecond)
