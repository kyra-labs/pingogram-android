package dev.kyralabs.pingogram.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDragHandle
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.MutableThreePaneScaffoldState
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.PaneExpansionAnchor
import androidx.compose.material3.adaptive.layout.PaneExpansionState
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldScope
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.rememberPaneExpansionState
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldNavigator
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import dev.kyralabs.pingogram.R
import dev.kyralabs.pingogram.core.ui.component.ContentSheet
import dev.kyralabs.pingogram.core.ui.component.EmptyState
import dev.kyralabs.pingogram.core.ui.component.HideNavigationBar
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.navigation.ChatPane
import dev.kyralabs.pingogram.feature.chats.navigation.ChatListPane
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The Chats tab as Material's list-detail canonical layout (`docs/ARCHITECTURE.md`, Navigation; `docs/DESIGN_SYSTEM.md`
 * §5, §7): a [NavigableListDetailPaneScaffold] with the chat list ([ChatListPane]) and the conversation of the chat its
 * navigator shows ([ChatPane]), or [NoChatSelected].
 *
 * - **Panes**: the scaffold's directive from the window ([calculatePaneScaffoldDirective], which keeps panes off a
 *   hinge) gives two panes on expanded and wider windows, one on compact and medium ones.
 * - **Opening a chat**: with two panes, a tap shows the chat in the detail pane, in place of the one shown (the
 *   navigator's history never holds more than the list and one chat), and its row looks selected. With one pane,
 *   [onOpenChat] pushes the conversation full screen, as without this layout.
 * - **Resizing**: the navigator keeps the chat. Narrower, the conversation shows alone, as pushed: the floating
 *   navigation bar steps aside and the top bar's back button returns to the list. Wider again, both panes. The
 *   history is saved, so the chat survives rotation, process death and switching tabs.
 * - **Back**: predictive, through the navigator ([BACK_BEHAVIOR]): it closes the conversation, back to the list alone
 *   or to [NoChatSelected] beside it. Selection mode in the list handles back first, and never changes the chat shown.
 * - **Media**: a photo, video or GIF tapped in the conversation opens through [onOpenMedia], which pushes the media
 *   viewer full screen above the whole layout, whichever pane it was tapped in.
 * - **Containers**: with two panes, each is its own rounded container on the window's `surfaceContainer`
 *   ([paneContainer]), and a drag handle between them resizes the list ([PaneDragHandle]) between Material's fixed
 *   widths and an even split. The width is saved with the scaffold, so it survives rotation and process death.
 * - **Motion**: the scaffold's pane motion; panes that stay change their bounds on the slow spatial spring, as large
 *   layout changes do (§5).
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun ChatsListDetail(
    onOpenArchive: () -> Unit,
    onOpenChat: (chatId: Long) -> Unit,
    onOpenMedia: (chatId: Long, messageId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val navigator =
        rememberListDetailPaneScaffoldNavigator<Long>(
            scaffoldDirective = calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2()),
        )
    FollowDirective(navigator)
    val scope = rememberCoroutineScope()
    val shownChatId = navigator.currentDestination?.takeIf { it.pane == ListDetailPaneScaffoldRole.Detail }?.contentKey
    val twoPanes = navigator.scaffoldDirective.maxHorizontalPartitions > 1
    val listVisible = navigator.scaffoldValue[ListDetailPaneScaffoldRole.List] != PaneAdaptedValue.Hidden
    val detailVisible = navigator.scaffoldValue[ListDetailPaneScaffoldRole.Detail] != PaneAdaptedValue.Hidden
    // The conversation shows alone in the floating navigation bar's window, like a pushed one.
    HideNavigationBar(hidden = !listVisible)
    // The chat last shown keeps its pane while the pane leaves, so it does not turn empty as it slides out.
    var lastChatId by remember { mutableStateOf<Long?>(null) }
    SideEffect { if (shownChatId != null) lastChatId = shownChatId }
    val boundsSpec = MaterialTheme.motionScheme.slowSpatialSpec<IntRect>()
    val paneExpansionState =
        rememberPaneExpansionState(keyProvider = navigator.scaffoldValue, anchors = LIST_PANE_ANCHORS)
    NavigableListDetailPaneScaffold(
        navigator = navigator,
        listPane = {
            AnimatedPane(boundsAnimationSpec = boundsSpec, modifier = Modifier.paneContainer(twoPanes, last = false)) {
                ChatListPane(
                    onOpenArchive = onOpenArchive,
                    onOpenChat = { chatId ->
                        if (twoPanes) navigator.show(chatId, scope) else onOpenChat(chatId)
                    },
                    openChatId = shownChatId,
                )
            }
        },
        detailPane = {
            AnimatedPane(boundsAnimationSpec = boundsSpec, modifier = Modifier.paneContainer(twoPanes, last = true)) {
                val chatId = shownChatId ?: lastChatId.takeUnless { detailVisible }
                if (chatId != null) {
                    ChatPane(
                        chatId = chatId,
                        onOpenMedia = onOpenMedia,
                        // Beside the list there is nothing to go back to; a pane sliding out keeps its button.
                        onBack =
                            if (listVisible && shownChatId != null) {
                                null
                            } else {
                                { scope.launch { navigator.navigateBack(BACK_BEHAVIOR) } }
                            },
                    )
                } else {
                    NoChatSelected()
                }
            }
        },
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainer),
        defaultBackBehavior = BACK_BEHAVIOR,
        paneExpansionDragHandle = { state -> PaneDragHandle(state) },
        paneExpansionState = paneExpansionState,
    )
}

/**
 * Each pane's own rounded container beside the other (`docs/DESIGN_SYSTEM.md` §2.2, §7): `extraLarge` corners on the
 * `surfaceContainer` window, above the system navigation bar and the window margin, and the window margin from the
 * window's end. The pane's top bar sits on the window's color, so its content's sheet reads as the container. With one
 * pane, the screen fills the window as when pushed.
 */
@Composable
private fun Modifier.paneContainer(
    twoPanes: Boolean,
    last: Boolean,
): Modifier {
    if (!twoPanes) return this
    val margin = MaterialTheme.spacing.windowMarginMedium
    val shape = MaterialTheme.shapes.extraLarge
    return this
        .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
        .padding(bottom = margin, end = if (last) margin else 0.dp)
        .clip(shape)
}

/**
 * Resizes the list pane between [LIST_PANE_ANCHORS] (`docs/DESIGN_SYSTEM.md` §7): Material's drag handle in the space
 * between the panes, with the draggable's default semantics, so accessibility services can move it between the
 * anchors too.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun ThreePaneScaffoldScope.PaneDragHandle(state: PaneExpansionState) {
    val interactionSource = remember { MutableInteractionSource() }
    VerticalDragHandle(
        modifier =
            Modifier.paneExpansionDraggable(
                state = state,
                minTouchTargetSize = LocalMinimumInteractiveComponentSize.current,
                interactionSource = interactionSource,
            ),
        interactionSource = interactionSource,
    )
}

/** Material's fixed list widths (360 and 412 dp) and an even split, where a drag of the handle settles. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
private val LIST_PANE_ANCHORS =
    listOf(
        PaneExpansionAnchor.Offset.fromStart(360.dp),
        PaneExpansionAnchor.Offset.fromStart(412.dp),
        PaneExpansionAnchor.Proportion(0.5f),
    )

/**
 * Moves the scaffold to the panes of a new directive. The navigator moves it only when it navigates, so a window
 * resized without recreating the activity (one that crosses no size threshold of the app's resources) would keep the
 * old panes; a recreated activity starts with the right ones anyway.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun FollowDirective(navigator: ThreePaneScaffoldNavigator<Long>) {
    LaunchedEffect(navigator, navigator.scaffoldDirective) {
        (navigator.scaffoldState as? MutableThreePaneScaffoldState)?.animateTo(navigator.scaffoldValue)
    }
}

/**
 * Shows chat [chatId] in the detail pane, in place of the chat shown; nothing if it is shown. Both steps change the
 * history before they suspend for their animations, so no frame shows the pane without a chat in between: the second
 * animation takes over from the first.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
private fun ThreePaneScaffoldNavigator<Long>.show(
    chatId: Long,
    scope: CoroutineScope,
) {
    val current = currentDestination
    if (current?.pane == ListDetailPaneScaffoldRole.Detail && current.contentKey == chatId) return
    if (current?.pane == ListDetailPaneScaffoldRole.Detail) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) { navigateBack(BackNavigationBehavior.PopLatest) }
    }
    scope.launch(start = CoroutineStart.UNDISPATCHED) { navigateTo(ListDetailPaneScaffoldRole.Detail, chatId) }
}

/** Back from the conversation leaves the detail pane, whichever pane shows. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
private val BACK_BEHAVIOR = BackNavigationBehavior.PopUntilCurrentDestinationChange

/**
 * The detail pane while no chat is open: an empty state (`docs/DESIGN_SYSTEM.md` §6) on the same sheet as a
 * conversation's, below an empty top bar of a conversation's height, so its container lines up with the list's.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun NoChatSelected(modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            TopAppBar(
                title = {},
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { innerPadding ->
        ContentSheet(innerPadding, Modifier.consumeWindowInsets(innerPadding)) { contentPadding ->
            EmptyState(
                illustration = MaterialShapes.Sunny,
                icon = UiR.drawable.ic_chat_filled,
                title = stringResource(R.string.chats_no_chat_selected_title),
                body = stringResource(R.string.chats_no_chat_selected_body),
                modifier = Modifier.padding(contentPadding),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@PingogramPreviews
@Composable
private fun NoChatSelectedPreview() {
    PingogramPreview { NoChatSelected() }
}
