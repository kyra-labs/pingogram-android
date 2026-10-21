package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.DpSize
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.TextBlocksLayout
import dev.kyralabs.pingogram.core.ui.text.rememberFormattedTextStyles
import dev.kyralabs.pingogram.core.ui.text.textBlocks
import dev.kyralabs.pingogram.core.ui.text.toAnnotatedString
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.content.MessageAnimation
import dev.kyralabs.pingogram.feature.chat.ui.content.MessagePhoto
import dev.kyralabs.pingogram.feature.chat.ui.content.MessageVideo
import dev.kyralabs.pingogram.feature.chat.ui.content.PhotoImage
import dev.kyralabs.pingogram.feature.chat.ui.content.PreviewImages
import dev.kyralabs.pingogram.feature.chat.ui.preview.SponsoredPreviewData
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * A sponsored message among the messages (`REQUIREMENTS.md` §2; `docs/DESIGN_SYSTEM.md` §6 "Sponsored message"): a
 * filled [Card] with its default shape and colors, as wide as a bubble can be, at the start like an incoming message.
 * It shows the message as TDLib returned it, never shortened or restyled, clearly labelled:
 *
 * - **Header**: the sponsor's photo where there is one, the label "Sponsored" ("Recommended" for a recommended
 *   channel) and the sponsor's name, and an info button that opens "About this ad" ([SponsoredEvent.ShowInfo]).
 * - **Text** with its formatting, spoilers shown; a link in it opens with the ad ([SponsoredEvent.Open] with its URL).
 * - **Media**: the photo, video or GIF, which opens the ad too ([SponsoredEvent.Open] as a media click).
 * - **Button**: the advertiser's, a [FilledTonalButton] with the press morph of [ButtonDefaults.shapes], which opens
 *   the ad ([SponsoredEvent.Open]).
 *
 * Screen readers hear the label, the name and the text as one, with "About this ad" as a custom action; the media,
 * the info button and the advertiser's button are their own. No fixed heights: it grows with the font scale. Nothing
 * here reports it as viewed: the list does, from its layout ([ReportSponsoredViews]).
 */
@Composable
internal fun SponsoredMessageCard(
    card: SponsoredCard,
    onEvent: (ChatEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    Box(modifier.fillMaxWidth()) {
        Card(Modifier.widthIn(max = MaterialTheme.sizes.bubbleMaxWidth).fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(horizontal = spacing.medium, vertical = spacing.small),
                verticalArrangement = Arrangement.spacedBy(spacing.small),
            ) {
                SponsoredContent(card, onEvent)
                card.media?.let { media ->
                    SponsoredMedia(media, onOpen = { onEvent(SponsoredEvent.Open(card.id, isMediaClick = true)) })
                }
                FilledTonalButton(
                    onClick = { onEvent(SponsoredEvent.Open(card.id)) },
                    modifier = Modifier.fillMaxWidth(),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = card.buttonText, style = MaterialTheme.typography.labelLarge.userContent)
                }
            }
        }
    }
}

/** The header with the info button, and the text: one node for screen readers, the info button apart. */
@Composable
private fun SponsoredContent(
    card: SponsoredCard,
    onEvent: (ChatEvent) -> Unit,
) {
    val about =
        stringResource(if (card.isRecommended) R.string.sponsored_about_recommended else R.string.sponsored_about)
    val currentOnEvent by rememberUpdatedState(onEvent)
    val showInfo = remember(card.id) { { currentOnEvent(SponsoredEvent.ShowInfo(card.id)) } }
    Column(
        modifier =
            Modifier.semantics(mergeDescendants = true) {
                customActions =
                    listOf(
                        CustomAccessibilityAction(about) {
                            showInfo()
                            true
                        },
                    )
            },
    ) {
        SponsoredHeader(card, about, showInfo)
        if (card.text.text.isNotEmpty()) {
            val colors = MaterialTheme.colorScheme
            val styles =
                rememberFormattedTextStyles(
                    codeBackground = colors.surfaceContainerHigh,
                    spoilerColor = colors.onSurfaceVariant,
                )
            val onLink =
                remember(card.id) {
                    LinkInteractionListener { link ->
                        val url = (link as? LinkAnnotation.Url)?.url ?: return@LinkInteractionListener
                        currentOnEvent(SponsoredEvent.Open(card.id, url = url))
                    }
                }
            val text =
                remember(card.text, styles, onLink) {
                    card.text.toAnnotatedString(
                        styles,
                        spoilersRevealed = true,
                        onLinkClick = onLink,
                        drawsBlocks = true,
                    )
                }
            // Code on rounded blocks, as in the bubbles.
            val blocks = remember { TextBlocksLayout() }
            Text(
                text = text,
                modifier =
                    Modifier.textBlocks(
                        layout = blocks,
                        codeColor = styles.codeBackground,
                        spoilerColor = styles.spoilerColor,
                        shape = MaterialTheme.shapes.small,
                        horizontalPadding = MaterialTheme.sizes.textBlockPadding,
                    ),
                style = MaterialTheme.typography.bodyMedium.userContent,
                onTextLayout = blocks.onTextLayout,
            )
        }
    }
}

/** The sponsor's photo, the label and the sponsor's name, and the info button ([about]) that calls [onInfo]. */
@Composable
private fun SponsoredHeader(
    card: SponsoredCard,
    about: String,
    onInfo: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        card.sponsorPhoto?.let { photo ->
            val size = MaterialTheme.sizes.avatarSmall
            PhotoImage(
                photo = photo,
                displaySize = DpSize(size, size),
                hidden = false,
                modifier = Modifier.padding(end = MaterialTheme.spacing.medium).size(size).clip(CircleShape),
                // An avatar: never dimmed like the media.
                colorFilter = null,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text =
                    stringResource(
                        if (card.isRecommended) R.string.sponsored_label_recommended else R.string.sponsored_label,
                    ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
            if (card.title.isNotBlank()) {
                Text(text = card.title, style = MaterialTheme.typography.titleSmallEmphasized.userContent)
            }
        }
        IconButton(onClick = onInfo, shapes = IconButtonDefaults.shapes()) {
            Icon(painter = painterResource(UiR.drawable.ic_info), contentDescription = about)
        }
    }
}

/**
 * The ad's photo, video or GIF, as in a message, with the shape of small things nested in a card (`small`), opening
 * the ad when tapped ([onOpen]).
 */
@Composable
private fun SponsoredMedia(
    media: MessageContent,
    onOpen: () -> Unit,
) {
    val shape = MaterialTheme.shapes.small
    when (media) {
        is MessageContent.Photo -> MessagePhoto(media, onOpen, shape = shape)
        is MessageContent.Video -> MessageVideo(media, onOpen, shape = shape)
        is MessageContent.Animation -> MessageAnimation(media, onOpen, shape = shape)
        else -> Unit
    }
}

/** The cards of the previews: a text with formatting, a recommended channel, a photo, and a long text. */
internal class SponsoredCardPreviews : PreviewParameterProvider<SponsoredCard> {
    override val values: Sequence<SponsoredCard> =
        with(SponsoredPreviewData) { sequenceOf(text, recommended, photo, longText) }
}

@PingogramPreviews
@Composable
private fun SponsoredMessageCardPreview(
    @PreviewParameter(SponsoredCardPreviews::class) card: SponsoredCard,
) {
    PingogramPreview {
        PreviewImages {
            SponsoredMessageCard(card, onEvent = {}, modifier = Modifier.padding(MaterialTheme.spacing.medium))
        }
    }
}
