package dev.kyralabs.pingogram.feature.chat.ui.bot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpSize
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.content.PhotoImage
import dev.kyralabs.pingogram.feature.chat.ui.content.VideoThumbnail
import dev.kyralabs.pingogram.feature.chat.ui.content.largestPhotoSize
import dev.kyralabs.pingogram.feature.chat.ui.content.still
import dev.kyralabs.pingogram.feature.chat.ui.preview.BotPreviewData

/**
 * What a bot shows in its empty chat (`REQUIREMENTS.md` FR-C5, `docs/DESIGN_SYSTEM.md` §6 "Bot chat"), centred like
 * the secret chat's card: a service-style card on `surfaceContainerHighest` with `extraLarge` corners, at most
 * `sizes.bubbleMaxWidth` wide. On top the bot's photo, or its animation's still (it plays nowhere inline yet, as GIFs
 * do not), edge to edge in the card's top corners at its aspect ratio up to `sizes.mediaMaxHeight` tall, dimmed in dark
 * theme as the conversation's media; then "What can this bot do?" in `titleSmallEmphasized` and the bot's description
 * in `bodyMedium` `onSurfaceVariant`, which takes its own direction (`userContent`). Screen readers hear it as one.
 */
@Composable
internal fun BotIntroCard(
    intro: BotIntro,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.extraLarge
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = MaterialTheme.sizes.bubbleMaxWidth)
                    .background(scheme.surfaceContainerHighest, shape)
                    .semantics(mergeDescendants = true) {},
        ) {
            IntroMedia(intro)
            Column(
                modifier = Modifier.padding(horizontal = spacing.large, vertical = spacing.medium),
                verticalArrangement = Arrangement.spacedBy(spacing.small),
            ) {
                Text(
                    text = stringResource(R.string.bot_intro_title),
                    color = scheme.onSurface,
                    style = MaterialTheme.typography.titleSmallEmphasized,
                )
                if (intro.description.isNotBlank()) {
                    Text(
                        text = intro.description,
                        color = scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium.userContent,
                    )
                }
            }
        }
    }
}

/**
 * A bot's empty chat: what it does ([BotIntroCard]), centred in the sheet, scrolling where the window is too short for
 * it.
 */
@Composable
internal fun BotIntroArea(
    intro: BotIntro,
    modifier: Modifier,
) {
    BoxWithConstraints(modifier) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(MaterialTheme.spacing.medium),
            contentAlignment = Alignment.Center,
        ) {
            BotIntroCard(intro)
        }
    }
}

/** The bot's photo or its animation's still, as wide as the card, in its top corners; nothing without either. */
@Composable
private fun IntroMedia(intro: BotIntro) {
    val photo = intro.photo
    val animation = intro.animation
    if (photo == null && animation == null) return
    val top = MaterialTheme.shapes.extraLarge.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize)
    val maxHeight = MaterialTheme.sizes.mediaMaxHeight
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val largest = photo?.let { largestPhotoSize(it.sizes) }
        val width = largest?.width ?: animation?.width ?: 0
        val height = largest?.height ?: animation?.height ?: 0
        val displayHeight = if (width > 0 && height > 0) maxWidth * height / width else maxWidth
        val size = DpSize(maxWidth, displayHeight.coerceAtMost(maxHeight))
        val mediaModifier = Modifier.size(size).clip(top)
        if (photo != null) {
            PhotoImage(photo = photo, displaySize = size, hidden = false, modifier = mediaModifier)
        } else if (animation != null) {
            VideoThumbnail(still = animation.still(), displaySize = size, hidden = false, modifier = mediaModifier)
        }
    }
}

@PingogramPreviews
@Composable
private fun BotIntroPreview() {
    PingogramPreview { BotIntroCard(BotPreviewData.intro, Modifier.padding(MaterialTheme.spacing.medium)) }
}

@PingogramPreviews
@Composable
private fun BotIntroTextOnlyPreview() {
    PingogramPreview { BotIntroCard(BotPreviewData.textIntro, Modifier.padding(MaterialTheme.spacing.medium)) }
}
