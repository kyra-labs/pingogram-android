package dev.kyralabs.pingogram.feature.chats.ui.stories

import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.spokenList
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chats.R
import dev.kyralabs.pingogram.feature.chats.ui.isLargeFontScale
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The chats with active stories above the chat list, as Telegram's apps show them, built from Material parts as
 * Material has no stories component: a keyed [LazyRow] of [StoryRingItem]s, each ring growing in and moving on the
 * motion scheme's springs as the list changes. It reports [onNearEnd] while its end is within [LOAD_MORE_THRESHOLD]
 * rings of the screen, so more chats load: the one effect of the chat list's first row, not one per chat. The route
 * shows the row only while there are rings, so it never takes room empty.
 */
@Composable
internal fun StoryRingsRow(
    rings: ImmutableList<StoryRing>,
    onOpen: (chatId: Long) -> Unit,
    onNearEnd: () -> Unit,
    modifier: Modifier = Modifier,
    onAddStory: () -> Unit = {},
    onShowUpload: () -> Unit = {},
) {
    val listState = rememberLazyListState()
    val motionScheme = MaterialTheme.motionScheme
    val spacing = MaterialTheme.spacing
    LazyRow(
        modifier = modifier,
        state = listState,
        contentPadding = PaddingValues(horizontal = spacing.small, vertical = spacing.small),
        horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall),
    ) {
        items(rings, key = { it.chatId }, contentType = { STORY_RING }) { ring ->
            StoryRingItem(
                ring = ring,
                onClick = {
                    when {
                        ring.upload != null -> onShowUpload()
                        ring.isMine && ring.storyCount == 0 -> onAddStory()
                        else -> onOpen(ring.chatId)
                    }
                },
                onAddStory = onAddStory.takeIf { ring.isMine },
                modifier =
                    Modifier.animateItem(
                        fadeInSpec = motionScheme.defaultEffectsSpec(),
                        placementSpec = motionScheme.defaultSpatialSpec(),
                        fadeOutSpec = motionScheme.fastEffectsSpec(),
                    ),
            )
        }
    }
    val currentOnNearEnd by rememberUpdatedState(onNearEnd)
    LaunchedEffect(listState) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            layout.totalItemsCount > 0 && lastVisible >= layout.totalItemsCount - 1 - LOAD_MORE_THRESHOLD
        }.distinctUntilChanged()
            .filter { it }
            .collect { currentOnNearEnd() }
    }
}

/**
 * One chat's stories: its avatar (`sizes.avatarRow`, a circle like every avatar) in a ring of `sizes.avatarRing`,
 * `primary` while a story is unseen and `outlineVariant` once all are seen, drawn in the draw phase a ring's width
 * away from the avatar; under it the chat's name (or "Your story") in `labelSmall` on one line (two from 150% font), as
 * user content. One button for screen readers: "Ada, 3 stories, not seen".
 *
 * While the user's stories are being posted (board 10c), their ring is a determinate wavy progress indicator in
 * `primary` around the avatar (`sizes.avatarSmall`, leaving the wave its room), with a cancel badge and "Posting 62%"
 * in `primary` under it; once TDLib gave up, the ring turns `error` with an error badge and "Not posted". A tap then
 * shows what is posting ([StoryUploadSheet]).
 */
@Composable
internal fun StoryRingItem(
    ring: StoryRing,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onAddStory: (() -> Unit)? = null,
) {
    val sizes = MaterialTheme.sizes
    val spacing = MaterialTheme.spacing
    val resources = LocalResources.current
    val name = if (ring.isMine) resources.getString(R.string.stories_your_story) else ring.name
    val upload = ring.upload
    // The user's own ring without a story: no ring, an add badge, and a tap posts one.
    val adds = ring.isMine && ring.storyCount == 0 && upload == null
    val addLabel = resources.getString(R.string.stories_add_story)
    val label = upload?.let { uploadLabel(resources, it) } ?: name
    Column(
        modifier =
            modifier
                .width(sizes.avatarMedium + spacing.large)
                .clip(MaterialTheme.shapes.large)
                .combinedClickable(role = Role.Button, onLongClick = onAddStory, onClick = onClick)
                .clearAndSetSemantics {
                    contentDescription =
                        if (upload != null) {
                            spokenList(resources, listOf(name, label))
                        } else {
                            ringDescription(resources, ring, name, adds)
                        }
                    if (onAddStory != null && !adds) {
                        customActions = listOf(CustomAccessibilityAction(addLabel) { onAddStory().let { true } })
                    }
                }.padding(vertical = spacing.extraSmall),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.extraSmall),
    ) {
        if (upload != null) UploadRing(ring, upload) else StoryRingPicture(ring, adds)
        RingLabel(label, upload)
    }
}

/** The avatar in its ring (none while [adds], an add badge instead). */
@Composable
private fun StoryRingPicture(
    ring: StoryRing,
    adds: Boolean,
) {
    val sizes = MaterialTheme.sizes
    val colors = MaterialTheme.colorScheme
    val ringColor = if (ring.hasUnread) colors.primary else colors.outlineVariant
    val ringWidth = sizes.avatarRing
    Box(
        modifier =
            Modifier
                .size(sizes.avatarMedium)
                .drawBehind { if (!adds) drawRing(ringColor, ringWidth.toPx()) },
        contentAlignment = Alignment.Center,
    ) {
        Avatar(photo = ring.photo, name = ring.name, id = ring.chatId, size = sizes.avatarRow)
        if (adds) AddBadge(Modifier.align(Alignment.BottomEnd))
    }
}

/** The name under the ring, or while posting how far it is (`primary`) or that it failed (`error`). */
@Composable
private fun RingLabel(
    label: String,
    upload: StoryRingUpload?,
) {
    val colors = MaterialTheme.colorScheme
    Text(
        text = label,
        modifier = Modifier.fillMaxWidth(),
        style =
            if (upload != null) {
                MaterialTheme.typography.labelSmallEmphasized
            } else {
                MaterialTheme.typography.labelSmall.userContent
            },
        color =
            when {
                upload == null -> colors.onSurface
                upload.failed > 0 -> colors.error
                else -> colors.primary
            },
        textAlign = TextAlign.Center,
        // A second line at large font scales, where one line of the ring's width cuts most names.
        maxLines = if (isLargeFontScale()) 2 else 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** "Ada, 3 stories, not seen"; for the user's own ring without a story, "Your story, Add a story". */
private fun ringDescription(
    resources: Resources,
    ring: StoryRing,
    name: String,
    adds: Boolean,
): String {
    if (adds) return spokenList(resources, listOf(name, resources.getString(R.string.stories_add_story)))
    val count = resources.getQuantityString(R.plurals.stories_count, ring.storyCount, ring.storyCount)
    val seen = resources.getString(if (ring.hasUnread) R.string.stories_a11y_unseen else R.string.stories_a11y_seen)
    return spokenList(resources, listOf(name, count, seen))
}

/** The add symbol on a `primary` circle, ringed in the row's `surface`, at the avatar's bottom end. Decorative. */
@Composable
private fun AddBadge(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    RingBadge(UiR.drawable.ic_add, colors.primary, colors.onPrimary, modifier)
}

/** The ring along the edge of the box, [width] pixels wide, inside it. */
internal fun DrawScope.drawRing(
    color: Color,
    width: Float,
) {
    drawCircle(
        color = color,
        radius = (size.minDimension - width) / 2,
        center = Offset(size.width / 2, size.height / 2),
        style = Stroke(width),
    )
}

/** How close to its end the row asks for more chats with stories. */
private const val LOAD_MORE_THRESHOLD = 4

private const val STORY_RING = "story_ring"

/** Rings for previews: unseen, seen, the user's own and a long name. */
internal object StoryRingsPreviewData {
    val rings =
        persistentListOf(
            StoryRing(chatId = 1, name = "Me", photo = null, hasUnread = true, storyCount = 1, isMine = true),
            StoryRing(chatId = 2, name = "Ada Lovelace", photo = null, hasUnread = true, storyCount = 3),
            StoryRing(chatId = 3, name = "Grace Hopper", photo = null, hasUnread = true, storyCount = 1),
            StoryRing(chatId = 4, name = "Pingogram News", photo = null, hasUnread = false, storyCount = 2),
            StoryRing(chatId = 5, name = "Alan Turing", photo = null, hasUnread = false, storyCount = 1),
            StoryRing(chatId = 6, name = "Margaret Hamilton", photo = null, hasUnread = false, storyCount = 4),
        )

    /** The user's stories being posted, 62% sent. */
    val posting =
        rings.toPersistentList().set(
            0,
            rings[0].copy(upload = StoryRingUpload(persistentListOf(-1, -2), progress = 0.62f)),
        )

    /** One of the user's stories failed. */
    val failed =
        rings.toPersistentList().set(
            0,
            rings[0].copy(upload = StoryRingUpload(persistentListOf(-1), progress = null, failed = 1)),
        )

    /** The user without a story of their own: their ring offers to post one. */
    val withoutOwnStory =
        rings.toPersistentList().set(0, StoryRing(1, "Me", null, hasUnread = false, storyCount = 0, isMine = true))
}

internal class StoryRingsPreviewStates : PreviewParameterProvider<ImmutableList<StoryRing>> {
    override val values =
        sequenceOf(
            StoryRingsPreviewData.rings,
            persistentListOf(StoryRingsPreviewData.rings[1]),
            StoryRingsPreviewData.posting,
            StoryRingsPreviewData.failed,
        )
}

@PingogramPreviews
@Composable
private fun StoryRingsRowPreview(
    @PreviewParameter(StoryRingsPreviewStates::class) rings: ImmutableList<StoryRing>,
) {
    PingogramPreview { StoryRingsRow(rings = rings, onOpen = {}, onNearEnd = {}) }
}
