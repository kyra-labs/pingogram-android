package dev.kyralabs.pingogram.feature.chat.ui.content

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.domain.model.ExpiredMedia
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.text.formatPhoneNumber
import dev.kyralabs.pingogram.core.ui.text.numericContent
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.BubbleColors
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * A location message (`REQUIREMENTS.md` FR-C4), padded in its bubble: a location icon, "Location" (or "Live location"
 * while it is shared live) and the coordinates. No map: Pingogram loads no map tiles from third parties
 * (`REQUIREMENTS.md` §6.2). Tapping it calls [onOpen], e.g. to open the point in a maps app.
 */
@Composable
internal fun MessageLocation(
    content: MessageContent.Location,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    colors: BubbleColors = MaterialTheme.pingogramColors.incomingBubble,
) {
    InfoRow(
        icon = UiR.drawable.ic_location_on_filled,
        title =
            stringResource(
                if (content.livePeriod != 0) UiR.string.preview_live_location else UiR.string.preview_location,
            ),
        subtitle = coordinatesText(content.location),
        colors = colors,
        modifier = modifier,
        onClick = onOpen,
    )
}

/**
 * A venue message (`REQUIREMENTS.md` FR-C4), padded in its bubble: a location icon, the place's name and its address
 * (or its coordinates without one). Tapping it calls [onOpen].
 */
@Composable
internal fun MessageVenue(
    content: MessageContent.Venue,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    colors: BubbleColors = MaterialTheme.pingogramColors.incomingBubble,
) {
    val venue = content.venue
    InfoRow(
        icon = UiR.drawable.ic_location_on_filled,
        title = venue.title.ifBlank { stringResource(UiR.string.preview_location) },
        subtitle = venue.address.ifBlank { coordinatesText(venue.location) },
        colors = colors,
        modifier = modifier,
        onClick = onOpen,
    )
}

/**
 * A shared contact (`REQUIREMENTS.md` FR-C4), padded in its bubble: an [Avatar] with the contact's initials, the name
 * and the phone number, formatted for the user's country. Actions (message, add to contacts) come with contacts (M4).
 */
@Composable
internal fun MessageContact(
    content: MessageContent.Contact,
    modifier: Modifier = Modifier,
    colors: BubbleColors = MaterialTheme.pingogramColors.incomingBubble,
) {
    val contact = content.contact
    val name =
        listOf(contact.firstName, contact.lastName)
            .filter(String::isNotBlank)
            .joinToString(" ")
            .ifBlank { stringResource(UiR.string.preview_contact) }
    val country = LocalConfiguration.current.locales[0].country
    val phone = remember(contact.phoneNumber, country) { formatPhoneNumber(contact.phoneNumber, country) }
    Row(
        modifier = modifier.semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(
            photo = null,
            name = name,
            id = contact.userId.takeIf { it != 0L } ?: name.hashCode().toLong(),
            size = MaterialTheme.sizes.avatarSmall,
            onlineRingColor = colors.container,
        )
        InfoText(title = name, subtitle = phone, colors = colors, modifier = Modifier.weight(1f, fill = false))
    }
}

/** A game a bot sent (`REQUIREMENTS.md` FR-C4), padded in its bubble: a game icon, its title and "Game". */
@Composable
internal fun MessageGame(
    content: MessageContent.Game,
    modifier: Modifier = Modifier,
    colors: BubbleColors = MaterialTheme.pingogramColors.incomingBubble,
) {
    val kind = stringResource(UiR.string.preview_game)
    InfoRow(
        icon = UiR.drawable.ic_sports_esports_filled,
        title = content.title.ifBlank { kind },
        subtitle = kind.takeIf { content.title.isNotBlank() },
        colors = colors,
        modifier = modifier,
    )
}

/**
 * An invoice from a bot or a business (`REQUIREMENTS.md` FR-C4, §1.2: Pingogram does not pay), padded in its bubble:
 * a receipt icon, its title and "Invoice".
 */
@Composable
internal fun MessageInvoice(
    content: MessageContent.Invoice,
    modifier: Modifier = Modifier,
    colors: BubbleColors = MaterialTheme.pingogramColors.incomingBubble,
) {
    val kind = stringResource(UiR.string.preview_invoice)
    InfoRow(
        icon = UiR.drawable.ic_receipt_long_filled,
        title = content.title.ifBlank { kind },
        subtitle = kind.takeIf { content.title.isNotBlank() },
        colors = colors,
        modifier = modifier,
    )
}

/** A forwarded story (`REQUIREMENTS.md` FR-C4, §5.9), padded in its bubble: a story icon and "Story". */
@Composable
internal fun MessageStory(
    modifier: Modifier = Modifier,
    colors: BubbleColors = MaterialTheme.pingogramColors.incomingBubble,
) {
    InfoRow(
        icon = UiR.drawable.ic_web_stories_filled,
        title = stringResource(UiR.string.preview_story),
        subtitle = null,
        colors = colors,
        modifier = modifier,
    )
}

/**
 * Self-destructing media that was opened and is gone (`REQUIREMENTS.md` FR-C4), padded in its bubble: "Photo
 * expired", "Video expired", … in italic `bodyMedium`, in the bubble's content color.
 */
@Composable
internal fun MessageExpired(
    content: MessageContent.Expired,
    modifier: Modifier = Modifier,
    colors: BubbleColors = MaterialTheme.pingogramColors.incomingBubble,
) {
    Text(
        text =
            stringResource(
                when (content.media) {
                    ExpiredMedia.Photo -> UiR.string.preview_expired_photo
                    ExpiredMedia.Video -> UiR.string.preview_expired_video
                    ExpiredMedia.VideoNote -> UiR.string.preview_expired_video_message
                    ExpiredMedia.VoiceNote -> UiR.string.preview_expired_voice_message
                },
            ),
        modifier = modifier,
        color = colors.content,
        fontStyle = FontStyle.Italic,
        style = MaterialTheme.typography.bodyMedium,
    )
}

/**
 * A row of content that is an icon and two lines: [icon] on an `accent` circle ([ContentIcon]), [title] and
 * [subtitle]. Tapping it calls [onClick] if there is one; screen readers hear both lines at once.
 */
@Composable
internal fun InfoRow(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String?,
    colors: BubbleColors,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier =
            if (onClick != null) {
                modifier.clickable(onClickLabel = stringResource(R.string.content_open), onClick = onClick)
            } else {
                modifier.semantics(mergeDescendants = true) {}
            },
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ContentIcon(icon, colors)
        InfoText(title = title, subtitle = subtitle, colors = colors, modifier = Modifier.weight(1f, fill = false))
    }
}

/** [title] in `titleSmall` over [subtitle] in `bodySmall`, both in the bubble's content color, two lines at most. */
@Composable
private fun InfoText(
    title: String,
    subtitle: String?,
    colors: BubbleColors,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall)) {
        Text(
            text = title,
            color = colors.content,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleSmall.userContent,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                color = colors.content,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                // Phone numbers and coordinates too, which read left to right in every language.
                style = MaterialTheme.typography.bodySmall.numericContent,
            )
        }
    }
}
