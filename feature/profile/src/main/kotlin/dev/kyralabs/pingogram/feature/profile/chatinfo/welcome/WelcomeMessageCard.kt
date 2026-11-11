package dev.kyralabs.pingogram.feature.profile.chatinfo.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.Photo
import dev.kyralabs.pingogram.core.ui.text.rememberFormattedTextStyles
import dev.kyralabs.pingogram.core.ui.text.spokenList
import dev.kyralabs.pingogram.core.ui.text.toAnnotatedString
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.profile.R
import dev.kyralabs.pingogram.feature.profile.ui.ProfileImage
import dev.kyralabs.pingogram.feature.profile.ui.ProfileSectionHeader
import dev.kyralabs.pingogram.feature.profile.ui.fileIdFor
import dev.kyralabs.pingogram.feature.profile.ui.profileRowContainerColor
import kotlinx.collections.immutable.ImmutableList
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * One welcome message as a card of the group's segmented list ([shape], the rows' container): its photo
 * (`sizes.avatarMedium`, `large` corners) beside its text in `bodyLarge` with its formatting (user content, six lines
 * at most), or why it cannot be shown; then its kind on a `surfaceContainerHighest` pill and "Edit" and "Delete"
 * icon buttons, "Delete" in `error` (a [LoadingIndicator] while it goes). Screen readers hear the card as one item
 * ("Message 1, Text, Welcome to…"), the buttons as buttons of their own.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun WelcomeMessageCard(
    item: WelcomeItem,
    position: Int,
    shape: Shape,
    isDeleting: Boolean,
    onEvent: (WelcomeMessagesEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    val resources = LocalResources.current
    val styles = rememberFormattedTextStyles()
    val text = remember(item.text, styles) { item.text.toAnnotatedString(styles) }
    val kind = stringResource(item.kind)
    val other = stringResource(R.string.welcome_other_text)
    val label = stringResource(R.string.welcome_message_description, position)
    Surface(modifier = modifier, shape = shape, color = profileRowContainerColor()) {
        Column(Modifier.padding(start = spacing.medium, top = spacing.medium, end = spacing.extraSmall)) {
            Row(
                modifier =
                    Modifier.padding(end = spacing.small).semantics(mergeDescendants = true) {
                        contentDescription =
                            spokenList(
                                resources,
                                listOf(label, kind, if (item.isSupported) item.text.text else other),
                            )
                    },
                horizontalArrangement = Arrangement.spacedBy(spacing.medium),
            ) {
                item.photo?.let { WelcomePhotoImage(it, MaterialTheme.sizes.avatarMedium) }
                Text(
                    text = if (item.isSupported) text else AnnotatedString(other),
                    modifier = Modifier.clearAndSetSemantics {},
                    color =
                        if (item.isSupported) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    maxLines = MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge.userContent,
                )
            }
            CardActions(item, position, kind, isDeleting, onEvent)
        }
    }
}

/** A card's kind, then "Edit" (for a message Pingogram can show) and "Delete", or its progress while it goes. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CardActions(
    item: WelcomeItem,
    position: Int,
    kind: String,
    isDeleting: Boolean,
    onEvent: (WelcomeMessagesEvent) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    Row(verticalAlignment = Alignment.CenterVertically) {
        KindPill(kind)
        Spacer(Modifier.weight(1f))
        if (item.isSupported) {
            IconButton(
                onClick = { onEvent(WelcomeMessagesEvent.Edit(item.id)) },
                enabled = !isDeleting,
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(
                    painter = painterResource(UiR.drawable.ic_edit),
                    contentDescription = stringResource(R.string.welcome_edit, position),
                )
            }
        }
        if (isDeleting) {
            LoadingIndicator(
                Modifier
                    .padding(spacing.extraSmall)
                    .size(MaterialTheme.sizes.leadingIconContainer),
            )
        } else {
            IconButton(
                onClick = { onEvent(WelcomeMessagesEvent.Delete(item.id)) },
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(
                    painter = painterResource(UiR.drawable.ic_delete),
                    contentDescription = stringResource(R.string.welcome_delete, position),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** What a message is, on a tonal pill; decorative, as the card says it. */
@Composable
private fun KindPill(kind: String) {
    val colors = MaterialTheme.colorScheme
    Text(
        text = kind,
        modifier =
            Modifier
                .clearAndSetSemantics {}
                .background(colors.surfaceContainerHighest, CircleShape)
                .padding(horizontal = MaterialTheme.spacing.small, vertical = MaterialTheme.spacing.extraSmall),
        color = colors.onSurfaceVariant,
        maxLines = 1,
        style = MaterialTheme.typography.labelMediumEmphasized,
    )
}

/**
 * What new members see: "What new members see" over the messages as incoming bubbles (`surfaceContainerHigh`,
 * `extraLarge` corners) on a `surfaceContainer` panel with `extraLarge` corners, under "Welcome message · only they
 * see this" in `labelMediumEmphasized` `primary`. Each bubble shows its photo (`sizes.avatarSmall`) and three lines of
 * its text. Screen readers hear the panel as one item.
 */
@Composable
internal fun WelcomePreview(
    messages: ImmutableList<WelcomeItem>,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    val resources = LocalResources.current
    val labelText = stringResource(R.string.welcome_preview_label)
    val photo = stringResource(R.string.welcome_photo)
    val shown = messages.filter { it.isSupported }
    Column(modifier) {
        ProfileSectionHeader(stringResource(R.string.welcome_preview_title))
        Surface(
            modifier =
                Modifier.fillMaxWidth().clearAndSetSemantics {
                    contentDescription =
                        spokenList(
                            resources,
                            listOf(labelText) +
                                shown.flatMap { listOfNotNull(photo.takeIf { _ -> it.photo != null }, it.text.text) },
                        )
                },
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(Modifier.padding(spacing.medium), verticalArrangement = Arrangement.spacedBy(spacing.extraSmall)) {
                Text(
                    text = labelText,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMediumEmphasized,
                )
                shown.forEach { PreviewBubble(it.text, it.photo) }
            }
        }
    }
}

/** One message of the preview as an incoming bubble, its photo before its text. */
@Composable
private fun PreviewBubble(
    text: FormattedText,
    photo: Photo?,
) {
    val spacing = MaterialTheme.spacing
    Row(
        modifier =
            Modifier
                .widthIn(max = MaterialTheme.sizes.bubbleMaxWidth)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.extraLarge)
                .padding(horizontal = spacing.medium, vertical = spacing.small),
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        photo?.let { WelcomePhotoImage(it, MaterialTheme.sizes.avatarSmall) }
        if (text.text.isNotBlank()) {
            Text(
                text = text.text,
                maxLines = PREVIEW_LINES,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.userContent,
            )
        }
    }
}

/** A message's photo at [size] with `large` corners, dimmed in dark theme as media are; decorative. */
@Composable
internal fun WelcomePhotoImage(
    photo: Photo,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    ProfileImage(
        fileId = photo.fileIdFor(sizePx),
        sizePx = sizePx,
        modifier = modifier.size(size).clip(MaterialTheme.shapes.large),
        colorFilter = MaterialTheme.pingogramColors.mediaColorFilter,
    )
}

private const val MAX_LINES = 6
private const val PREVIEW_LINES = 3
