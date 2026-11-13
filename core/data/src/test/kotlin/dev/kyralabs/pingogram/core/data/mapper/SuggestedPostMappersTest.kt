package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.ServiceAction
import dev.kyralabs.pingogram.core.domain.model.SuggestedPost
import dev.kyralabs.pingogram.core.domain.model.SuggestedPostAnswer
import dev.kyralabs.pingogram.core.domain.model.SuggestedPostPrice
import dev.kyralabs.pingogram.core.domain.model.SuggestedPostState
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Posts suggested to a channel through its direct messages, and what became of them. */
class SuggestedPostMappersTest {
    @Test
    fun `a suggested post maps with its price, time, state and what the user may do`() {
        val info =
            TdApi.SuggestedPostInfo(
                TdApi.SuggestedPostPriceStar(50),
                1_800_000_000,
                TdApi.SuggestedPostStatePending(),
                true,
                true,
            )

        assertEquals(
            SuggestedPost(SuggestedPostPrice.Stars(50), 1_800_000_000, SuggestedPostState.Pending, true, true),
            info.toSuggestedPost(),
        )
        assertEquals(
            SuggestedPost(SuggestedPostPrice.Toncoin(150), 0, SuggestedPostState.Declined),
            TdApi
                .SuggestedPostInfo(
                    TdApi.SuggestedPostPriceGram(150),
                    0,
                    TdApi.SuggestedPostStateDeclined(),
                    false,
                    false,
                ).toSuggestedPost(),
        )
    }

    @Test
    fun `a message carries its suggested post, others none`() {
        val message = TdApiFixtures.message().apply { suggestedPostInfo = free() }

        assertEquals(SuggestedPostState.Approved, message.toMessage().suggestedPost?.state)
        assertNull(TdApiFixtures.message().toMessage().suggestedPost)
    }

    @Test
    fun `what became of a suggested post maps to its answer`() {
        val price = TdApi.SuggestedPostPriceStar(5)
        assertEquals(
            listOf(
                ServiceAction.SuggestedPostAnswered(7, SuggestedPostAnswer.Approved),
                ServiceAction.SuggestedPostAnswered(7, SuggestedPostAnswer.Declined, "Too long"),
                ServiceAction.SuggestedPostAnswered(7, SuggestedPostAnswer.ApprovalFailed),
                ServiceAction.SuggestedPostAnswered(7, SuggestedPostAnswer.Paid),
                ServiceAction.SuggestedPostAnswered(7, SuggestedPostAnswer.Refunded),
            ),
            listOf(
                TdApi.MessageSuggestedPostApproved(7, price, 0),
                TdApi.MessageSuggestedPostDeclined(7, "Too long"),
                TdApi.MessageSuggestedPostApprovalFailed(7, price),
                TdApi.MessageSuggestedPostPaid(7, TdApi.StarAmount(5, 0), 0),
                TdApi.MessageSuggestedPostRefunded(7, TdApi.SuggestedPostRefundReasonPostDeleted()),
            ).map(::suggestedPostAnswerOf),
        )
    }

    private fun free() = TdApi.SuggestedPostInfo(null, 0, TdApi.SuggestedPostStateApproved(), false, false)
}
