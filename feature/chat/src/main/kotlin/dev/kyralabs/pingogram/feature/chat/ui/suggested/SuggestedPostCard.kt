package dev.kyralabs.pingogram.feature.chat.ui.suggested

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.domain.model.SuggestedPostState
import dev.kyralabs.pingogram.core.ui.component.GrowingVisibility
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatEvent
import dev.kyralabs.pingogram.feature.chat.ui.ContentAction
import dev.kyralabs.pingogram.feature.chat.ui.SuggestedPostEvent
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * Below a post suggested to a channel (`docs/DESIGN_SYSTEM.md` §6 "Suggested posts"), on the bubble's side and at most
 * a bubble's width: a card on `surfaceContainerHigh` with `large` corners, the megaphone in `primary` and "Suggested
 * post" in `titleSmallEmphasized`, then where it stands in `bodyMedium` `onSurfaceVariant` (when it goes out, any time,
 * approved or declined) and what its author offers; for an administrator, "Decline" (`FilledTonalButton`) and
 * "Approve" (`Button`) sharing the row, Approve only for a free post (a paid one says to answer in another Telegram
 * app: Pingogram takes no payments). Screen readers hear it in the bubble's description ([suggestedPostDescription])
 * and press its buttons as the bubble's custom actions ([suggestedPostActions]).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SuggestedPostCard(
    post: SuggestedPostUi,
    onEvent: (ChatEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    Surface(
        modifier = modifier.widthIn(max = MaterialTheme.sizes.bubbleMaxWidth),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(spacing.medium), verticalArrangement = Arrangement.spacedBy(spacing.small)) {
            Row(
                Modifier.semantics { heading() },
                horizontalArrangement = Arrangement.spacedBy(spacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(UiR.drawable.ic_campaign),
                    contentDescription = null,
                    modifier = Modifier.size(MaterialTheme.sizes.inlineIcon),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    stringResource(R.string.suggested_post_title),
                    style = MaterialTheme.typography.titleSmallEmphasized,
                )
            }
            Text(
                text = suggestedPostDetails(LocalResources.current, post),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            // Once answered, the buttons shrink away instead of leaving the card at once; they show as they were.
            val answerable = post.canApprove || post.canDecline
            val lastAnswerable = remember { arrayOfNulls<SuggestedPostUi>(1) }
            if (answerable) lastAnswerable[0] = post
            GrowingVisibility(visible = answerable) {
                AnswerButtons(post.messageId, lastAnswerable[0] ?: post, onEvent)
            }
        }
    }
}

/** "Decline" and "Approve" sharing the card's row, as [shown] allows them. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AnswerButtons(
    messageId: Long,
    shown: SuggestedPostUi,
    onEvent: (ChatEvent) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
        if (shown.canDecline) {
            FilledTonalButton(
                onClick = { onEvent(SuggestedPostEvent.Answer(messageId, approve = false)) },
                modifier = Modifier.weight(1f),
                shapes = ButtonDefaults.shapes(),
            ) { Text(stringResource(R.string.suggested_post_decline), maxLines = 1) }
        }
        if (shown.canApprove) {
            Button(
                onClick = { onEvent(SuggestedPostEvent.Answer(messageId, approve = true)) },
                modifier = Modifier.weight(1f),
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(
                    stringResource(R.string.suggested_post_approve),
                    maxLines = 1,
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }
        }
    }
}

/** Where [post] stands and what its author offers, one paragraph. */
internal fun suggestedPostDetails(
    resources: Resources,
    post: SuggestedPostUi,
): String {
    val state =
        when (post.state) {
            SuggestedPostState.Approved -> {
                resources.getString(R.string.suggested_post_approved)
            }

            SuggestedPostState.Declined -> {
                resources.getString(R.string.suggested_post_declined)
            }

            SuggestedPostState.Pending -> {
                post.sendDate?.let { resources.getString(R.string.suggested_post_publishes_at, it.resolve(resources)) }
                    ?: resources.getString(R.string.suggested_post_any_time)
            }
        }
    val price = post.price?.resolve(resources)
    val elsewhere = resources.getString(R.string.suggested_post_answer_elsewhere).takeIf { post.approvesElsewhere }
    return listOfNotNull(state, price, elsewhere).joinToString(" ")
}

/** What screen readers hear of the card, in the bubble's description. */
internal fun suggestedPostDescription(
    resources: Resources,
    post: SuggestedPostUi,
): String = resources.getString(R.string.suggested_post_title) + ". " + suggestedPostDetails(resources, post)

/** The card's buttons as the bubble's custom actions. */
internal fun suggestedPostActions(post: SuggestedPostUi?): List<ContentAction> =
    listOfNotNull(
        ContentAction(R.string.suggested_post_approve_action, SuggestedPostEvent.Answer(post?.messageId ?: 0, true))
            .takeIf { post?.canApprove == true },
        ContentAction(R.string.suggested_post_decline_action, SuggestedPostEvent.Answer(post?.messageId ?: 0, false))
            .takeIf { post?.canDecline == true },
    )
