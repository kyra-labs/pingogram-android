package dev.kyralabs.pingogram.feature.chats.ui

import android.os.Build
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxDefaults
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.IntSize
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chats.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * A chat in the list with its swipe actions (`REQUIREMENTS.md` FR-L4, FR-L8; `docs/DESIGN_SYSTEM.md` §5–6, §9): a
 * [SwipeToDismissBox] around the [ChatListItem].
 *
 * - **Swipes**: what Appearance → Chat list → Swipe actions picked ([LocalChatSwipes], [rowActionFor]), by default
 *   start to end marks the chat as read, or as unread with nothing unread ([startSwipeAction]), and end to start
 *   archives it, or unarchives it in the archive ([endSwipeAction]); a side set to nothing, or whose action the chat
 *   does not have (muting Saved Messages), does not swipe; while swiping changes the folder
 *   ([ChatSwipes.changesFolder]), no row swipes, as the whole list moves. Both mirror in right-to-left layouts. The
 *   row never stays dismissed: past the threshold the action runs, with a `Confirm` haptic on Android 14 and later,
 *   and the row springs back. An archived chat then leaves the list through TDLib's update and `animateItem`; in a
 *   folder that keeps archived chats it stays, its swipe turned to unarchive. One swipe runs one action
 *   ([rememberOnSwiped]), and a row that comes back to the list is at rest: its swipe state is not saved.
 * - **Behind the row**: the action's icon and label on its tonal container ([swipeColors]), drawn only while the row
 *   is away from its place. The row itself takes its `large` container meanwhile, as when pressed, so it never slides
 *   with a square edge past the action's rounded one. The icon grows to full size on the motion scheme's fast spatial
 *   spring as the swipe crosses the threshold, and back when it returns, with a `TextHandleMove` haptic each way
 *   ([swipeThresholdFeedback]). Everything moving is read in the draw phase, so a swipe never recomposes.
 * - **Selection**: in selection mode ([selectionMode]) swipes are off and a tap selects; a long press starts selection
 *   (the list item's long click gives the `LongPress` haptic). Screen readers hear the row as a check box then.
 * - **Open** ([open]): the chat's conversation shows beside the list; the list item looks and reads as selected.
 * - **Screen readers** get every action of the row as a custom action outside selection mode
 *   ([ChatListItemModel.actions]), so nothing depends on swiping.
 */
@Composable
internal fun ChatRow(
    item: ChatListItemModel,
    selected: Boolean,
    selectionMode: Boolean,
    open: Boolean,
    onEvent: (ChatListEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val swipes = LocalChatSwipes.current
    val start = item.rowActionFor(swipes.start)
    val end = item.rowActionFor(swipes.end)
    val threshold = SwipeToDismissBoxDefaults.positionalThreshold
    // Not rememberSwipeToDismissBoxState, which saves the state: a row that left the list while springing back (an
    // archived chat) would come back with Undo swiped away, and run its swipe again at once.
    val state = remember { SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled, threshold) }
    val iconScale = remember { Animatable(SWIPE_ICON_RESTING_SCALE) }
    // Changes only when a swipe starts or ends, not in every frame of it.
    val dragged by remember(state) { derivedStateOf { state.dismissDirection != SwipeToDismissBoxValue.Settled } }
    val resources = LocalResources.current
    val iconSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            SwipeBackground(state, start?.reveal, end?.reveal, iconScale, SwipeFeedback(threshold, iconSpec))
        },
        modifier = modifier,
        enableDismissFromStartToEnd = start != null,
        enableDismissFromEndToStart = end != null,
        gesturesEnabled = !selectionMode && !swipes.changesFolder && (start != null || end != null),
        onDismiss = rememberOnSwiped(state, item.id, start, end, onEvent),
    ) {
        ChatListItem(
            item = item,
            onClick = { onEvent(ChatListEvent.ChatClicked(item.id)) },
            modifier =
                Modifier.semantics {
                    if (selectionMode) {
                        role = Role.Checkbox
                        toggleableState = ToggleableState(selected)
                    } else {
                        customActions =
                            item.actions.map { action ->
                                CustomAccessibilityAction(resources.getString(action.label)) {
                                    onEvent(ChatListEvent.RowAction(item.id, action))
                                    true
                                }
                            }
                    }
                },
            onLongClick = { onEvent(ChatListEvent.ChatLongClicked(item.id)) },
            onLongClickLabel = if (selectionMode) null else stringResource(R.string.chat_list_select),
            selected = selected,
            open = open,
            dragged = dragged,
        )
    }
}

/**
 * What [SwipeToDismissBox] calls once the row settled swiped away towards a direction: runs that side's action, [start]
 * or [end] as the row shows it then, with a `Confirm` haptic on Android 14 and later (`docs/DESIGN_SYSTEM.md` §5), and
 * springs the row back.
 *
 * One instance for the row's whole life, so one swipe runs one action. The box runs its `onDismiss` again whenever it
 * gets another instance while the row is still away (material3 1.5.0-beta01 keys its effect on it), and the action
 * changes the row while it springs back: a chat archived in a folder stays, its swipe turned to unarchive; a read
 * chat's swipe turns to unread. A new instance would run the other action then, which turns the row back, and so on,
 * restarting the spring each time, so the row stuck out while TDLib got a request per frame.
 */
@Composable
private fun rememberOnSwiped(
    state: SwipeToDismissBoxState,
    chatId: Long,
    start: ChatRowAction?,
    end: ChatRowAction?,
    onEvent: (ChatListEvent) -> Unit,
): (SwipeToDismissBoxValue) -> Unit {
    val scope = rememberCoroutineScope()
    val haptics by rememberUpdatedState(LocalHapticFeedback.current)
    val currentStart by rememberUpdatedState(start)
    val currentEnd by rememberUpdatedState(end)
    val currentOnEvent by rememberUpdatedState(onEvent)
    return remember(state, chatId) {
        { direction ->
            val action =
                when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> currentStart

                    SwipeToDismissBoxValue.EndToStart -> currentEnd

                    // Away from its place without a direction: only a restored state could be, and it is not saved.
                    SwipeToDismissBoxValue.Settled -> null
                }
            if (action != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                }
                currentOnEvent(ChatListEvent.RowAction(chatId, action))
            }
            scope.launch { state.reset() }
        }
    }
}

/**
 * What a swipe toward one side reveals: [icon] and [label] on the container of [tone]. Equal reveals are equal, so a
 * row that composes again with the same actions skips its background.
 */
@Immutable
internal data class SwipeReveal(
    @param:DrawableRes val icon: Int,
    @param:StringRes val label: Int,
    val tone: SwipeTone,
)

/**
 * The tonal pair behind a swipe: `secondaryContainer` (read, unread, pin, mute, hide), `tertiaryContainer` (archive,
 * unarchive) or `errorContainer` (delete).
 */
internal enum class SwipeTone { Secondary, Tertiary, Error }

/** What swiping this action reveals: its icon and short label, archiving and deleting on their own tones. */
private val ChatRowAction.reveal: SwipeReveal
    get() =
        SwipeReveal(
            icon = icon,
            label = swipeLabel,
            tone =
                when (this) {
                    ChatRowAction.Archive, ChatRowAction.Unarchive -> SwipeTone.Tertiary
                    ChatRowAction.Delete -> SwipeTone.Error
                    else -> SwipeTone.Secondary
                },
        )

/**
 * What a swipe reveals: the container of the side the row moves away from ([start] or [end]), inset like the row's
 * and with `large` corners, with its icon and label at that side. Decorative: screen readers get the actions on the
 * row. The icons and labels are composed only while the row is away from its place: at rest nothing shows them, and
 * every row of a list would compose and lay out two of each, when a folder's rows replace another's or the list comes
 * back from another screen. So is the threshold's [feedback] ([swipeThresholdFeedback]), whose watch would otherwise
 * start with every row the list brings into view.
 */
@Composable
internal fun SwipeBackground(
    state: SwipeToDismissBoxState,
    start: SwipeReveal?,
    end: SwipeReveal?,
    iconScale: Animatable<Float, AnimationVector1D>,
    feedback: SwipeFeedback,
) {
    val startColors = start?.tone.swipeColors()
    val endColors = end?.tone.swipeColors()
    val swiping by remember(state) { derivedStateOf { state.dismissDirection != SwipeToDismissBoxValue.Settled } }
    val shape = MaterialTheme.shapes.large
    Box(
        Modifier
            .fillMaxSize()
            // Measures the row's whole width, the distance of a full swipe, before the insets.
            .then(
                if (swiping) {
                    Modifier.swipeThresholdFeedback(state, feedback.positionalThreshold, iconScale, feedback.iconSpec)
                } else {
                    Modifier
                },
            ).padding(rowInsets())
            .clearAndSetSemantics {}
            .drawBehind {
                val color =
                    when (state.dismissDirection) {
                        SwipeToDismissBoxValue.StartToEnd -> startColors.container
                        SwipeToDismissBoxValue.EndToStart -> endColors.container
                        SwipeToDismissBoxValue.Settled -> return@drawBehind
                    }
                drawOutline(shape.createOutline(size, layoutDirection, this), color)
            },
    ) {
        if (swiping) {
            start?.let {
                SwipeActionLabel(
                    reveal = it,
                    color = startColors.content,
                    iconScale = iconScale,
                    modifier =
                        Modifier.align(Alignment.CenterStart).revealedBy(state, SwipeToDismissBoxValue.StartToEnd),
                )
            }
            end?.let {
                SwipeActionLabel(
                    reveal = it,
                    color = endColors.content,
                    iconScale = iconScale,
                    modifier =
                        Modifier.align(Alignment.CenterEnd).revealedBy(state, SwipeToDismissBoxValue.EndToStart),
                )
            }
        }
    }
}

@Composable
private fun SwipeActionLabel(
    reveal: SwipeReveal,
    color: Color,
    iconScale: Animatable<Float, AnimationVector1D>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(horizontal = MaterialTheme.spacing.extraLarge),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(reveal.icon),
            contentDescription = null,
            modifier =
                Modifier.graphicsLayer {
                    scaleX = iconScale.value
                    scaleY = iconScale.value
                },
            tint = color,
        )
        Text(
            text = stringResource(reveal.label),
            color = color,
            maxLines = 1,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/** Shows the content only while the row moves away towards [direction]; read in the draw phase. */
private fun Modifier.revealedBy(
    state: SwipeToDismissBoxState,
    direction: SwipeToDismissBoxValue,
): Modifier = graphicsLayer { alpha = if (state.dismissDirection == direction) 1f else 0f }

/** How a swipe's threshold is fed back: where it lies ([positionalThreshold]) and the spring its icon grows on. */
@Immutable
internal data class SwipeFeedback(
    val positionalThreshold: (Float) -> Float,
    val iconSpec: FiniteAnimationSpec<Float>,
)

/**
 * The colors behind a swipe: `tertiaryContainer` for archive and unarchive, `errorContainer` for delete,
 * `secondaryContainer` for the others (none behind a side that does not swipe).
 */
private class SwipeColors(
    val container: Color,
    val content: Color,
)

@Composable
private fun SwipeTone?.swipeColors(): SwipeColors {
    val colors = MaterialTheme.colorScheme
    return when (this) {
        SwipeTone.Tertiary -> SwipeColors(colors.tertiaryContainer, colors.onTertiaryContainer)
        SwipeTone.Error -> SwipeColors(colors.errorContainer, colors.onErrorContainer)
        SwipeTone.Secondary, null -> SwipeColors(colors.secondaryContainer, colors.onSecondaryContainer)
    }
}

/** The short label behind a swipe: "Read", "Unread", "Archive", "Unarchive". */
private val ChatRowAction.swipeLabel: Int
    get() =
        when (this) {
            ChatRowAction.MarkAsRead -> R.string.chat_list_swipe_read
            ChatRowAction.MarkAsUnread -> R.string.chat_list_swipe_unread
            else -> label
        }

/**
 * Follows a swipe of [state] across its [positionalThreshold] (the row's width being the swipe's distance): a
 * `TextHandleMove` haptic each time the user drags it across, and [iconScale] springs to full size past the threshold
 * on [animationSpec], back to its resting size before it.
 *
 * A modifier node rather than an effect in every row (`docs/PERFORMANCE.md` §4): it watches the offset in its own
 * coroutine scope, without recomposing, and only while the row is away from its place ([SwipeBackground]); each swipe
 * starts with the icon at its resting size. While the row springs back after its action ran, the icon shrinks without a
 * haptic. With animations turned off, Compose ends the spring at once.
 */
private fun Modifier.swipeThresholdFeedback(
    state: SwipeToDismissBoxState,
    positionalThreshold: (Float) -> Float,
    iconScale: Animatable<Float, AnimationVector1D>,
    animationSpec: FiniteAnimationSpec<Float>,
): Modifier = this then SwipeThresholdFeedbackElement(state, positionalThreshold, iconScale, animationSpec)

private data class SwipeThresholdFeedbackElement(
    val state: SwipeToDismissBoxState,
    val positionalThreshold: (Float) -> Float,
    val iconScale: Animatable<Float, AnimationVector1D>,
    val animationSpec: FiniteAnimationSpec<Float>,
) : ModifierNodeElement<SwipeThresholdFeedbackNode>() {
    override fun create() = SwipeThresholdFeedbackNode(state, positionalThreshold, iconScale, animationSpec)

    override fun update(node: SwipeThresholdFeedbackNode) =
        node.update(state, positionalThreshold, iconScale, animationSpec)
}

private class SwipeThresholdFeedbackNode(
    private var state: SwipeToDismissBoxState,
    private var positionalThreshold: (Float) -> Float,
    private var iconScale: Animatable<Float, AnimationVector1D>,
    private var animationSpec: FiniteAnimationSpec<Float>,
) : Modifier.Node(),
    LayoutAwareModifierNode,
    CompositionLocalConsumerModifierNode {
    /** The row's width, the distance of a full swipe. */
    private var width by mutableFloatStateOf(0f)
    private var isPastThreshold = false
    private var watch: Job? = null

    fun update(
        state: SwipeToDismissBoxState,
        positionalThreshold: (Float) -> Float,
        iconScale: Animatable<Float, AnimationVector1D>,
        animationSpec: FiniteAnimationSpec<Float>,
    ) {
        val changed = state != this.state || iconScale != this.iconScale
        this.state = state
        this.positionalThreshold = positionalThreshold
        this.iconScale = iconScale
        this.animationSpec = animationSpec
        if (changed && isAttached) watch()
    }

    override fun onAttach() {
        // A swipe that ended before the icon shrank back left it larger.
        coroutineScope.launch { iconScale.snapTo(SWIPE_ICON_RESTING_SCALE) }
        watch()
    }

    override fun onRemeasured(size: IntSize) {
        width = size.width.toFloat()
    }

    private fun watch() {
        watch?.cancel()
        isPastThreshold = false
        watch =
            coroutineScope.launch {
                snapshotFlow { pastThreshold() }.collect { past ->
                    if (past == isPastThreshold) return@collect
                    isPastThreshold = past
                    // Dragged across by the user, not springing back after the action ran.
                    if (state.currentValue == SwipeToDismissBoxValue.Settled) {
                        currentValueOf(LocalHapticFeedback).performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    launch { iconScale.animateTo(if (past) 1f else SWIPE_ICON_RESTING_SCALE, animationSpec) }
                }
            }
    }

    private fun pastThreshold(): Boolean {
        // Settled also stands for no offset yet (before the first layout), which requireOffset would refuse.
        val offset = if (state.dismissDirection == SwipeToDismissBoxValue.Settled) 0f else abs(state.requireOffset())
        return width > 0f && offset >= positionalThreshold(width)
    }
}

/** The size of a swipe's icon before the threshold, against its full size past it. */
internal const val SWIPE_ICON_RESTING_SCALE = 0.6f
