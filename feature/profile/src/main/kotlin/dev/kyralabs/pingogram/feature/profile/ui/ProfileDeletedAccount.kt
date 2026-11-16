package dev.kyralabs.pingogram.feature.profile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.profile.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * What a deleted account's profile says in place of its information (`docs/DESIGN_SYSTEM.md` §6 "Deleted account",
 * board 24): a card on the rows' container with `extraLarge` corners, the history symbol on a
 * `MaterialShapes.Cookie9Sided` badge (`sizes.headlineBadge`) in `surfaceContainerHighest`, "This account was deleted"
 * in `titleMediumEmphasized` (a heading) and what is kept in `bodyMedium` `onSurfaceVariant` (a polite live region,
 * board 24g), centred.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DeletedAccountCard(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val spacing = MaterialTheme.spacing
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = profileRowContainerColor()),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.large, vertical = spacing.extraLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(MaterialTheme.sizes.headlineBadge)
                        .clip(MaterialShapes.Cookie9Sided.toShape())
                        .background(colors.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(UiR.drawable.ic_history),
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(R.string.profile_deleted_title),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.profile_deleted_text),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@PingogramPreviews
@Composable
private fun DeletedAccountCardPreview() {
    PingogramPreview { DeletedAccountCard() }
}
