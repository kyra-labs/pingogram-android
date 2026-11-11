package dev.kyralabs.pingogram.feature.calls.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.calls.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The participants while the comments pane shows (`docs/DESIGN_SYSTEM.md` §6, "Voice chat comments"): a keyed grid of
 * tiles above the pane, the videos (and a conference call's "Invite link") across its width first. Each tile, on
 * `surfaceContainerHigh` with `extraLarge` corners, holds the avatar ringed in `primary` while the participant speaks
 * (the ring fades in the draw phase), the name and what they are doing; one item for screen readers.
 */
@Composable
internal fun ParticipantTiles(
    state: GroupCallUiState.InCall,
    videos: ParticipantVideo,
    self: CallVideo,
    onEvent: (GroupCallEvent) -> Unit,
    conference: ConferenceActions,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    LazyVerticalGrid(
        columns = GridCells.Adaptive(MaterialTheme.sizes.sharedMediaCell),
        modifier = modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        contentPadding = PaddingValues(horizontal = spacing.medium, vertical = spacing.small),
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
        verticalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        if (state.video.remote.isNotEmpty()) {
            item(key = "videos", contentType = "videos", span = { GridItemSpan(maxLineSpan) }) {
                VideoGrid(state, videos, self, onEvent)
            }
        }
        val link = state.inviteLink
        if (state.isConference && !link.isNullOrEmpty()) {
            item(key = "link", contentType = "link", span = { GridItemSpan(maxLineSpan) }) {
                InviteLinkChip(onClick = { conference.onShareLink(link) })
            }
        }
        if (state.canInvite) {
            item(key = "add", contentType = "add", span = { GridItemSpan(maxLineSpan) }) {
                AddPeopleRow(conference.onAddPeople)
            }
        }
        items(state.participants, key = { it.key }, contentType = { "tile" }) { participant ->
            ParticipantTile(participant, Modifier.animateItem())
        }
    }
}

/** A conference call's "Add people" and "Invite link". */
internal class ConferenceActions(
    val onAddPeople: () -> Unit,
    val onShareLink: (String) -> Unit,
)

/** "Invite link": an `AssistChip` with the link symbol, which shares the conference call's link. */
@Composable
private fun InviteLinkChip(onClick: () -> Unit) {
    Row {
        AssistChip(
            onClick = onClick,
            label = { Text(stringResource(R.string.group_call_invite_link)) },
            leadingIcon = {
                Icon(
                    painterResource(UiR.drawable.ic_link),
                    contentDescription = null,
                    modifier = Modifier.size(AssistChipDefaults.IconSize),
                )
            },
            colors =
                AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            border = null,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ParticipantTile(
    participant: GroupCallParticipantUi,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val speaking = participant.state == ParticipantState.Speaking
    val ringSpec = if (LocalStillCallAnimations.current) snap() else MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val ring by animateFloatAsState(if (speaking) 1f else 0f, ringSpec)
    val ringWidth = MaterialTheme.sizes.avatarRing
    val name =
        participant.name.ifBlank { stringResource(R.string.call_someone) }.let {
            if (participant.isCurrentUser) stringResource(R.string.group_call_you, it) else it
        }
    val stateText = stringResource(participant.state.label())
    Surface(
        color = colors.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier.clearAndSetSemantics { contentDescription = "$name, $stateText" },
    ) {
        Column(
            Modifier.fillMaxWidth().padding(MaterialTheme.spacing.small),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Avatar(
                photo = participant.photo,
                name = participant.name,
                id = participant.id,
                size = MaterialTheme.sizes.avatarMedium,
                modifier =
                    Modifier.padding(ringWidth * 2).drawBehind {
                        if (ring > 0f) {
                            drawCircle(
                                color = colors.primary.copy(alpha = ring),
                                radius = size.minDimension / 2 + ringWidth.toPx(),
                                style = Stroke(ringWidth.toPx()),
                            )
                        }
                    },
            )
            Spacer(Modifier.height(MaterialTheme.spacing.extraSmall))
            Text(
                name,
                style = MaterialTheme.typography.labelLargeEmphasized.userContent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(
                stateText,
                style = MaterialTheme.typography.labelSmall,
                color = if (speaking) colors.primary else colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
