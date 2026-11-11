package dev.kyralabs.pingogram.feature.chat.ui.suggested

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.domain.model.SuggestedPost
import dev.kyralabs.pingogram.core.domain.model.SuggestedPostPrice
import dev.kyralabs.pingogram.core.domain.model.SuggestedPostState
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ItemInputs
import java.util.Locale

/**
 * What the card below a suggested post shows (`docs/DESIGN_SYSTEM.md` §6 "Suggested posts"): where it stands
 * ([state]), when it is to go out ([sendDate], null for any time the administrators choose), what its author offers
 * ([price], null for nothing), and the administrators' answers: [canApprove] a free one, [canDecline] any.
 */
@Immutable
data class SuggestedPostUi(
    val messageId: Long,
    val state: SuggestedPostState,
    val sendDate: UiText?,
    val price: UiText?,
    val canApprove: Boolean,
    val canDecline: Boolean,
    /** An offer the user could approve elsewhere: Pingogram takes no payments (`REQUIREMENTS.md` §1.2). */
    val approvesElsewhere: Boolean,
)

/** The card of message [messageId]'s [post]: a paid one is approved in another Telegram app, never here. */
internal fun ItemInputs.suggestedPostUi(
    messageId: Long,
    post: SuggestedPost,
): SuggestedPostUi =
    SuggestedPostUi(
        messageId = messageId,
        state = post.state,
        sendDate = post.sendDate.takeIf { it > 0 }?.let(timeFormatter::dateAndTime),
        price = post.price?.let(::priceText),
        canApprove = post.canBeApproved && post.price == null,
        canDecline = post.canBeDeclined,
        approvesElsewhere = post.canBeApproved && post.price != null,
    )

private fun priceText(price: SuggestedPostPrice): UiText =
    when (price) {
        is SuggestedPostPrice.Stars -> {
            UiText.plural(R.plurals.suggested_post_offers_stars, price.count.toInt(), price.count)
        }

        is SuggestedPostPrice.Toncoin -> {
            val amount = String.format(Locale.ROOT, "%d.%02d", price.cents / CENTS, price.cents % CENTS)
            UiText.resource(R.string.suggested_post_offers_toncoin, amount)
        }
    }

private const val CENTS = 100
