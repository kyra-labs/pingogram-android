package dev.kyralabs.pingogram.feature.media.story

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.media.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The views pill of the user's own story (board 26, owner decision 2026-11-15: across the bar's full width): a
 * `FilledTonalButton` at the composer's height with the faces of who saw it last (`avatarExtraSmall`, overlapping),
 * "24 views · ❤ 6" in `labelLargeEmphasized` and an arrow up, which opens the list of viewers. Screen readers hear
 * "Viewed by 24, 6 reactions, opens viewer list".
 */
@Composable
internal fun StoryViewsPill(
    views: StoryViewsUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    val avatar = MaterialTheme.sizes.avatarExtraSmall
    FilledTonalButton(
        onClick = onClick,
        modifier =
            modifier.heightIn(min = MaterialTheme.sizes.composerField).semantics {
                val viewed =
                    resources.getQuantityString(R.plurals.story_views_description, views.viewCount, views.viewCount)
                val reactions =
                    resources.getQuantityString(R.plurals.story_reactions, views.reactionCount, views.reactionCount)
                contentDescription = resources.getString(R.string.story_views_pill_description, viewed, reactions)
            },
        shapes = ButtonDefaults.shapes(),
    ) {
        Row(
            modifier = Modifier.clearAndSetSemantics {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            // Each face a third over the one before it.
            Box {
                views.recent.forEachIndexed { index, face ->
                    Avatar(
                        photo = face.photo,
                        name = face.name,
                        id = face.userId,
                        size = avatar,
                        modifier = Modifier.padding(start = avatar * index * (OVERLAP - 1) / OVERLAP),
                    )
                }
            }
            Text(
                text = viewsText(views),
                modifier = Modifier.weight(1f, fill = false).padding(start = MaterialTheme.spacing.extraSmall),
                style = MaterialTheme.typography.labelLargeEmphasized.copy(fontFeatureSettings = "tnum"),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(painterResource(R.drawable.ic_keyboard_arrow_up), contentDescription = null)
        }
    }
}

/** "24 views · ❤ 6", or "24 views" without reactions. */
@Composable
private fun viewsText(views: StoryViewsUi): String {
    val resources = LocalResources.current
    val seen = resources.getQuantityString(R.plurals.story_views, views.viewCount, views.viewCount)
    return if (views.reactionCount > 0) {
        resources.getString(R.string.story_views_with_reactions, seen, views.reactionCount)
    } else {
        seen
    }
}

/** The faces overlap by a third of their size. */
private const val OVERLAP = 3
