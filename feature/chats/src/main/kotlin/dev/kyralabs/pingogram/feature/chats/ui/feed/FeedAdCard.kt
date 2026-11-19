package dev.kyralabs.pingogram.feature.chats.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import coil3.compose.AsyncImage
import dev.kyralabs.pingogram.core.ui.image.TdlibFileImage
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.rememberFormattedTextStyles
import dev.kyralabs.pingogram.core.ui.text.toAnnotatedString
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chats.R
import dev.kyralabs.pingogram.feature.chats.ui.preview.FeedPreviewData
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * A channel's sponsored message in the feed (`REQUIREMENTS.md` §2; `docs/DESIGN_SYSTEM.md` §6 "Channel feed", frames
 * 3 and 3b), after that channel's newest post: a full-size card on an unread post's `surfaceContainerHigh` and shape,
 * never dimmed, shrunk or collapsed, showing the ad exactly as TDLib returned it.
 *
 * - **Header**: the advertiser's round photo, its name, "Sponsored" ("Recommended") with "· in" the channel, then ⓘ
 *   "About this ad" ([FeedAdEvent.ShowInfo]) and × "Report ad", which opens the same sheet at the report.
 * - **Text** and **media** as a post's; the media opens the ad as a media click.
 * - **Button**: the advertiser's, full width (`FilledTonalButton`, `open_in_new`), opening the ad ([FeedAdEvent.Open]).
 *
 * Screen readers hear "Sponsored message from …" first. Nothing here reports it viewed: the list does, once the whole
 * card is on screen.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FeedAdCard(
    ad: FeedAd,
    channelTitle: String,
    onEvent: (FeedEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnEvent by rememberUpdatedState(onEvent)
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        ) {
            AdHeader(ad, channelTitle, currentOnEvent)
            if (ad.text.text.isNotEmpty()) {
                val styles = rememberFormattedTextStyles()
                val text = remember(ad.text, styles) { ad.text.toAnnotatedString(styles, spoilersRevealed = true) }
                Text(text, style = MaterialTheme.typography.bodyLarge.userContent)
            }
            ad.media?.let { media ->
                Box(Modifier.clickable { currentOnEvent(FeedAdEvent.Open(ad.chatId, ad.id, isMediaClick = true)) }) {
                    FeedPostMedia(media)
                }
            }
            FilledTonalButton(
                onClick = { currentOnEvent(FeedAdEvent.Open(ad.chatId, ad.id)) },
                modifier = Modifier.fillMaxWidth(),
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(ad.buttonText, style = MaterialTheme.typography.labelLarge.userContent)
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Icon(
                    painterResource(R.drawable.ic_open_in_new),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
            }
        }
    }
}

/** The advertiser's photo and name, the label with the channel, and the info and report buttons. */
@Composable
private fun AdHeader(
    ad: FeedAd,
    channelTitle: String,
    onEvent: (FeedEvent) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val label =
        stringResource(if (ad.isRecommended) UiR.string.sponsored_label_recommended else UiR.string.sponsored_label)
    val spokenLabel = stringResource(R.string.feed_ad_from, ad.title.ifBlank { channelTitle })
    Row(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val size = MaterialTheme.sizes.avatarRow
        AsyncImage(
            model =
                ad.sponsorPhoto
                    ?.sizes
                    ?.minByOrNull { it.width }
                    ?.file
                    ?.id
                    ?.let(::TdlibFileImage),
            contentDescription = null,
            modifier = Modifier.size(size).clip(CircleShape).background(colors.surfaceContainerHighest),
            contentScale = ContentScale.Crop,
        )
        Column(Modifier.weight(1f)) {
            if (ad.title.isNotBlank()) {
                Text(ad.title, maxLines = 2, style = MaterialTheme.typography.titleSmallEmphasized.userContent)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    modifier = Modifier.semantics { contentDescription = spokenLabel },
                    style = MaterialTheme.typography.labelMediumEmphasized,
                )
                Text(
                    text = stringResource(R.string.feed_ad_in, channelTitle),
                    maxLines = 1,
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        AdButtons(ad, onEvent)
    }
}

/** ⓘ "About this ad", and × "Report ad", which opens the same sheet at the report. */
@Composable
private fun AdButtons(
    ad: FeedAd,
    onEvent: (FeedEvent) -> Unit,
) {
    val about =
        stringResource(if (ad.isRecommended) UiR.string.sponsored_about_recommended else UiR.string.sponsored_about)
    IconButton(
        onClick = { onEvent(FeedAdEvent.ShowInfo(ad.chatId, ad.id)) },
        shapes = IconButtonDefaults.shapes(),
    ) {
        Icon(painterResource(UiR.drawable.ic_info), contentDescription = about)
    }
    IconButton(
        onClick = {
            onEvent(FeedAdEvent.ShowInfo(ad.chatId, ad.id))
            onEvent(FeedAdEvent.Report)
        },
        shapes = IconButtonDefaults.shapes(),
    ) {
        Icon(
            painterResource(UiR.drawable.ic_close),
            contentDescription = stringResource(UiR.string.sponsored_report),
        )
    }
}

@PingogramPreviews
@Composable
private fun FeedAdCardPreview() {
    PingogramPreview { FeedAdCard(FeedPreviewData.ad, channelTitle = "Kotlin Weekly", onEvent = {}) }
}
