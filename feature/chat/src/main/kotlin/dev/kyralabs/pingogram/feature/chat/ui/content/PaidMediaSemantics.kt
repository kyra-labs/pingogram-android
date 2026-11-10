package dev.kyralabs.pingogram.feature.chat.ui.content

import android.content.res.Resources
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatItem
import dev.kyralabs.pingogram.feature.chat.ui.giveaway.giveawayNumber
import dev.kyralabs.pingogram.feature.chat.ui.giveaway.toPluralCount

/**
 * What screen readers hear of [bubble]'s paid post within its bubble's description (`docs/DESIGN_SYSTEM.md` §9): what
 * it holds and its price, and, while not paid for, that Pingogram cannot pay for it; "Bought for 25 Telegram Stars"
 * once it is. Empty for other content.
 */
internal fun paidMediaDescription(
    resources: Resources,
    bubble: ChatItem.Bubble,
): String {
    val content = bubble.content as? MessageContent.PaidMedia ?: return ""
    val locale = resources.configuration.locales[0]
    val stars =
        resources.getQuantityString(
            R.plurals.paid_media_stars,
            content.starCount.toPluralCount(),
            giveawayNumber(content.starCount, locale),
        )
    val (photos, videos) = content.mediaCounts()
    val count =
        when {
            videos == 0 -> resources.getQuantityString(R.plurals.paid_media_photos, photos, photos)
            photos == 0 -> resources.getQuantityString(R.plurals.paid_media_videos, videos, videos)
            else -> resources.getQuantityString(R.plurals.paid_media_items, photos + videos, photos + videos)
        }
    return if (content.isUnlocked) {
        "${resources.getString(R.string.paid_media_bought_description, stars)}, $count"
    } else {
        "${resources.getString(R.string.paid_media_locked_description, count, stars)}. " +
            resources.getString(R.string.paid_media_note)
    }
}
