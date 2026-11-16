package dev.kyralabs.pingogram.feature.chat.ui.pinned

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import dev.kyralabs.pingogram.core.domain.model.Photo
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.image.TdlibFileImage
import dev.kyralabs.pingogram.core.ui.text.rememberFormattedTextStyles
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.toAnnotatedString
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.content.fileSizeText
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * One pinned message on the page (the owner's design, `docs/DESIGN_SYSTEM.md` §6 "Pinned messages bar"): on the rows'
 * `surface` in [shape] (a segment of its month's group), the sender's avatar in its shape, the name in the sender hue's
 * ink (§13.6), when it was sent and a "#23" pin badge on `primaryContainer`; the whole text with its formatting, a
 * photo
 * (`large` corners), a file card or a link preview; then "Jump to message", Copy and Unpin (where [canUnpin]).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PinnedCardItem(
    card: PinnedCard,
    shape: Shape,
    canUnpin: Boolean,
    onEvent: (PinnedPageEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    Surface(modifier = modifier.fillMaxWidth(), shape = shape, color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.padding(start = spacing.medium, end = spacing.medium, top = spacing.medium),
            verticalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            CardHeader(card)
            card.photo?.let { CardPhoto(it) }
            card.clip?.let { CardClip(it) }
            CardText(card)
            card.file?.let { CardFile(it) }
            card.link?.let { CardLink(it) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { onEvent(PinnedPageEvent.Jump(card.messageId)) },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_forward),
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.pinned_jump))
                }
                Spacer(Modifier.weight(1f))
                if (card.canCopy) {
                    IconButton(
                        onClick = { onEvent(PinnedPageEvent.Copy(card.messageId)) },
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(painterResource(UiR.drawable.ic_content_copy), stringResource(R.string.pinned_copy))
                    }
                }
                if (canUnpin) {
                    IconButton(
                        onClick = { onEvent(PinnedPageEvent.Unpin(card.messageId)) },
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(painterResource(UiR.drawable.ic_keep_off), stringResource(R.string.pinned_unpin))
                    }
                }
            }
        }
    }
}

/** The sender's avatar, name and the time over the pin's number. */
@Composable
private fun CardHeader(card: PinnedCard) {
    val spacing = MaterialTheme.spacing
    val name = card.name.resolve()
    val hues = MaterialTheme.pingogramColors.senderHues
    val ink = if (card.senderHue >= 0) hues[card.senderHue].ink else MaterialTheme.colorScheme.primary
    Row(horizontalArrangement = Arrangement.spacedBy(spacing.small), verticalAlignment = Alignment.CenterVertically) {
        Avatar(
            photo = card.avatar.photo,
            name = name,
            id = card.avatar.id,
            size = MaterialTheme.sizes.avatarSmall,
            shape = card.avatarShape,
        )
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }) {
            Text(
                text = name,
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmallEmphasized.userContent,
            )
            Text(
                text = card.dateTime.resolve(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        PinBadge(card.number)
    }
}

/** "#23" after the pin symbol, on `primaryContainer`. */
@Composable
private fun PinBadge(number: Int) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier =
            Modifier
                .background(colors.primaryContainer, MaterialTheme.shapes.extraLarge)
                .padding(horizontal = MaterialTheme.spacing.small, vertical = MaterialTheme.spacing.extraSmall),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(UiR.drawable.ic_push_pin),
            contentDescription = null,
            modifier = Modifier.size(MaterialTheme.sizes.badgeIcon),
            tint = colors.onPrimaryContainer,
        )
        Text(
            text = stringResource(R.string.pinned_number, number),
            color = colors.onPrimaryContainer,
            style = MaterialTheme.typography.labelMediumEmphasized,
        )
    }
}

/** The whole text with its formatting, or what kind of message it is. */
@Composable
private fun CardText(card: PinnedCard) {
    val styles = rememberFormattedTextStyles()
    val kind = card.kind?.resolve()
    if (kind != null) {
        Text(kind, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
    } else if (card.text.text.isNotEmpty()) {
        val text = remember(card.text, styles) { card.text.toAnnotatedString(styles) }
        Text(text, style = MaterialTheme.typography.bodyLarge.userContent)
    }
}

/** A file: its icon on a `primaryContainer` tile, its name over its size and kind, on the card's inner container. */
@Composable
private fun CardFile(file: PinnedFile) {
    val colors = MaterialTheme.colorScheme
    val spacing = MaterialTheme.spacing
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    colors.surfaceContainerHigh,
                    MaterialTheme.shapes.large,
                ).padding(spacing.small),
        horizontalArrangement = Arrangement.spacedBy(spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(
                        MaterialTheme.sizes.leadingIconContainer,
                    ).background(colors.primaryContainer, MaterialTheme.shapes.medium),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(UiR.drawable.ic_download),
                contentDescription = null,
                tint = colors.onPrimaryContainer,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                file.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall.userContent,
            )
            val size = fileSizeText(file.size)
            Text(
                text =
                    if (file.kind.isEmpty()) {
                        size
                    } else {
                        stringResource(
                            R.string.pinned_file_size_kind,
                            size,
                            file.kind,
                        )
                    },
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * A link preview: a leading accent bar in `primary` (Owner decision to confirm, 2026-11-15: the board draws one, which
 * §4 leaves out of reply quotes and link previews), the host in `primary`, the title and the description.
 */
@Composable
private fun CardLink(link: PinnedLink) {
    val colors = MaterialTheme.colorScheme
    val spacing = MaterialTheme.spacing
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(
                    androidx.compose.foundation.layout.IntrinsicSize.Min,
                ).background(colors.surfaceContainerHigh, MaterialTheme.shapes.large)
                .padding(spacing.small),
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        Box(
            Modifier
                .width(
                    MaterialTheme.sizes.pinnedIndicator,
                ).fillMaxHeight()
                .background(colors.primary, MaterialTheme.shapes.extraSmall),
        )
        Column(Modifier.weight(1f)) {
            if (link.host.isNotEmpty()) {
                Text(
                    link.host,
                    color = colors.primary,
                    maxLines = 1,
                    style = MaterialTheme.typography.labelMediumEmphasized,
                )
            }
            if (link.title.isNotEmpty()) Text(link.title, style = MaterialTheme.typography.titleSmall.userContent)
            if (link.description.isNotEmpty()) {
                Text(
                    link.description,
                    color = colors.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium.userContent,
                )
            }
        }
    }
}
