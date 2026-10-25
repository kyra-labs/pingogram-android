package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.contextmenu.builder.item
import androidx.compose.foundation.text.contextmenu.modifier.appendTextContextMenuComponents
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.rememberSelectionState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Scrim
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import dev.kyralabs.pingogram.core.ui.component.rememberSelectionBack
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.predictiveBackScale
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.content.Playback
import dev.kyralabs.pingogram.feature.chat.ui.content.downloadableFileId
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The floating actions of a long-pressed message (`REQUIREMENTS.md` FR-C6, FR-C7; `docs/DESIGN_SYSTEM.md` §6 "Message
 * actions"), as Google Messages shows them: the [message] lifted where it was ([anchor]) over a [Scrim], with the
 * conversation behind it blurred ([blurBehindMenu], API 31+), its quick reactions above it and its actions below.
 *
 * - **Reactions**: a pill of the quick reactions ([ReactionToggleButton]s, which morph when chosen), scrolling
 *   sideways where they do not fit, ending in the medium icon button that opens the reactions picker
 *   ([MoreReactionsButton]) in a bottom sheet over the menu ([MessageActionsBottomSheet]).
 * - **Menu**: Material 3 Expressive menu groups ([DropdownMenuGroup], [MenuDefaults.groupShape] and
 *   [MenuDefaults.itemShape]): the everyday actions, then reporting and deleting in `error` in a group of their own.
 *   The groups sit in this overlay rather than in a `DropdownMenuPopup` window, so that the message, its reactions and
 *   its menu move as one and the predictive back gesture reaches them.
 * - **Fitting**: the reactions, the message and the menu stay together, aligned with the bubble's side. Where they do
 *   not fit around the message's place in the safe area, they move into view on the motion scheme's default spatial
 *   spring; a menu too tall scrolls, and a message too tall to leave room for it scrolls in its place.
 * - **Text**: a second long press on the lifted message selects its text ([SelectionContainer]), which the platform's
 *   text toolbar copies; where the user may reply to it, the toolbar's "Quote" replies quoting the selection
 *   ([LiftedText]).
 * - **Motion**: the scrim and the blur fade in on the default effects spring as the reactions and the menu grow from
 *   the bubble on the fast spatial spring. A reaction or an action, a tap on the scrim and back fade them out (the
 *   message returning to its place), then send their event; the back gesture is predictive, shrinking them to 90%
 *   ([predictiveBackScale]).
 *
 * Its touch path only: screen readers that explore by touch and short windows get the bottom sheet
 * ([MessageLiftState.lift]).
 */
@Suppress("LongParameterList") // The actions, where the message was and its side, the lift, events, the message.
@Composable
internal fun MessageActionsMenu(
    sheet: MessageActionsSheet,
    anchor: LiftAnchor,
    isOutgoing: Boolean,
    lift: MessageLiftState,
    onEvent: (ChatEvent) -> Unit,
    modifier: Modifier = Modifier,
    message: @Composable () -> Unit,
) {
    val currentOnEvent by rememberUpdatedState(onEvent)
    val scope = rememberCoroutineScope()
    val motionScheme = MaterialTheme.motionScheme
    val motion = remember(lift, scope) { MenuMotion(lift, scope, motionScheme) }
    LaunchedEffect(motion) { motion.open() }
    // However the menu leaves (its own close, or the actions closing elsewhere), nothing stays lifted or blurred.
    DisposableEffect(lift) { onDispose { lift.clear() } }
    val close: (ChatEvent) -> Unit = remember(motion) { { event -> motion.close { currentOnEvent(event) } } }
    val back = rememberSelectionBack(enabled = !motion.closing) { close(ChatEvent.DismissMessageActions) }
    val alignEnd = isOutgoing
    val endIsRight = LocalLayoutDirection.current == LayoutDirection.Ltr
    val originX = if (alignEnd == endIsRight) 1f else 0f
    val focus = rememberMenuFocus(lift)
    Box(modifier.fillMaxSize().menuFocus(focus) { close(ChatEvent.DismissMessageActions) }) {
        Scrim(
            contentDescription = stringResource(R.string.message_actions_close),
            onClick = { close(ChatEvent.DismissMessageActions) },
            alpha = { lift.visibility },
        )
        MenuLayout(
            anchor = anchor,
            alignRight = alignEnd == endIsRight,
            move = { motion.move.value },
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(MaterialTheme.spacing.small)
                    .predictiveBackScale { back.value },
        ) {
            Box(Modifier.popIn(motion, lift, TransformOrigin(originX, 1f))) {
                if (sheet.reactions.isNotEmpty() || sheet.picker != null) {
                    ReactionsBar(
                        sheet = sheet,
                        onReaction = { close(ChatEvent.ToggleReaction(sheet.reactionsMessageId, it.label)) },
                        onMore = { currentOnEvent(ChatEvent.ExpandReactions) },
                    )
                }
            }
            LiftedText(sheet, onQuote = { close(it) }, content = message)
            MenuGroups(
                actions = sheet.actions,
                onAction = { close(it.eventFor(sheet.messageId)) },
                modifier = Modifier.popIn(motion, lift, TransformOrigin(originX, 0f)),
            )
        }
    }
    if (sheet.showsPicker && sheet.picker != null) MessageActionsBottomSheet(sheet, onEvent, onClose = close)
}

/**
 * The lifted message in a [SelectionContainer], scrolling where it is taller than its room. Where [sheet] offers
 * replying, its text selection's menu (the platform's text toolbar) offers "Quote" after its own items, which goes to
 * [onQuote] as [ReplyEvent.Quote] with what is selected, read as the menu opens.
 */
@Composable
private fun LiftedText(
    sheet: MessageActionsSheet,
    onQuote: (ReplyEvent) -> Unit,
    content: @Composable () -> Unit,
) {
    val selection = rememberSelectionState()
    val canQuote = MessageAction.Reply in sheet.actions
    val quoteLabel = stringResource(R.string.quote_action)
    val currentOnQuote by rememberUpdatedState(onQuote)
    val messageId = sheet.messageId
    val quote =
        if (canQuote) {
            Modifier.appendTextContextMenuComponents {
                val selected = selection.selectedTexts.map { it.text }
                if (selected.any { it.isNotBlank() }) {
                    item(QuoteMenuKey, quoteLabel, R.drawable.ic_format_quote) {
                        close()
                        currentOnQuote(ReplyEvent.Quote(messageId, selected))
                    }
                }
            }
        } else {
            Modifier
        }
    SelectionContainer(
        state = selection,
        modifier = Modifier.verticalScroll(rememberScrollState()).testTag(LIFTED_MESSAGE_TAG).then(quote),
        content = content,
    )
}

/** The key of "Quote" in the lifted text's selection menu. */
private data object QuoteMenuKey

/**
 * The bubble of [state]'s open actions where a long press lifted it for the floating menu ([MessageLiftState.anchor]),
 * decided once as they open: not when they open at the reactions picker (a bubble's "+N"), nor for a message no longer
 * in the list, whose actions then show in their sheet.
 */
@Composable
internal fun liftedBubble(
    state: ChatUiState,
    lift: MessageLiftState,
): ChatItem.Bubble? {
    val actions = state.messageActions
    val id = actions?.messageId
    val lifted = remember(id) { actions != null && !actions.showsPicker && lift.anchor?.messageId == id }
    val items = (state.content as? ChatContent.Content)?.items.takeIf { lifted }
    return items?.firstOrNull { it is ChatItem.Bubble && it.id == id } as? ChatItem.Bubble
}

/**
 * The lifted copy of [bubble], as the list shows it (its file, playback and uploads from [state]); nothing in it acts
 * but the text selection around it.
 */
@Composable
internal fun LiftedBubble(
    bubble: ChatItem.Bubble,
    state: ChatUiState,
    resources: BubbleResources,
) {
    val fileState = bubble.content.downloadableFileId?.let(state.fileStates::get)
    val playback = state.playback?.takeIf { it.messageId == bubble.id }?.playback ?: Playback.Stopped
    BubbleRowContent(
        bubble = bubble,
        fileState = fileState,
        playback = playback,
        resources = resources,
        onEvent = {},
        uploads = bubble.uploadsIn(state.uploads),
        isSelected = false,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Places the reactions (first), the message (second) and the menu (third): the message at its [anchor] (in the
 * window), the reactions above it and the menu below it on its side ([alignRight]), all moved by as much as they need
 * to fit, times [move] (0 to 1, read in the placement phase).
 */
@Composable
private fun MenuLayout(
    anchor: LiftAnchor,
    alignRight: Boolean,
    move: () -> Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val gap = MaterialTheme.spacing.small
    Layout(content, modifier) { measurables, constraints ->
        val (barMeasurable, messageMeasurable, menuMeasurable) = measurables
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val gapPx = gap.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val bar = barMeasurable.measure(loose)
        val barSpace = if (bar.height > 0) bar.height + gapPx else 0
        val anchorWidth = anchor.bounds.width.roundToInt()
        // The menu leaves the message at least a share of the height; a taller menu scrolls.
        val reserved = min(anchor.bounds.height.roundToInt(), height / MESSAGE_SHARE)
        val menuMax = (height - barSpace - gapPx - reserved).coerceAtLeast(0)
        val menu = menuMeasurable.measure(loose.copy(maxHeight = menuMax))
        val messageMax = (height - barSpace - gapPx - menu.height).coerceAtLeast(0)
        val message = messageMeasurable.measure(Constraints(anchorWidth, anchorWidth, 0, messageMax))
        layout(width, height) {
            val origin = coordinates?.positionInWindow() ?: Offset.Zero
            val left = (anchor.bounds.left - origin.x).roundToInt()
            val top = (anchor.bounds.top - origin.y).roundToInt()
            val blockTop = top - barSpace
            val blockBottom = top + message.height + gapPx + menu.height
            val fit =
                when {
                    blockTop < 0 -> -blockTop
                    blockBottom > height -> height - blockBottom
                    else -> 0
                }
            val y = top + (fit * move()).roundToInt()

            fun x(childWidth: Int): Int {
                val aligned = if (alignRight) left + anchorWidth - childWidth else left
                return aligned.coerceIn(0, (width - childWidth).coerceAtLeast(0))
            }
            bar.place(x(bar.width), y - barSpace)
            message.place(left, y)
            menu.place(x(menu.width), y + message.height + gapPx)
        }
    }
}

/** The quick reactions in a pill of the menu's color, scrolling sideways, then the button that opens the picker. */
@Composable
private fun ReactionsBar(
    sheet: MessageActionsSheet,
    onReaction: (ReactionOption) -> Unit,
    onMore: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val style = rememberReactionButtonStyle()
    Surface(
        shape = CircleShape,
        color = MenuDefaults.groupStandardContainerColor,
        shadowElevation = MenuDefaults.ShadowElevation,
    ) {
        Row(
            modifier = Modifier.padding(spacing.extraSmall),
            horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall),
        ) {
            Row(
                modifier = Modifier.weight(1f, fill = false).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall),
            ) {
                sheet.reactions.forEach { reaction ->
                    ReactionToggleButton(
                        reaction = reaction,
                        picture = sheet.pictureOf(reaction.label),
                        style = style,
                        onToggle = { onReaction(reaction) },
                    )
                }
            }
            if (sheet.picker != null) MoreReactionsButton(onMore)
        }
    }
}

/**
 * The actions as expressive menu groups: the everyday ones, then reporting and deleting in `error`; scrolls where the
 * window has no room for all of them.
 */
@Composable
private fun MenuGroups(
    actions: ImmutableList<MessageAction>,
    onAction: (MessageAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val groups = remember(actions) { actions.partition { !it.isDestructive }.toList().filter { it.isNotEmpty() } }
    Column(
        // Room below the last group for its shadow, which the scroll would clip.
        modifier =
            modifier
                .width(IntrinsicSize.Max)
                .verticalScroll(rememberScrollState())
                .padding(bottom = MaterialTheme.spacing.small),
        verticalArrangement = Arrangement.spacedBy(MenuDefaults.GroupSpacing),
    ) {
        groups.forEachIndexed { groupIndex, group ->
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(groupIndex, groups.size)) {
                group.forEachIndexed { index, action ->
                    MenuAction(action, MenuDefaults.itemShape(index, group.size).shape) { onAction(action) }
                }
            }
        }
    }
}

/** One action of the menu: its icon and label, in `error` for reporting and deleting. */
@Composable
private fun MenuAction(
    action: MessageAction,
    shape: Shape,
    onClick: () -> Unit,
) {
    val error = MaterialTheme.colorScheme.error
    DropdownMenuItem(
        onClick = onClick,
        text = { Text(stringResource(action.label)) },
        shape = shape,
        modifier = Modifier.fillMaxWidth(),
        leadingIcon = { Icon(painterResource(action.icon), contentDescription = null) },
        colors =
            if (action.isDestructive) {
                MenuDefaults.itemColors(textColor = error, leadingIconColor = error)
            } else {
                MenuDefaults.itemColors()
            },
    )
}

/** Grows from [origin] on the [motion]'s pop and fades with the [lift]'s visibility, in the draw phase. */
private fun Modifier.popIn(
    motion: MenuMotion,
    lift: MessageLiftState,
    origin: TransformOrigin,
): Modifier =
    graphicsLayer {
        val scale = motion.pop.value
        scaleX = scale
        scaleY = scale
        alpha = lift.visibility
        transformOrigin = origin
    }

/**
 * The menu's motion: [open] fades the scrim and the blur in ([MessageLiftState.visibility], default effects spring),
 * grows the reactions and the menu ([pop], fast spatial spring) and moves the block into view ([move], default spatial
 * spring); [close] fades it all out on the fast effects spring, the message returning to its place on the fast spatial
 * spring, then runs what closed it, once.
 */
@Stable
private class MenuMotion(
    private val lift: MessageLiftState,
    private val scope: CoroutineScope,
    private val motionScheme: MotionScheme,
) {
    val pop = Animatable(POP_FROM)
    val move = Animatable(0f)
    var closing by mutableStateOf(false)
        private set
    private var fade: Job? = null

    fun open() {
        fade = scope.launch { fadeTo(1f, motionScheme.defaultEffectsSpec()) }
        scope.launch { pop.animateTo(1f, motionScheme.fastSpatialSpec()) }
        scope.launch { move.animateTo(1f, motionScheme.defaultSpatialSpec()) }
    }

    fun close(then: () -> Unit) {
        if (closing) return
        closing = true
        fade?.cancel()
        scope.launch { move.animateTo(0f, motionScheme.fastSpatialSpec()) }
        scope.launch {
            fadeTo(0f, motionScheme.fastEffectsSpec())
            then()
        }
    }

    private suspend fun fadeTo(
        target: Float,
        spec: AnimationSpec<Float>,
    ) = animate(lift.visibility, target, animationSpec = spec) { value, _ -> lift.visibility = value }
}

/** The floating menu of the user's own message 6 in the private chat, lifted from the middle of the window. */
@PingogramPreviews
@Composable
private fun MessageActionsMenuPreview() {
    PingogramPreview {
        val density = LocalDensity.current
        val window = LocalWindowInfo.current.containerSize
        val spacing = MaterialTheme.spacing
        val sizes = MaterialTheme.sizes
        val lift =
            remember(density, window) {
                with(density) {
                    val left = spacing.medium.toPx()
                    val bounds =
                        Rect(
                            offset = Offset(left, sizes.illustrationMaxSize.toPx()),
                            size = Size(window.width - 2 * left, sizes.headlineBadge.toPx()),
                        )
                    MessageLiftState(LiftAnchor(ChatPreviewData.ownMessageActions.messageId, bounds)).apply {
                        visibility = 1f
                    }
                }
            }
        ChatScreen(state = ChatPreviewData.ownActionsSheet, onEvent = {}, onBack = {}, lift = lift)
    }
}

/** How small the reactions and the menu start, of their size. */
private const val POP_FROM = 0.8f

/** The menu leaves the message at least this fraction of the height: 1 / [MESSAGE_SHARE]. */
private const val MESSAGE_SHARE = 4

/** The lifted copy of the message, for tests. */
internal const val LIFTED_MESSAGE_TAG = "lifted_message"
