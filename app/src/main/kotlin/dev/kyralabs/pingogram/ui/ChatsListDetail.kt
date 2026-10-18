package dev.kyralabs.pingogram.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.MutableThreePaneScaffoldState
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntRect
import dev.kyralabs.pingogram.R
import dev.kyralabs.pingogram.core.ui.component.EmptyState
import dev.kyralabs.pingogram.core.ui.component.HideNavigationBar
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
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
 * - **Motion**: the scaffold's pane motion; panes that stay change their bounds on the slow spatial spring, as large
 *   layout changes do (§5).
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun ChatsListDetail(
    onOpenArchive: () -> Unit,
    onOpenChat: (chatId: Long) -> Unit,
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
    NavigableListDetailPaneScaffold(
        navigator = navigator,
        listPane = {
            AnimatedPane(boundsAnimationSpec = boundsSpec) {
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
            AnimatedPane(boundsAnimationSpec = boundsSpec) {
                val chatId = shownChatId ?: lastChatId.takeUnless { detailVisible }
                if (chatId != null) {
                    ChatPane(
                        chatId = chatId,
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
        modifier = modifier,
        defaultBackBehavior = BACK_BEHAVIOR,
    )
}

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

/** The detail pane while no chat is open: an empty state (`docs/DESIGN_SYSTEM.md` §6). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun NoChatSelected(modifier: Modifier = Modifier) {
    Scaffold(modifier) { innerPadding ->
        EmptyState(
            illustration = MaterialShapes.Sunny,
            icon = UiR.drawable.ic_chat_filled,
            title = stringResource(R.string.chats_no_chat_selected_title),
            body = stringResource(R.string.chats_no_chat_selected_body),
            modifier = Modifier.padding(innerPadding),
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@PingogramPreviews
@Composable
private fun NoChatSelectedPreview() {
    PingogramPreview { NoChatSelected() }
}
