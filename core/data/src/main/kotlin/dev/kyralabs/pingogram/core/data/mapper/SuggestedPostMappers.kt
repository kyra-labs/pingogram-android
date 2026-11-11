package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.ServiceAction
import dev.kyralabs.pingogram.core.domain.model.SuggestedPost
import dev.kyralabs.pingogram.core.domain.model.SuggestedPostAnswer
import dev.kyralabs.pingogram.core.domain.model.SuggestedPostPrice
import dev.kyralabs.pingogram.core.domain.model.SuggestedPostState
import org.drinkless.tdlib.TdApi

/** The domain's suggested post; a state a newer TDLib added reads as waiting. */
internal fun TdApi.SuggestedPostInfo.toSuggestedPost(): SuggestedPost =
    SuggestedPost(
        price = price?.toSuggestedPostPrice(),
        sendDate = sendDate.toLong(),
        state =
            when (state) {
                is TdApi.SuggestedPostStateApproved -> SuggestedPostState.Approved
                is TdApi.SuggestedPostStateDeclined -> SuggestedPostState.Declined
                else -> SuggestedPostState.Pending
            },
        canBeApproved = canBeApproved,
        canBeDeclined = canBeDeclined,
    )

/** Null for a price of a kind a newer TDLib added. */
internal fun TdApi.SuggestedPostPrice.toSuggestedPostPrice(): SuggestedPostPrice? =
    when (this) {
        is TdApi.SuggestedPostPriceStar -> SuggestedPostPrice.Stars(starCount)
        is TdApi.SuggestedPostPriceGram -> SuggestedPostPrice.Toncoin(gramCentCount)
        else -> null
    }

/** The service message of what became of a suggested post; null for other content. */
internal fun suggestedPostAnswerOf(content: TdApi.MessageContent): ServiceAction? =
    when (content) {
        is TdApi.MessageSuggestedPostApproved -> {
            ServiceAction.SuggestedPostAnswered(content.suggestedPostMessageId, SuggestedPostAnswer.Approved)
        }

        is TdApi.MessageSuggestedPostDeclined -> {
            ServiceAction.SuggestedPostAnswered(
                content.suggestedPostMessageId,
                SuggestedPostAnswer.Declined,
                content.comment.orEmpty(),
            )
        }

        is TdApi.MessageSuggestedPostApprovalFailed -> {
            ServiceAction.SuggestedPostAnswered(content.suggestedPostMessageId, SuggestedPostAnswer.ApprovalFailed)
        }

        is TdApi.MessageSuggestedPostPaid -> {
            ServiceAction.SuggestedPostAnswered(content.suggestedPostMessageId, SuggestedPostAnswer.Paid)
        }

        is TdApi.MessageSuggestedPostRefunded -> {
            ServiceAction.SuggestedPostAnswered(content.suggestedPostMessageId, SuggestedPostAnswer.Refunded)
        }

        else -> {
            null
        }
    }
