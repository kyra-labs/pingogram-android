package dev.kyralabs.pingogram.feature.media.story

import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.RoundedPolygon
import dev.kyralabs.pingogram.core.domain.model.ReactionType
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.component.EmptyState
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.media.R
import dev.kyralabs.pingogram.feature.media.ui.BlurredMinithumbnail
import dev.kyralabs.pingogram.feature.media.ui.ExpandableCaption
import dev.kyralabs.pingogram.feature.media.ui.ViewerError
import dev.kyralabs.pingogram.feature.media.ui.ViewerImage
import java.util.Locale
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * Below the frame, as tall as the composer's pill whether or not it shows anything, so pages line up as they slide:
 * the reply field ("Reply privately…", the rounded tonal field in `extraLarge` corners as the composer's, user
 * content), which pauses the story while it has the keyboard, with its send button once it holds text; and the
 * reaction button with the row of reactions above it ([StoryReactionsRow]).
 */
@Composable
internal fun StoryBottomBar(
    reply: StoryReplyUi?,
    reactions: StoryReactionsUi?,
    replyText: TextFieldState,
    onEvent: (StoryViewerEvent) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    Column(Modifier.fillMaxWidth()) {
        val motionScheme = MaterialTheme.motionScheme
        AnimatedVisibility(
            visible = reactions?.isOpen == true,
            // Over the frame's bottom, taking no room: the frame keeps its size while the reactions show.
            modifier = Modifier.overlayAbove(),
            enter = expandVertically(motionScheme.defaultSpatialSpec()) + fadeIn(motionScheme.defaultEffectsSpec()),
            exit = shrinkVertically(motionScheme.fastSpatialSpec()) + fadeOut(motionScheme.fastEffectsSpec()),
        ) {
            if (reactions != null) {
                StoryReactionsRow(reactions, onReact = { onEvent(StoryViewerEvent.React(it)) })
            }
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = barMinHeight())
                    .padding(horizontal = spacing.small, vertical = spacing.small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            if (reply != null) {
                StoryReplyField(replyText, reply, onEvent, Modifier.weight(1f))
                if (replyText.text.isNotBlank()) {
                    FilledIconButton(
                        onClick = { onEvent(StoryViewerEvent.SendReply) },
                        enabled = !reply.isSending,
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(
                            painter = painterResource(UiR.drawable.ic_send_filled),
                            contentDescription = stringResource(R.string.story_send),
                        )
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
            if (reactions != null) {
                StoryReactionButton(reactions, onClick = { onEvent(StoryViewerEvent.ReactionsOpen(!reactions.isOpen)) })
            }
        }
    }
}

@Composable
private fun StoryReplyField(
    replyText: TextFieldState,
    reply: StoryReplyUi,
    onEvent: (StoryViewerEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnEvent by rememberUpdatedState(onEvent)
    TextField(
        state = replyText,
        modifier = modifier.onFocusChanged { currentOnEvent(StoryViewerEvent.ReplyFocused(it.isFocused)) },
        enabled = !reply.isSending,
        textStyle = MaterialTheme.typography.bodyLarge.userContent,
        placeholder = {
            // Pingogram's own words, in the layout's direction, unlike what is typed.
            Text(
                stringResource(R.string.story_reply_placeholder),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        onKeyboardAction = { currentOnEvent(StoryViewerEvent.SendReply) },
        lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = REPLY_MAX_LINES),
        shape = MaterialTheme.shapes.extraLarge,
        colors = TextFieldDefaults.tonalColors(),
    )
}

/**
 * The reaction button: the chosen reaction's emoji, or a heart outline before one is chosen; a tap opens and closes
 * the row of reactions.
 */
@Composable
private fun StoryReactionButton(
    reactions: StoryReactionsUi,
    onClick: () -> Unit,
) {
    val chosen = (reactions.chosen as? ReactionType.Emoji)?.emoji
    val label =
        if (chosen != null) {
            stringResource(R.string.story_reaction_chosen, chosen)
        } else {
            stringResource(R.string.story_react)
        }
    IconButton(
        onClick = onClick,
        shapes = IconButtonDefaults.shapes(),
        modifier = Modifier.semantics { contentDescription = label },
    ) {
        if (chosen != null) {
            Text(chosen, style = MaterialTheme.typography.titleLarge, modifier = Modifier.clearAndSetSemantics {})
        } else {
            Icon(painter = painterResource(UiR.drawable.ic_favorite), contentDescription = null)
        }
    }
}

/**
 * The reactions the user may put on the story as a pill of `IconToggleButton`s (`toggleableShapes()`, checked in
 * `secondaryContainer`) on `surfaceContainerHigh`, scrolling sideways where they do not fit; a tap sets one or takes
 * the chosen one back, with a toggle haptic.
 */
@Composable
private fun StoryReactionsRow(
    reactions: StoryReactionsUi,
    onReact: (ReactionType.Emoji) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val colors =
        IconButtonDefaults.iconToggleButtonColors(
            checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacing.small),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(
                modifier =
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = MaterialTheme.spacing.extraSmall)
                        .selectableGroup(),
            ) {
                reactions.available.forEach { reaction ->
                    val checked = reactions.chosen == reaction
                    IconToggleButton(
                        checked = checked,
                        onCheckedChange = {
                            haptics.performHapticFeedback(
                                if (checked) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn,
                            )
                            onReact(reaction)
                        },
                        enabled = !reactions.isSending,
                        shapes = IconButtonDefaults.toggleableShapes(),
                        colors = colors,
                    ) {
                        Text(reaction.emoji, style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
    }
}

/** Lays the content out above the top of its place, over what is there, taking no height of its own. */
private fun Modifier.overlayAbove(): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(minHeight = 0))
        layout(placeable.width, 0) { placeable.place(0, -placeable.height) }
    }

/** How tall the bar below the frame is at least: the composer's pill, with room above and below it. */
@Composable
internal fun barMinHeight(): Dp = MaterialTheme.sizes.composerField + MaterialTheme.spacing.small * 2

private const val REPLY_MAX_LINES = 4
