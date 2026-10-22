package dev.kyralabs.pingogram.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.rememberPaneExpansionState
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldNavigator
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntRect
import dev.kyralabs.pingogram.R
import dev.kyralabs.pingogram.core.ui.component.ContentSheet
import dev.kyralabs.pingogram.core.ui.component.EmptyState
import dev.kyralabs.pingogram.core.ui.component.FollowScaffoldDirective
import dev.kyralabs.pingogram.core.ui.component.HideNavigationBar
import dev.kyralabs.pingogram.core.ui.component.ListPaneExpansionAnchors
import dev.kyralabs.pingogram.core.ui.component.PaneDragHandle
import dev.kyralabs.pingogram.core.ui.component.paneContainer
import dev.kyralabs.pingogram.core.ui.component.showInDetailPane
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.transition.FullBleedShape
import dev.kyralabs.pingogram.core.ui.transition.ProvideNavAnimatedVisibilityScope
import dev.kyralabs.pingogram.feature.chat.navigation.ChatPane
import dev.kyralabs.pingogram.feature.chats.navigation.ChatListPane
import dev.kyralabs.pingogram.feature.chats.navigation.GlobalSearchState
import dev.kyralabs.pingogram.feature.chats.navigation.SearchPane
import kotlinx.coroutines.CoroutineScope
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
 *   viewer full screen above the whole layout, whichever pane it was tapped in; a profile opens through
 *   [onOpenProfile], pushed above the whole layout too.
 * - **Containers**: with two panes, each is its own rounded container on the window's `surfaceContainer`
 *   ([paneContainer], `core:ui`), and a drag handle between them resizes the list ([PaneDragHandle]) between
 *   Material's fixed widths and an even split. The width is saved with the scaffold, so it survives rotation and
 *   process death.
 * - **New chat**: the list's "New chat" button, at the bottom end of the list pane, opens [onOpenNewChat], pushed
 *   above the whole layout as the archive is.
 * - **Search** ([search], `docs/DESIGN_SYSTEM.md` §6 "Global search"): docked, it takes the list pane's place
 *   ([SearchPane]), growing out of the search button ([SearchOrList]). With two panes a chat it found shows in the
 *   detail pane as a tapped one does, and a message opens its conversation there at the message; with one pane (a
 *   window that shrank while search was docked) it fills the window, the floating navigation bar steps aside, and
 *   results push the conversation ([onOpenChat], at a message found). Back closes the conversation beside it first,
 *   then search.
 * - **Motion**: the scaffold's pane motion; panes that stay change their bounds on the slow spatial spring, as large
 *   layout changes do (§5).
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun ChatsListDetail(
    search: GlobalSearchState,
    onOpenArchive: () -> Unit,
    onOpenChat: (chatId: Long, messageId: Long?) -> Unit,
    onOpenMedia: (chatId: Long, messageId: Long) -> Unit,
    onOpenNewChat: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenProfile: (userId: Long) -> Unit = {},
) {
    val navigator =
        rememberListDetailPaneScaffoldNavigator<Long>(
            scaffoldDirective = calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2()),
        )
    FollowScaffoldDirective(navigator)
    val scope = rememberCoroutineScope()
    val shownChatId = navigator.currentDestination?.takeIf { it.pane == ListDetailPaneScaffoldRole.Detail }?.contentKey
    val twoPanes = navigator.scaffoldDirective.maxHorizontalPartitions > 1
    val listVisible = navigator.scaffoldValue[ListDetailPaneScaffoldRole.List] != PaneAdaptedValue.Hidden
    val detailVisible = navigator.scaffoldValue[ListDetailPaneScaffoldRole.Detail] != PaneAdaptedValue.Hidden
    // The conversation shows alone in the floating navigation bar's window, like a pushed one, and so does search.
    HideNavigationBar(hidden = !listVisible || (search.isDocked && !twoPanes))
    val opener = rememberDetailOpener(navigator, scope, twoPanes, onOpenChat)
    // The chat last shown keeps its pane while the pane leaves, so it does not turn empty as it slides out.
    var lastChatId by remember { mutableStateOf<Long?>(null) }
    SideEffect { if (shownChatId != null) lastChatId = shownChatId }
    val boundsSpec = MaterialTheme.motionScheme.slowSpatialSpec<IntRect>()
    val paneExpansionState =
        rememberPaneExpansionState(keyProvider = navigator.scaffoldValue, anchors = ListPaneExpansionAnchors)
    NavigableListDetailPaneScaffold(
        navigator = navigator,
        listPane = {
            AnimatedPane(boundsAnimationSpec = boundsSpec, modifier = Modifier.paneContainer(twoPanes, last = false)) {
                SearchOrList(search, opener, twoPanes, !navigator.canNavigateBack(BACK_BEHAVIOR)) {
                    ChatListPane(
                        onOpenArchive = onOpenArchive,
                        onOpenChat = opener::openChat,
                        onOpenNewChat = onOpenNewChat,
                        openChatId = shownChatId,
                    )
                }
            }
        },
        detailPane = {
            AnimatedPane(boundsAnimationSpec = boundsSpec, modifier = Modifier.paneContainer(twoPanes, last = true)) {
                val chatId = shownChatId ?: lastChatId.takeUnless { detailVisible }
                if (chatId != null) {
                    ChatPane(
                        chatId = chatId,
                        messageId = opener.messageIn(chatId),
                        onOpenMedia = onOpenMedia,
                        // The chat messages were forwarded to replaces this one, or is pushed with one pane.
                        onOpenChat = opener::openChat,
                        onOpenProfile = onOpenProfile,
                        onBack =
                            backUnless(listVisible && shownChatId != null) {
                                scope.launch { navigator.navigateBack(BACK_BEHAVIOR) }
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
 * The detail pane's back button: [back], except [besideList], where there is nothing to go back to; a pane sliding out
 * keeps its button.
 */
private fun backUnless(
    besideList: Boolean,
    back: () -> Unit,
): (() -> Unit)? = if (besideList) null else back

/**
 * The list pane's content: the chat [list], or docked search ([SearchPane]) while [search] is docked, crossfading on
 * the effects springs. Search gets this crossfade's animated visibility scope, so the search button grows into it; the
 * list keeps the destination's, which its "New chat" button grows out of. With [twoPanes] search sits in the pane's
 * rounded container; system back closes it while [searchBackEnabled]. Its results open through [opener].
 */
@Composable
private fun SearchOrList(
    search: GlobalSearchState,
    opener: DetailOpener,
    twoPanes: Boolean,
    searchBackEnabled: Boolean,
    list: @Composable () -> Unit,
) {
    val motionScheme = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = search.isDocked,
        transitionSpec = {
            fadeIn(motionScheme.defaultEffectsSpec()) togetherWith fadeOut(motionScheme.fastEffectsSpec())
        },
        label = "dockedSearch",
    ) { docked ->
        if (docked) {
            ProvideNavAnimatedVisibilityScope(this) {
                SearchPane(
                    state = search,
                    onOpenChat = opener::openChat,
                    onOpenMessage = opener::openMessage,
                    shape = if (twoPanes) MaterialTheme.shapes.extraLarge else FullBleedShape,
                    backEnabled = searchBackEnabled,
                )
            }
        } else {
            list()
        }
    }
}

/** A [DetailOpener] for [navigator], following the window's panes ([twoPanes]) and the latest callbacks. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun rememberDetailOpener(
    navigator: ThreePaneScaffoldNavigator<Long>,
    scope: CoroutineScope,
    twoPanes: Boolean,
    onOpenChat: (chatId: Long, messageId: Long?) -> Unit,
): DetailOpener {
    // A message found by search, as [chat id, message id]; saved, so its chat still opens at it after process death.
    val message = rememberSaveable { mutableStateOf<LongArray?>(null) }
    val currentTwoPanes = rememberUpdatedState(twoPanes)
    val currentOnOpenChat = rememberUpdatedState(onOpenChat)
    return remember(navigator, scope) { DetailOpener(navigator, scope, message, currentTwoPanes, currentOnOpenChat) }
}

/**
 * Opens what the list pane asks for: with two panes, a chat in the detail pane in place of the one shown, and a
 * message found by search there too, at that message ([messageIn]); with one pane, pushed full screen ([onOpenChat],
 * with the message, if any).
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
private class DetailOpener(
    private val navigator: ThreePaneScaffoldNavigator<Long>,
    private val scope: CoroutineScope,
    private val message: MutableState<LongArray?>,
    private val twoPanes: State<Boolean>,
    private val onOpenChat: State<(chatId: Long, messageId: Long?) -> Unit>,
) {
    fun openChat(chatId: Long) {
        message.value = null
        if (twoPanes.value) navigator.showInDetailPane(chatId, scope) else onOpenChat.value(chatId, null)
    }

    fun openMessage(
        chatId: Long,
        messageId: Long,
    ) {
        if (twoPanes.value) {
            message.value = longArrayOf(chatId, messageId)
            navigator.showInDetailPane(chatId, scope)
        } else {
            onOpenChat.value(chatId, messageId)
        }
    }

    /** The message chat [chatId]'s conversation opens at; null for its usual place. */
    fun messageIn(chatId: Long): Long? = message.value?.takeIf { it[0] == chatId }?.get(1)
}

/** Back from the conversation leaves the detail pane, whichever pane shows. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
private val BACK_BEHAVIOR = BackNavigationBehavior.PopUntilCurrentDestinationChange

/**
 * The detail pane while no chat is open: an empty state (`docs/DESIGN_SYSTEM.md` §6) on the same sheet as a
 * conversation's, below an empty top bar of a conversation's height, so its container lines up with the list's.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
