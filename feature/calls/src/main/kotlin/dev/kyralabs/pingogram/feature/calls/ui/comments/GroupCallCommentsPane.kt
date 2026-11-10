@file:Suppress("TooManyFunctions") // One composable per part of the comments pane.

package dev.kyralabs.pingogram.feature.calls.ui.comments

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.SenderHues
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.core.ui.transition.isReducedMotion
import dev.kyralabs.pingogram.feature.calls.R
import kotlinx.collections.immutable.ImmutableList
import java.text.NumberFormat
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The voice chat's comments (`docs/DESIGN_SYSTEM.md` §6, "Voice chat comments"): a pane on `surfaceContainerHigh`
 * with `extraLarge` top corners, docked at the bottom. "Comments · 214" heads it (with an administrator's menu to turn
 * them on or off), the comments follow, newest at the bottom, reactions float up at the bottom end, and "Add a comment"
 * with its emoji and send buttons ends it.
 *
 * [state] and [announcement] are read here only, so a new comment recomposes the pane and nothing around it.
 */
@Composable
internal fun GroupCallCommentsPane(
    state: State<GroupCallCommentsUiState>,
    announcement: State<CommentAnnouncement?>,
    onEvent: (CommentEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val comments = state.value as? GroupCallCommentsUiState.Available ?: return
    val spacing = MaterialTheme.spacing
    val title = stringResource(R.string.group_call_comments)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize),
        modifier = modifier.semantics { paneTitle = title },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                ).imePadding(),
        ) {
            PaneHeader(comments, announcement, onEvent)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                CommentList(comments, onEvent)
                FloatingReactions(comments.reactions, Modifier.align(Alignment.BottomEnd).padding(end = spacing.large))
            }
            CommentInput(inputMode(comments), onEvent)
        }
    }
}

/**
 * "Comments · 214" in `titleSmallEmphasized` `primary`, a heading. Screen readers hear new comments here, politely and
 * at most every few seconds ([announcement]), not each one as it comes.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PaneHeader(
    state: GroupCallCommentsUiState.Available,
    announcement: State<CommentAnnouncement?>,
    onEvent: (CommentEvent) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val count = NumberFormat.getIntegerInstance().format(state.received)
    val title = stringResource(R.string.group_call_comments_count, count)
    val heard = announcement.value?.let { announcementText(it) }
    Row(
        Modifier.fillMaxWidth().padding(start = spacing.large, end = spacing.extraSmall, top = spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmallEmphasized,
            color = MaterialTheme.colorScheme.primary,
            modifier =
                Modifier.weight(1f).clearAndSetSemantics {
                    heading()
                    contentDescription = listOfNotNull(title, heard).joinToString(". ")
                    liveRegion = LiveRegionMode.Polite
                },
        )
        if (state.canToggle) ModerationMenu(state.areAllowed, onEvent)
    }
}

@Composable
private fun announcementText(announcement: CommentAnnouncement): String =
    pluralStringResource(
        R.plurals.group_call_comments_new,
        announcement.count,
        announcement.count,
        announcement.name.ifBlank { stringResource(R.string.call_someone) },
        announcement.text,
    )

/** An administrator's "More options": comments on or off for everyone. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ModerationMenu(
    areAllowed: Boolean,
    onEvent: (CommentEvent) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val label = if (areAllowed) R.string.group_call_comments_turn_off else R.string.group_call_comments_turn_on
    Box {
        IconButton(onClick = { open = true }, shapes = IconButtonDefaults.shapes()) {
            Icon(
                painterResource(UiR.drawable.ic_more_vert),
                contentDescription = stringResource(R.string.group_call_comments_options),
            )
        }
        DropdownMenuPopup(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 0, count = 1)) {
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    onClick = {
                        open = false
                        onEvent(CommentEvent.SetAllowed(!areAllowed))
                    },
                    shape = MenuDefaults.itemShape(0, 1).shape,
                )
            }
        }
    }
}

/** The comments, newest at the bottom: a keyed list laid out from the bottom, so the newest stays in view. */
@Composable
private fun CommentList(
    state: GroupCallCommentsUiState.Available,
    onEvent: (CommentEvent) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    if (state.comments.isEmpty()) {
        Text(
            text =
                stringResource(
                    if (state.areAllowed) R.string.group_call_comments_empty else R.string.group_call_comments_off,
                ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = spacing.large, vertical = spacing.small),
        )
        return
    }
    val reversed = remember(state.comments) { state.comments.asReversed() }
    val listState = rememberLazyListState()
    val newest = reversed.firstOrNull()?.id
    val follow = remember { NewestFollower() }
    // A lazy list keeps its first item in place by its key, which would leave new comments below the bottom. While the
    // user reads the newest (the start of the reversed list, as last laid out), the list follows the new ones: decided
    // after composition and before the list measures them, without reading its position in composition.
    SideEffect {
        if (newest != follow.newest) {
            if (listState.firstVisibleItemIndex == 0) listState.requestScrollToItem(0)
            follow.newest = newest
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        reverseLayout = true,
        contentPadding = PaddingValues(horizontal = spacing.medium, vertical = spacing.small),
        verticalArrangement = Arrangement.spacedBy(spacing.extraSmall),
    ) {
        items(reversed, key = { it.id }, contentType = { if (it.paidStarCount > 0) "paid" else "comment" }) { comment ->
            CommentRow(comment, state.canModerate, onEvent, Modifier.animateItem())
        }
    }
}

/**
 * A comment: the sender's avatar, their name in their hue's ink (`labelLargeEmphasized`) and the text (`bodyMedium`,
 * user content). A paid one sits on `tertiaryContainer` with its "★ 50" pill; one Telegram refused says "Not sent" in
 * `error`. A long press opens what the user may do with it (delete, report, every comment of its sender), which screen
 * readers get as custom actions.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CommentRow(
    comment: CommentUi,
    canModerate: Boolean,
    onEvent: (CommentEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val spacing = MaterialTheme.spacing
    val haptics = LocalHapticFeedback.current
    val hues = MaterialTheme.pingogramColors.senderHues
    val ink = hues[SenderHues.indexOf(-1, comment.senderId)].ink
    val name = comment.name.ifBlank { stringResource(R.string.call_someone) }
    val actions = commentActions(comment, canModerate, name, onEvent)
    var menu by remember { mutableStateOf(false) }
    val paid = comment.paidStarCount > 0
    val stars = stringResource(R.string.group_call_comment_stars, comment.paidStarCount)
    val failed = stringResource(R.string.group_call_comment_not_sent)
    Box(modifier) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (paid) {
                            Modifier.background(
                                colors.tertiaryContainer,
                                MaterialTheme.shapes.large,
                            )
                        } else {
                            Modifier
                        },
                    ).combinedClickable(
                        enabled = actions.isNotEmpty(),
                        onClick = {},
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            menu = true
                        },
                    ).padding(spacing.small)
                    .clearAndSetSemantics {
                        contentDescription =
                            listOfNotNull(name, comment.text, stars.takeIf { paid }, failed.takeIf { comment.isFailed })
                                .joinToString(", ")
                        customActions =
                            actions.map {
                                CustomAccessibilityAction(it.label) {
                                    it.run()
                                    true
                                }
                            }
                    },
            horizontalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            Avatar(
                photo = comment.photo,
                name = comment.name,
                id = comment.senderId,
                size = MaterialTheme.sizes.avatarExtraSmall,
            )
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        name,
                        style = MaterialTheme.typography.labelLargeEmphasized.userContent,
                        color = if (paid) colors.onTertiaryContainer else ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (paid) StarPill(stars, Modifier.padding(start = spacing.small))
                }
                Text(
                    comment.text,
                    style = MaterialTheme.typography.bodyMedium.userContent,
                    color = if (paid) colors.onTertiaryContainer else colors.onSurface,
                )
                if (comment.isFailed) {
                    Text(failed, style = MaterialTheme.typography.labelSmall, color = colors.error)
                }
            }
        }
        CommentMenu(expanded = menu, actions = actions, onDismiss = { menu = false })
    }
}

/** The newest comment [CommentList] last followed. */
private class NewestFollower {
    var newest: Int? = null
}

/** A paid comment's price: "★ 50" on `tertiary`. */
@Composable
private fun StarPill(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmallEmphasized,
        color = MaterialTheme.colorScheme.onTertiary,
        modifier =
            modifier
                .background(MaterialTheme.colorScheme.tertiary, MaterialTheme.shapes.extraLarge)
                .padding(horizontal = MaterialTheme.spacing.small),
    )
}

/** What may be done with a comment, for its menu and its custom actions. */
private class CommentAction(
    val label: String,
    val isDestructive: Boolean,
    val run: () -> Unit,
)

@Composable
private fun commentActions(
    comment: CommentUi,
    canModerate: Boolean,
    name: String,
    onEvent: (CommentEvent) -> Unit,
): List<CommentAction> {
    if (!comment.canDelete) return emptyList()
    val delete = stringResource(R.string.group_call_comment_delete)
    val report = stringResource(R.string.group_call_comment_report)
    val deleteAll = stringResource(R.string.group_call_comment_delete_all, name)
    return remember(comment.id, comment.isOwn, canModerate, delete, report, deleteAll) {
        buildList {
            add(CommentAction(delete, isDestructive = true) { onEvent(CommentEvent.Delete(comment.id, false)) })
            if (!comment.isOwn && canModerate) {
                add(CommentAction(report, isDestructive = true) { onEvent(CommentEvent.Delete(comment.id, true)) })
                add(
                    CommentAction(deleteAll, isDestructive = true) {
                        onEvent(CommentEvent.DeleteAllFrom(comment.senderKey))
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CommentMenu(
    expanded: Boolean,
    actions: List<CommentAction>,
    onDismiss: () -> Unit,
) {
    DropdownMenuPopup(expanded = expanded && actions.isNotEmpty(), onDismissRequest = onDismiss) {
        DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 0, count = 1)) {
            actions.forEachIndexed { index, action ->
                DropdownMenuItem(
                    text = { Text(action.label, color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        onDismiss()
                        action.run()
                    },
                    shape = MenuDefaults.itemShape(index, actions.size).shape,
                )
            }
        }
    }
}

/**
 * The newest reactions, each rising from the bottom end and fading as it goes, in its graphics layer: the draw phase
 * only (`docs/PERFORMANCE.md` §4). Decorative; none with reduced motion, where the comment itself says it.
 */
@Composable
private fun FloatingReactions(
    reactions: ImmutableList<FloatingReaction>,
    modifier: Modifier = Modifier,
) {
    if (isReducedMotion()) return
    Box(modifier.clearAndSetSemantics {}) {
        reactions.forEach { reaction -> FloatingEmoji(reaction, MaterialTheme.sizes.reactionTile) }
    }
}

@Composable
private fun BoxScope.FloatingEmoji(
    reaction: FloatingReaction,
    rise: Dp,
) {
    val progress = remember(reaction.id) { Animatable(0f) }
    val motion = MaterialTheme.motionScheme
    LaunchedEffect(reaction.id) { progress.animateTo(1f, motion.slowSpatialSpec()) }
    Text(
        reaction.emoji,
        style = MaterialTheme.typography.headlineMedium,
        modifier =
            Modifier.align(Alignment.BottomCenter).graphicsLayer {
                val value = progress.value
                translationY = -rise.toPx() * RISE_SCREENS * value
                alpha = (1f - value).coerceIn(0f, 1f)
            },
    )
}

/**
 * "Add a comment": the rounded tonal field with the quick reactions' button at its end, and the send button beside
 * it (`primary`, a rounded square, as the composer's). Where the user may not write, a line says why instead: comments
 * are off, the call is too large, or comments cost Telegram Stars, which Pingogram can't pay.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CommentInput(
    mode: InputMode,
    onEvent: (CommentEvent) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = mode,
        transitionSpec = {
            fadeIn(motion.defaultEffectsSpec())
                .togetherWith(fadeOut(motion.fastEffectsSpec()))
                .using(SizeTransform(clip = false, sizeAnimationSpec = { _, _ -> motion.defaultSpatialSpec() }))
        },
        label = "comment input",
        modifier = Modifier.fillMaxWidth(),
    ) { shown ->
        if (shown == InputMode.Field) {
            CommentField(onEvent, Modifier.padding(horizontal = spacing.medium, vertical = spacing.small))
        } else {
            Text(
                text =
                    stringResource(
                        when (shown) {
                            InputMode.Off -> R.string.group_call_comments_off
                            InputMode.Paid -> R.string.group_call_comments_paid
                            else -> R.string.group_call_comments_too_large
                        },
                    ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = spacing.large, vertical = spacing.medium),
            )
        }
    }
}

private enum class InputMode { Field, Off, Paid, TooLarge }

private fun inputMode(state: GroupCallCommentsUiState.Available): InputMode =
    when {
        !state.areAllowed -> InputMode.Off
        state.isPaid -> InputMode.Paid
        !state.canComment -> InputMode.TooLarge
        else -> InputMode.Field
    }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CommentField(
    onEvent: (CommentEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.colorScheme
    val text = rememberTextFieldState()
    val haptics = LocalHapticFeedback.current
    val send = {
        val typed = text.text.toString()
        if (typed.isNotBlank()) {
            onEvent(CommentEvent.Send(typed))
            text.clearText()
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        }
    }
    Row(
        modifier = modifier.fillMaxWidth().widthIn(max = MaterialTheme.sizes.contentMaxWidth),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        TextField(
            state = text,
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.group_call_comment_hint)) },
            textStyle = MaterialTheme.typography.bodyLarge.userContent,
            lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = MAX_FIELD_LINES),
            shape = MaterialTheme.shapes.extraLarge,
            colors =
                TextFieldDefaults.colors(
                    focusedContainerColor = colors.surfaceContainerHighest,
                    unfocusedContainerColor = colors.surfaceContainerHighest,
                    focusedIndicatorColor = colors.surfaceContainerHighest.copy(alpha = 0f),
                    unfocusedIndicatorColor = colors.surfaceContainerHighest.copy(alpha = 0f),
                ),
            trailingIcon = { ReactionButton(onEvent) },
        )
        FilledIconButton(
            onClick = send,
            enabled = text.text.isNotBlank(),
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.size(IconButtonDefaults.mediumContainerSize()),
        ) {
            Icon(
                painterResource(UiR.drawable.ic_send_filled),
                contentDescription = stringResource(R.string.group_call_comment_send),
            )
        }
    }
}

/** The emoji button: a menu of the quick reactions, each sent at once as a comment of its own, which floats up. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ReactionButton(onEvent: (CommentEvent) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    Box {
        IconButton(onClick = { open = true }, shapes = IconButtonDefaults.shapes()) {
            Icon(
                painterResource(UiR.drawable.ic_mood),
                contentDescription = stringResource(R.string.group_call_comment_react),
            )
        }
        DropdownMenuPopup(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 0, count = 1)) {
                Row {
                    GroupCallCommentsViewModel.QUICK_REACTIONS.forEach { emoji ->
                        val label = stringResource(R.string.group_call_comment_send_reaction, emoji)
                        IconButton(
                            onClick = {
                                open = false
                                haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                                onEvent(CommentEvent.React(emoji))
                            },
                            shapes = IconButtonDefaults.shapes(),
                            modifier = Modifier.semantics { contentDescription = label },
                        ) {
                            Text(
                                emoji,
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.clearAndSetSemantics {},
                            )
                        }
                    }
                }
            }
        }
    }
}

/** How high a reaction rises: this many times the reaction tile's size. */
private const val RISE_SCREENS = 3f

private const val MAX_FIELD_LINES = 4
