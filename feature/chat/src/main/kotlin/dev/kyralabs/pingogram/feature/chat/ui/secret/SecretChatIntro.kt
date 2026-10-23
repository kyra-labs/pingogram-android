package dev.kyralabs.pingogram.feature.chat.ui.secret

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * What a secret chat is, at its start (`REQUIREMENTS.md` FR-E1): a service-style card, centred like service messages,
 * on their `surfaceContainerHighest` with `extraLarge` corners (`docs/DESIGN_SYSTEM.md` §2.2): the lock in `primary`
 * and "End-to-end encrypted" in `titleSmallEmphasized`, then in `bodyMedium` `onSurfaceVariant` that only the user
 * and [name] (isolated, §9) can read the messages, that they live on this device only, the self-destruct timer, and
 * that Pingogram blocks screenshots here, on this device only: the other side's app decides for itself. Screen readers
 * hear it as one.
 */
@Composable
internal fun SecretChatIntro(
    name: UiText,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    val scheme = MaterialTheme.colorScheme
    val body = MaterialTheme.typography.bodyMedium.userContent
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = MaterialTheme.sizes.bubbleMaxWidth)
                    .background(scheme.surfaceContainerHighest, MaterialTheme.shapes.extraLarge)
                    .padding(horizontal = spacing.large, vertical = spacing.medium)
                    .semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(UiR.drawable.ic_lock),
                    contentDescription = null,
                    modifier = Modifier.size(MaterialTheme.sizes.inlineIcon),
                    tint = scheme.primary,
                )
                Text(
                    text = stringResource(R.string.secret_intro_title),
                    color = scheme.onSurface,
                    style = MaterialTheme.typography.titleSmallEmphasized,
                )
            }
            val lines =
                listOf(
                    stringResource(R.string.secret_intro_encrypted, name.bidiIsolated().resolve()),
                    stringResource(R.string.secret_intro_device),
                    stringResource(R.string.secret_intro_timer),
                    stringResource(R.string.secret_intro_screenshots),
                )
            // A name in the first, isolated: each line takes its language's direction, not the layout's.
            lines.forEach { Text(text = it, color = scheme.onSurfaceVariant, style = body) }
        }
    }
}

@PingogramPreviews
@Composable
private fun SecretChatIntroPreview() {
    PingogramPreview { SecretChatIntro(UiText.Plain("Ada"), Modifier.padding(MaterialTheme.spacing.medium)) }
}
