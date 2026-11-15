package dev.kyralabs.pingogram.feature.chat.ui.transcription

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.BubbleColors
import dev.kyralabs.pingogram.core.ui.theme.chatStyle
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatEvent
import dev.kyralabs.pingogram.feature.chat.ui.TranscriptionEvent
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The Transcribe button beside a voice message's waveform, or on a round video's edge ([isVideo]): a tonal toggle
 * (`toggleableShapes()`, a rounded square that turns round once checked) in the bubble's block colors, its accent once
 * the text shows; the recognizer at work shows the loading indicator in it, and a tap then stops it. Screen readers
 * hear the bubble as one and use its custom actions instead.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TranscribeButton(
    transcription: MessageTranscription,
    colors: BubbleColors,
    modifier: Modifier = Modifier,
    isVideo: Boolean = false,
) {
    val transcript = transcription.transcript
    val working = transcript == BubbleTranscript.Transcribing
    val checked = transcript.isOpen || working
    val label =
        stringResource(
            when {
                working -> R.string.transcription_transcribing
                checked -> R.string.transcription_hide
                isVideo -> R.string.transcription_transcribe_video
                else -> R.string.transcription_transcribe
            },
        )
    FilledTonalIconToggleButton(
        checked = checked,
        onCheckedChange = { open ->
            val id = transcription.messageId
            transcription.onEvent(if (open) TranscriptionEvent.Transcribe(id) else TranscriptionEvent.Hide(id))
        },
        modifier = modifier,
        shapes = IconButtonDefaults.toggleableShapes(),
        colors =
            IconButtonDefaults.filledTonalIconToggleButtonColors(
                containerColor = colors.block,
                contentColor = colors.blockContent,
                checkedContainerColor = colors.accent,
                checkedContentColor = colors.onAccent,
            ),
    ) {
        if (working) {
            LoadingIndicator(
                modifier = Modifier.size(MaterialTheme.sizes.inlineIcon),
                color = LocalContentColor.current,
            )
        } else {
            Icon(painterResource(R.drawable.ic_speech_to_text), contentDescription = label)
        }
    }
}

/**
 * The transcript under a voice message's waveform, after a divider in the bubble's block color: tonal lines and
 * "Transcribing on this phone…" while the recognizer works; the text ([TranscriptText]); that no speech language is
 * ready, with a link to Android's settings; or that it failed, with "Try again" (an assertive live region).
 */
@Composable
internal fun TranscriptBlock(
    transcription: MessageTranscription,
    colors: BubbleColors,
    modifier: Modifier = Modifier,
) {
    val transcript = transcription.transcript ?: return
    Column(
        modifier = modifier.padding(top = MaterialTheme.spacing.extraSmall),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        HorizontalDivider(thickness = DividerDefaults.Thickness, color = colors.block)
        TranscriptContent(transcription.messageId, transcript, colors, transcription.onEvent)
    }
}

@Composable
private fun TranscriptContent(
    messageId: Long,
    transcript: BubbleTranscript,
    colors: BubbleColors,
    onEvent: (ChatEvent) -> Unit,
) {
    when (transcript) {
        BubbleTranscript.Transcribing -> Transcribing(colors)
        is BubbleTranscript.Shown -> TranscriptText(messageId, transcript, colors, onEvent)
        BubbleTranscript.LanguageUnavailable -> LanguageUnavailable(colors, onEvent)
        BubbleTranscript.Failed -> TranscriptionFailed(messageId, colors, onEvent)
    }
}

/** Three tonal lines in the bubble's block color, shorter each, then "Transcribing on this phone…"; no shimmer. */
@Composable
private fun Transcribing(colors: BubbleColors) {
    val lineShape = MaterialTheme.shapes.small
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        PLACEHOLDER_LINES.forEach { fraction ->
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(MaterialTheme.spacing.medium)
                    .background(colors.block, lineShape),
            )
        }
        Text(
            text = stringResource(R.string.transcription_transcribing),
            color = colors.secondaryContent,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/**
 * The transcript in the message text's style, as user content: cut after [COLLAPSED_LINES] lines ([LARGE_FONT_LINES]
 * from 200% font) with a fade into the bubble and "Show more" until shown whole; then where it was made ("On this
 * phone" on the first one, "Transcribed automatically") and "Hide text".
 */
@Composable
private fun TranscriptText(
    messageId: Long,
    transcript: BubbleTranscript.Shown,
    colors: BubbleColors,
    onEvent: (ChatEvent) -> Unit,
) {
    var overflows by remember(transcript.text) { mutableStateOf(false) }
    val lines = if (LocalDensity.current.fontScale >= LARGE_FONT_SCALE) LARGE_FONT_LINES else COLLAPSED_LINES
    val cut = overflows && !transcript.isExpanded
    val fade = colors.container
    val fadeHeight = MaterialTheme.spacing.extraLarge
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall)) {
        if (transcript.text.isEmpty()) {
            Text(
                text = stringResource(R.string.transcription_no_words),
                color = colors.secondaryContent,
                style = MaterialTheme.chatStyle.messageText,
            )
        } else {
            Text(
                text = transcript.text,
                modifier =
                    Modifier.drawWithCache {
                        val height = fadeHeight.toPx()
                        val brush =
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, fade),
                                startY = size.height - height,
                                endY = size.height,
                            )
                        onDrawWithContent {
                            drawContent()
                            if (cut) drawRect(brush, topLeft = Offset(0f, size.height - height))
                        }
                    },
                color = colors.content,
                maxLines = if (transcript.isExpanded) Int.MAX_VALUE else lines,
                overflow = TextOverflow.Clip,
                onTextLayout = { if (!transcript.isExpanded) overflows = it.hasVisualOverflow },
                style = MaterialTheme.chatStyle.messageText.userContent,
            )
        }
        TranscriptActions(messageId, transcript, showsMore = cut, colors, onEvent)
    }
}

@Composable
private fun TranscriptActions(
    messageId: Long,
    transcript: BubbleTranscript.Shown,
    showsMore: Boolean,
    colors: BubbleColors,
    onEvent: (ChatEvent) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tag =
            when {
                transcript.isAutomatic -> R.string.transcription_automatic
                transcript.showsNotice -> R.string.transcription_on_this_phone
                else -> null
            }
        Box(Modifier.weight(1f)) {
            if (tag != null) {
                Text(
                    text = stringResource(tag),
                    color = colors.secondaryContent,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        TextButton(
            onClick = { onEvent(TranscriptionEvent.Hide(messageId)) },
            shapes = ButtonDefaults.shapes(),
            colors = ButtonDefaults.textButtonColors(contentColor = colors.content),
        ) {
            Text(stringResource(R.string.transcription_hide))
        }
        if (showsMore) {
            FilledTonalButton(
                onClick = { onEvent(TranscriptionEvent.ShowMore(messageId)) },
                shapes = ButtonDefaults.shapes(),
                colors =
                    ButtonDefaults.filledTonalButtonColors(
                        containerColor = colors.block,
                        contentColor = colors.blockContent,
                    ),
            ) {
                Text(stringResource(R.string.transcription_show_more))
                Icon(
                    painter = painterResource(UiR.drawable.ic_keyboard_arrow_down),
                    contentDescription = null,
                    modifier = Modifier.padding(start = ButtonDefaults.IconSpacing).size(ButtonDefaults.IconSize),
                )
            }
        }
    }
}

/** No speech language for the message is ready: why, and a link to Android's speech language settings. */
@Composable
private fun LanguageUnavailable(
    colors: BubbleColors,
    onEvent: (ChatEvent) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        verticalAlignment = Alignment.Top,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_translate),
            contentDescription = null,
            tint = colors.content,
            modifier = Modifier.padding(top = MaterialTheme.spacing.extraSmall).size(MaterialTheme.sizes.inlineIcon),
        )
        Column {
            Text(
                text = stringResource(R.string.transcription_unavailable),
                color = colors.content,
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(
                onClick = { onEvent(TranscriptionEvent.OpenLanguageSettings) },
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.textButtonColors(contentColor = colors.accent),
            ) {
                Text(stringResource(R.string.transcription_language_settings))
            }
        }
    }
}

/** The transcript could not be made: why, as an assertive live region, and "Try again". */
@Composable
private fun TranscriptionFailed(
    messageId: Long,
    colors: BubbleColors,
    onEvent: (ChatEvent) -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.End,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(UiR.drawable.ic_error),
                contentDescription = null,
                tint = colors.content,
                modifier = Modifier.size(MaterialTheme.sizes.inlineIcon),
            )
            Text(
                text = stringResource(R.string.transcription_failed),
                color = colors.content,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        FilledTonalButton(
            onClick = { onEvent(TranscriptionEvent.Transcribe(messageId)) },
            shapes = ButtonDefaults.shapes(),
            colors =
                ButtonDefaults.filledTonalButtonColors(
                    containerColor = colors.block,
                    contentColor = colors.blockContent,
                ),
        ) {
            Icon(
                painter = painterResource(UiR.drawable.ic_refresh),
                contentDescription = null,
                modifier = Modifier.padding(end = ButtonDefaults.IconSpacing).size(ButtonDefaults.IconSize),
            )
            Text(stringResource(R.string.transcription_try_again))
        }
    }
}

/**
 * A round video message's transcript (`docs/DESIGN_SYSTEM.md` §6 "Voice message transcription", board 28c): in a
 * bubble of its own under the circle, in [colors], as wide as the circle allows up to a bubble's width.
 */
@Composable
internal fun VideoNoteTranscript(
    messageId: Long,
    transcript: BubbleTranscript,
    colors: BubbleColors,
    onEvent: (ChatEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .widthIn(max = MaterialTheme.sizes.bubbleMaxWidth)
                .background(colors.container, MaterialTheme.shapes.extraLarge)
                .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small),
    ) {
        TranscriptContent(messageId, transcript, colors, onEvent)
    }
}

/** How much of each placeholder line's width the recognizer's tonal lines take. */
private val PLACEHOLDER_LINES = listOf(0.92f, 0.78f, 0.54f)

/** Lines of a transcript shown before "Show more". */
private const val COLLAPSED_LINES = 4

/** Lines shown from [LARGE_FONT_SCALE] on, where each holds fewer words. */
private const val LARGE_FONT_LINES = 6

private const val LARGE_FONT_SCALE = 2f
