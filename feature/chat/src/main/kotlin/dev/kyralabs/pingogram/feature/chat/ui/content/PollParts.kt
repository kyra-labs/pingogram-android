package dev.kyralabs.pingogram.feature.chat.ui.content

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withLink
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.NewPoll
import dev.kyralabs.pingogram.core.domain.model.Poll
import dev.kyralabs.pingogram.core.domain.model.PollMedia
import dev.kyralabs.pingogram.core.domain.model.PollOption
import dev.kyralabs.pingogram.core.domain.model.richMediaId
import dev.kyralabs.pingogram.core.ui.component.StateCrossfade
import dev.kyralabs.pingogram.core.ui.text.rememberFormattedTextStyles
import dev.kyralabs.pingogram.core.ui.text.toAnnotatedString
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.BubbleColors
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.core.ui.transition.sharedMediaBounds
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.PollAdding
import dev.kyralabs.pingogram.core.ui.R as UiR

// The parts of polls Telegram added in 2026 (`docs/DESIGN_SYSTEM.md` §6 "Polls", boards 4a and 4b).

/**
 * The poll's photo or video (td_api.tl `messagePoll.media`), as wide as the poll, as tall as its aspect ratio makes it
 * within the media sizes, `medium` corners, dimmed in dark theme as all media in the conversation; a tap opens it in
 * the media viewer ([PollAction.OpenMedia]), which it grows into by [richMediaId] with index 0 of [messageId].
 */
@Composable
internal fun PollPhoto(
    media: PollMedia,
    messageId: Long,
    onAction: (PollAction) -> Unit,
) {
    val sizes = MaterialTheme.sizes
    val shape = MaterialTheme.shapes.medium
    val frame =
        Modifier
            .sharedMediaBounds(richMediaId(messageId, 0), shape)
            .clip(shape)
            .clickable(onClickLabel = stringResource(R.string.poll_open_photo)) { onAction(PollAction.OpenMedia) }
    when (media) {
        is PollMedia.Photo -> {
            val largest = largestPhotoSize(media.photo.sizes)
            val size =
                mediaDisplaySize(
                    largest?.width ?: 0,
                    largest?.height ?: 0,
                    sizes.mediaMaxWidth,
                    sizes.mediaMaxHeight,
                    sizes.mediaMinSize,
                )
            PhotoImage(
                photo = media.photo,
                displaySize = size,
                hidden = false,
                modifier = frame.fillMaxWidth().height(size.height),
            )
        }

        is PollMedia.Video -> {
            val still = media.video.still()
            val size =
                mediaDisplaySize(
                    still.width,
                    still.height,
                    sizes.mediaMaxWidth,
                    sizes.mediaMaxHeight,
                    sizes.mediaMinSize,
                )
            Box(frame.fillMaxWidth().height(size.height)) {
                VideoThumbnail(still, size, false, Modifier.matchParentSize())
                PlayBadge(Modifier.align(Alignment.Center))
            }
        }

        is PollMedia.Link, PollMedia.Other -> {
            Unit
        }
    }
}

/**
 * The kicker ("POLL · MULTIPLE ANSWERS", `labelMediumEmphasized` in the accent), the question (`titleMedium`) and the
 * description with its formatting (`bodyMedium`), all as user content but the kicker.
 */
@Composable
internal fun PollHeader(
    poll: Poll,
    description: FormattedText,
    colors: BubbleColors,
) {
    val resources = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall)) {
        Text(
            text = pollKickerShown(resources, poll, locale),
            color = colors.accent,
            style = MaterialTheme.typography.labelMediumEmphasized,
        )
        Text(
            text = poll.question.text,
            color = colors.content,
            style = MaterialTheme.typography.titleMedium.userContent,
        )
        if (description.text.isNotEmpty()) {
            val styles = rememberFormattedTextStyles(codeBackground = colors.container, spoilerColor = colors.spoiler)
            val text = remember(description, styles) { description.toAnnotatedString(styles, spoilersRevealed = true) }
            Text(text = text, color = colors.secondaryContent, style = MaterialTheme.typography.bodyMedium.userContent)
        }
    }
}

/**
 * An answer's text (`bodyMedium`, emphasized for a closed poll's winner), then its link in the accent, which opens it,
 * and in an answered quiz whether it was the user's and right ([quizTag]).
 */
@Composable
internal fun OptionText(
    option: PollOption,
    poll: Poll,
    colors: BubbleColors,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    val typography = MaterialTheme.typography
    Column(modifier) {
        Text(
            text = option.text.text,
            color = colors.content,
            style = (if (emphasized) typography.bodyMediumEmphasized else typography.bodyMedium).userContent,
        )
        (option.media as? PollMedia.Link)?.let { link ->
            val label = link.label()
            val text =
                remember(link, label, colors.accent) {
                    buildAnnotatedString {
                        withLink(LinkAnnotation.Url(link.url, TextLinkStyles(SpanStyle(color = colors.accent)))) {
                            append(label)
                        }
                    }
                }
            Text(text = text, style = typography.labelMedium.userContent)
        }
        quizTag(option, poll)?.let { tag ->
            Text(
                text = stringResource(tag),
                color = if (option.isCorrect) colors.correct else colors.wrong,
                style = typography.labelSmall,
            )
        }
    }
}

/**
 * When the poll closes ("Ends in 2 h", a pill on the bubble's block color with the timer symbol) and, once the user
 * voted in a poll that hides its results, "Results hidden until the end". Nothing for a poll with neither.
 */
@Composable
internal fun PollStatus(
    poll: Poll,
    closesIn: Long?,
    colors: BubbleColors,
) {
    val ends = pollEndsText(LocalResources.current, closesIn.takeUnless { poll.isClosed })
    if (ends == null && !poll.hidesResults) return
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall)) {
        ends?.let {
            Row(
                modifier =
                    Modifier
                        .background(colors.block, CircleShape)
                        .padding(horizontal = MaterialTheme.spacing.small, vertical = MaterialTheme.spacing.extraSmall),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(UiR.drawable.ic_timer),
                    contentDescription = null,
                    modifier = Modifier.size(MaterialTheme.sizes.inlineIconSmall),
                    tint = colors.blockContent,
                )
                Text(
                    text = it,
                    color = colors.blockContent,
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                )
            }
        }
        if (poll.hidesResults) {
            Text(
                text = stringResource(R.string.poll_results_hidden),
                color = colors.secondaryContent,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/**
 * "Add an option" where the poll takes answers from its voters ([PollAction.OpenAddOption]), then its inline field
 * ([adding]): close before it, the check that adds it after it, and a [LoadingIndicator] in the check's place while
 * Telegram takes it.
 */
@Composable
internal fun PollAddOption(
    adding: PollAdding?,
    colors: BubbleColors,
    onAction: (PollAction) -> Unit,
) {
    // The button and the field crossfade in place, the bubble's height on the scheme's spring.
    StateCrossfade(targetState = adding, contentKey = { it != null }, label = "pollAddOption") { shown ->
        AddOptionContent(shown, colors, onAction)
    }
}

@Composable
private fun AddOptionContent(
    adding: PollAdding?,
    colors: BubbleColors,
    onAction: (PollAction) -> Unit,
) {
    if (adding == null) {
        TextButton(
            onClick = { onAction(PollAction.OpenAddOption) },
            shapes = ButtonDefaults.shapes(),
            colors = ButtonDefaults.textButtonColors(contentColor = colors.accent),
        ) {
            Icon(
                painter = painterResource(UiR.drawable.ic_add),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Text(
                text = stringResource(R.string.poll_add_option),
                modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
            )
        }
    } else {
        NewOptionField(adding == PollAdding.Sending, colors, onAction)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class) // LoadingIndicator
@Composable
private fun NewOptionField(
    isSending: Boolean,
    colors: BubbleColors,
    onAction: (PollAction) -> Unit,
) {
    val text = rememberSaveable(saver = TextFieldState.Saver) { TextFieldState() }
    val submit = {
        val answer = text.text.toString().trim()
        if (answer.isNotEmpty() && !isSending) onAction(PollAction.AddOption(answer))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = { onAction(PollAction.DismissAddOption) },
            shapes = IconButtonDefaults.shapes(),
            colors = IconButtonDefaults.iconButtonColors(contentColor = colors.secondaryContent),
        ) {
            Icon(painterResource(UiR.drawable.ic_close), stringResource(R.string.poll_add_option_cancel))
        }
        OutlinedTextField(
            state = text,
            modifier = Modifier.weight(1f),
            enabled = !isSending,
            placeholder = { Text(stringResource(R.string.poll_new_option)) },
            inputTransformation = InputTransformation.maxLength(NewPoll.MAX_OPTION_LENGTH),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            onKeyboardAction = { submit() },
            lineLimits = TextFieldLineLimits.SingleLine,
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedTextColor = colors.content,
                    unfocusedTextColor = colors.content,
                    disabledTextColor = colors.secondaryContent,
                    cursorColor = colors.accent,
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.secondaryContent,
                    disabledBorderColor = colors.secondaryContent,
                    focusedPlaceholderColor = colors.secondaryContent,
                    unfocusedPlaceholderColor = colors.secondaryContent,
                ),
        )
        AddOptionConfirm(isSending, colors, submit)
    }
}

/** The check that adds the typed answer, and the indicator in its place while Telegram takes it, crossfading. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class) // LoadingIndicator
@Composable
private fun AddOptionConfirm(
    isSending: Boolean,
    colors: BubbleColors,
    submit: () -> Unit,
) {
    // The check and the indicator while Telegram takes the answer crossfade in place.
    StateCrossfade(
        targetState = isSending,
        contentAlignment = Alignment.Center,
        label = "pollAddSending",
    ) { sending ->
        if (sending) {
            Box(Modifier.minimumInteractiveComponentSize(), contentAlignment = Alignment.Center) {
                LoadingIndicator(
                    modifier = Modifier.size(MaterialTheme.sizes.inlineProgressIndicator),
                    color = colors.accent,
                )
            }
        } else {
            IconButton(
                onClick = submit,
                shapes = IconButtonDefaults.shapes(),
                colors = IconButtonDefaults.iconButtonColors(contentColor = colors.accent),
            ) {
                Icon(painterResource(UiR.drawable.ic_check), stringResource(R.string.poll_add_option_confirm))
            }
        }
    }
}
