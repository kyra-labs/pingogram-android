package dev.kyralabs.pingogram.feature.media.story

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.ui.component.LocalVoiceChats
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.media.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * A live story (boards 12 and 26): the live symbol on a `MaterialShapes.Cookie9Sided` badge in `errorContainer`,
 * "Live now" in `displaySmallEmphasized`, the call's title (user content) and how many watch, what watching does, then
 * a default `Button` "Watch live" that opens the voice chat screen on the poster's call ([LocalVoiceChats]) beside a
 * tonal "Skip live story" ([onSkip], the next story). The texts are one heading for screen readers ("Rahul is live:
 * Sunset from the ferry, 128 watching"). Without [LocalVoiceChats] (previews), no "Watch live". The buttons take their
 * own taps, so the story's tap zones below them do not.
 */
@Composable
internal fun LiveStoryNotice(
    media: StoryMedia.Live,
    posterName: String,
    onSkip: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.windowMarginCompact, vertical = spacing.extraLarge),
        verticalArrangement = Arrangement.spacedBy(spacing.small, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LiveBadge()
        LiveHeading(media, posterName)
        Text(
            text = stringResource(R.string.story_live_body),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
        )
        LiveActions(media, onSkip, Modifier.padding(top = spacing.medium))
    }
}

/** The live symbol on a `MaterialShapes.Cookie9Sided` badge in `errorContainer`. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LiveBadge() {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(MaterialTheme.sizes.headlineBadge)
            .background(colors.errorContainer, MaterialShapes.Cookie9Sided.toShape()),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(UiR.drawable.ic_live_tv), contentDescription = null, tint = colors.onErrorContainer)
    }
}

/** "Live now", the call's title and how many watch: one heading for screen readers, worded as a sentence. */
@Composable
private fun LiveHeading(
    media: StoryMedia.Live,
    posterName: String,
) {
    val colors = MaterialTheme.colorScheme
    val resources = LocalResources.current
    val watching = media.watching.takeIf { it > 0 }
    val spoken =
        listOfNotNull(
            if (media.title.isBlank()) {
                resources.getString(R.string.story_live_is_live, posterName)
            } else {
                resources.getString(R.string.story_live_is_live_title, posterName, media.title)
            },
            watching?.let { resources.getQuantityString(R.plurals.story_live_watching, it, it) },
        ).joinToString(", ")
    Column(
        modifier =
            Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                heading()
                contentDescription = spoken
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.story_live_title),
            color = colors.onSurface,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.displaySmallEmphasized,
        )
        if (media.title.isNotBlank()) {
            Text(
                text = media.title,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium.userContent,
            )
        }
        if (watching != null) {
            Text(
                text = pluralStringResource(R.plurals.story_live_watching, watching, watching),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            )
        }
    }
}

/** "Watch live" where the app provides voice chats ([LocalVoiceChats]), and "Skip live story" ([onSkip]). */
@Composable
private fun LiveActions(
    media: StoryMedia.Live,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val voiceChats = LocalVoiceChats.current
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        voiceChats?.let { chats ->
            Button(
                onClick = { chats.watchLiveStory(media.chatId, media.groupCallId) },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.story_live_watch), style = MaterialTheme.typography.labelLargeEmphasized)
            }
        }
        FilledTonalIconButton(onClick = onSkip, shapes = IconButtonDefaults.shapes()) {
            Icon(
                painter = painterResource(R.drawable.ic_skip_next_filled),
                contentDescription = stringResource(R.string.story_live_skip),
            )
        }
    }
}
