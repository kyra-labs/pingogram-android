package dev.kyralabs.pingogram.feature.chats.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chats.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * What search of public posts says before searching them (`docs/DESIGN_SYSTEM.md` §6 "Global search"), a segment on
 * the result rows' own container (`ListItemDefaults.segmentedColors()`) with large corners: the search symbol, "Search
 * posts in public channels" in `titleMedium` (a heading), how many of the day's free searches are left in `bodyMedium`
 * `onSurfaceVariant`, and a default `Button` "Search posts" at the end ([onSearch]), as each search spends one; or,
 * with none left, the clock symbol, how many the day had and when the next one comes, a polite live region, without a
 * button.
 */
@Composable
internal fun PostsPromptCard(
    prompt: PostsPrompt,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    val exhausted = prompt as? PostsPrompt.Exhausted
    val offer = prompt as? PostsPrompt.Offer
    Column(
        modifier =
            modifier
                .background(
                    ListItemDefaults.segmentedColors().containerColor,
                    ListItemDefaults.segmentedShapes(0, 1).shape,
                ).padding(spacing.large),
        verticalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        PostsPromptHeader(exhausted)
        Text(
            text =
                when {
                    exhausted != null -> {
                        stringResource(R.string.search_posts_exhausted_body, exhausted.nextFree)
                    }

                    offer != null -> {
                        pluralStringResource(
                            R.plurals.search_posts_offer,
                            offer.left,
                            offer.left,
                            offer.daily,
                        )
                    }

                    else -> {
                        ""
                    }
                },
            modifier = if (exhausted != null) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (offer != null) {
            Button(
                onClick = onSearch,
                modifier = Modifier.align(Alignment.End),
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(
                    text = stringResource(R.string.search_posts_search),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }
        }
    }
}

/** The card's symbol and title: the search, or the clock once the day's free searches are used ([exhausted]). */
@Composable
private fun PostsPromptHeader(exhausted: PostsPrompt.Exhausted?) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(if (exhausted != null) UiR.drawable.ic_schedule else UiR.drawable.ic_search),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text =
                if (exhausted != null) {
                    pluralStringResource(R.plurals.search_posts_exhausted_title, exhausted.daily, exhausted.daily)
                } else {
                    stringResource(R.string.search_posts_title)
                },
            modifier = Modifier.weight(1f).semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

/** "6 of 10 free searches left today" under the posts found, in `bodySmall` `onSurfaceVariant`. */
@Composable
internal fun PostsLeftNote(
    text: UiText,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.resolve(),
        modifier = modifier.padding(horizontal = MaterialTheme.spacing.large, vertical = MaterialTheme.spacing.small),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
    )
}
