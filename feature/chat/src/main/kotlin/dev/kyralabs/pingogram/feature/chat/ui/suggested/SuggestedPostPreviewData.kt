package dev.kyralabs.pingogram.feature.chat.ui.suggested

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import dev.kyralabs.pingogram.core.domain.model.SuggestedPostState
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R

/** Suggested posts' cards for previews and goldens: a free one to answer, a paid one, and one approved. */
internal object SuggestedPostPreviewData {
    val free =
        SuggestedPostUi(
            messageId = 1,
            state = SuggestedPostState.Pending,
            sendDate = null,
            price = null,
            canApprove = true,
            canDecline = true,
            approvesElsewhere = false,
        )

    val paid =
        free.copy(
            messageId = 2,
            sendDate = UiText.Plain("October 5, 2026, 9:41 AM"),
            price = UiText.plural(R.plurals.suggested_post_offers_stars, 50, 50L),
            canApprove = false,
            approvesElsewhere = true,
        )

    val approved = free.copy(messageId = 3, state = SuggestedPostState.Approved, canApprove = false, canDecline = false)
}

/** The three cards, one above the other. */
@Composable
internal fun SuggestedPostCards() {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
        SuggestedPostCard(SuggestedPostPreviewData.free, onEvent = {})
        SuggestedPostCard(SuggestedPostPreviewData.paid, onEvent = {})
        SuggestedPostCard(SuggestedPostPreviewData.approved, onEvent = {})
    }
}

@PingogramPreviews
@Composable
private fun SuggestedPostCardsPreview() {
    PingogramPreview { SuggestedPostCards() }
}
