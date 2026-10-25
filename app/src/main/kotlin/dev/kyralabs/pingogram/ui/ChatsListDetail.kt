package dev.kyralabs.pingogram.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldRole
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.rememberPaneExpansionState
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.window.core.layout.WindowSizeClass
import dev.kyralabs.pingogram.R
import dev.kyralabs.pingogram.core.ui.component.ContentSheet
import dev.kyralabs.pingogram.core.ui.component.EmptyState
import dev.kyralabs.pingogram.core.ui.component.ExtraPaneDragHandle
import dev.kyralabs.pingogram.core.ui.component.FollowScaffoldDirective
import dev.kyralabs.pingogram.core.ui.component.HideNavigationBar
import dev.kyralabs.pingogram.core.ui.component.ListPaneExpansionAnchors
import dev.kyralabs.pingogram.core.ui.component.PaneDragHandle
import dev.kyralabs.pingogram.core.ui.component.extraPaneEdge
import dev.kyralabs.pingogram.core.ui.component.paneContainer
import dev.kyralabs.pingogram.core.ui.component.rememberExtraPaneWidth
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.trace.OpenChatTrace
import dev.kyralabs.pingogram.core.ui.transition.FullBleedShape
import dev.kyralabs.pingogram.core.ui.transition.ProvideNavAnimatedVisibilityScope
import dev.kyralabs.pingogram.feature.chat.navigation.ChatPane
import dev.kyralabs.pingogram.feature.chats.navigation.ChatListPane
import dev.kyralabs.pingogram.feature.chats.navigation.GlobalSearchState
import dev.kyralabs.pingogram.feature.chats.navigation.SearchPane
import dev.kyralabs.pingogram.navigation.ChatToShow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlin.math.min
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The Chats tab as Material's list-detail canonical layout (`docs/ARCHITECTURE.md`, Navigation; `docs/DESIGN_SYSTEM.md`
 * §5, §7): a [NavigableListDetailPaneScaffold] with the chat list ([ChatListPane]), the conversation of the chat its
 * navigator shows ([ChatPane]) or [NoChatSelected], and on extra-large windows a user's profile or a group's or
 * channel's info page beside the conversation ([ChatsExtraPane]).
 *
 * - **Panes**: the scaffold's directive from the window ([chatsPaneScaffoldDirective], which keeps panes off a hinge)
 *   gives three panes on extra-large windows, two on expanded and large ones, one on compact and medium ones. The
 *   third goes to the extra pane only while it shows a page ([ChatsPaneNavigator]).
 * - **Opening a chat**: with two panes, a tap shows the chat in the detail pane, in place of the one shown (the
 *   navigator's history never holds more than the list, one chat and one page beside it), and its row looks selected.
 *   With one pane, [onOpenChat] pushes the conversation full screen, as without this layout. A chat another screen asks
 *   for ([chatToShow]: a deep link, full-screen search, a profile's "Message") opens the same way, then [onChatShown].
 * - **Profiles and info pages**: with three panes, the conversation's title, a sender or a mention opens the profile
 *   ([onOpenProfile]'s user) or the info page ([onOpenChatInfo]'s chat) in the extra pane beside it, in place of the
 *   one shown; with fewer, they are pushed above the whole layout through those callbacks. A page in the extra pane
 *   opens what it links to in the pane itself (another profile, the members), a chat in the detail pane, media in the
 *   viewer, and the contact editor ([onEditContact]) above the layout.
 * - **Resizing**: the navigator keeps the chat and the page. Narrower, the most recent panes show, as pushed: the
 *   floating navigation bar steps aside and the top bars' back buttons return. Wider again, all of them. The history
 *   is saved, so both survive rotation, process death and switching tabs.
 * - **Back**: predictive, through the navigator ([BACK_BEHAVIOR]): it closes the extra pane first (once the pages
 *   opened in it are popped), then the conversation, back to the list alone or to [NoChatSelected] beside it.
 *   Selection mode in the list handles back first, and never changes the chat shown.
 * - **Media**: a photo, video or GIF tapped in the conversation opens through [onOpenMedia], which pushes the media
 *   viewer full screen above the whole layout, whichever pane it was tapped in. So do the chat's scheduled messages,
 *   from the conversation's bar ([onOpenScheduledMessages]).
 * - **Containers**: with two panes or more, each is its own rounded container on the window's `surfaceContainer`
 *   ([paneContainer], `core:ui`). A drag handle between the list and the conversation resizes the list
 *   ([PaneDragHandle]) between Material's fixed widths and an even split; with three panes, another between the
 *   conversation and the extra pane resizes that ([ExtraPaneDragHandle]). The widths are saved, so they survive
 *   rotation and process death.
 * - **New chat**: the list's "New chat" button, at the bottom end of the list pane, opens [onOpenNewChat], pushed
 *   above the whole layout as the archive is.
 * - **Stories**: a story ring above the chats opens [onOpenStory], the stories viewer full screen above the whole
 *   layout, as the media viewer is.
 * - **Folders**: a folder tab's "Edit folder" opens [onEditFolder] with its folder (null for "All chats"), pushed
 *   above the whole layout as the archive is.
 * - **Search** ([search], `docs/DESIGN_SYSTEM.md` §6 "Global search"): docked, it takes the list pane's place
 *   ([SearchPane]), growing out of the search button ([SearchOrList]). With two panes a chat it found shows in the
 *   detail pane as a tapped one does, and a message opens its conversation there at the message; with one pane (a
 *   window that shrank while search was docked) it fills the window, the floating navigation bar steps aside, and
 *   results push the conversation ([onOpenChat], at a message found). Back closes the conversation beside it first,
 *   then search.
 * - **Motion**: the scaffold's pane motion; panes that stay change their bounds on the slow spatial spring, as large
 *   layout changes do (§5).
 * - **Keyboard** ([PaneKeyboard], `docs/DESIGN_SYSTEM.md` §9): Tab moves from the list pane to the conversation (its
 *   top bar, messages, composer) and back. A chat opened from the list with a hardware keyboard gives its composer the
 *   focus, as Google Messages does on tablets; Esc in the conversation, with nothing in it to close, gives the focus
 *   back to the list, at the row it left, and the chat stays open.
 */
@Suppress("LongMethod") // The scaffold's three panes, its drag handles and what opens in them.
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
    onOpenChatInfo: (chatId: Long) -> Unit = {},
    onEditContact: (userId: Long) -> Unit = {},
    onOpenStory: (chatId: Long) -> Unit = {},
    onEditFolder: ((folderId: Int?) -> Unit)? = null,
    chatToShow: ChatToShow? = null,
    onChatShown: () -> Unit = {},
    onOpenScheduledMessages: (chatId: Long) -> Unit = {},
) {
    val navigator = rememberChatsPaneNavigator(chatsPaneScaffoldDirective(currentWindowAdaptiveInfoV2()))
    FollowScaffoldDirective(navigator)
    val scope = rememberCoroutineScope()
    val current = navigator.currentDestination
    val shown = ChatsPaneContent.of(current?.contentKey)
    val shownChatId = shown?.chatId
    val shownExtra = shown?.takeIf { current?.pane == ListDetailPaneScaffoldRole.Extra }
    val partitions = navigator.scaffoldDirective.maxHorizontalPartitions
    val twoPanes = partitions > 1
    val value = navigator.scaffoldValue
    val listVisible = value[ListDetailPaneScaffoldRole.List] != PaneAdaptedValue.Hidden
    val detailVisible = value[ListDetailPaneScaffoldRole.Detail] != PaneAdaptedValue.Hidden
    val extraVisible = value[ListDetailPaneScaffoldRole.Extra] != PaneAdaptedValue.Hidden
    // The conversation shows alone in the floating navigation bar's window, like a pushed one, and so does search.
    HideNavigationBar(hidden = !listVisible || (search.isDocked && !twoPanes))
    val opener =
        rememberPaneOpener(navigator, scope, twoPanes, threePanes = partitions > 2) {
            PaneCallbacks(onOpenChat, onOpenProfile, onOpenChatInfo)
        }
    ShowChatEffect(chatToShow, opener, onChatShown)
    // What was last shown keeps its pane while the pane leaves, so it does not turn empty as it slides out.
    var lastChatId by remember { mutableStateOf<Long?>(null) }
    var lastExtra by remember { mutableStateOf<ChatsPaneContent?>(null) }
    SideEffect {
        if (shownChatId != null) lastChatId = shownChatId
        if (shownExtra != null) lastExtra = shownExtra
    }
    val keyboard = rememberPaneKeyboard()
    val boundsSpec = MaterialTheme.motionScheme.slowSpatialSpec<IntRect>()
    val paneExpansionState = rememberPaneExpansionState(keyProvider = value, anchors = ListPaneExpansionAnchors)
    val extraWidth = rememberExtraPaneWidth()
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainer)) {
        NavigableListDetailPaneScaffold(
            navigator = navigator,
            listPane = {
                AnimatedPane(
                    boundsAnimationSpec = boundsSpec,
                    modifier = Modifier.paneContainer(twoPanes, last = false),
                ) {
                    Box(Modifier.listPaneFocus(keyboard)) {
                        SearchOrList(
                            search = search,
                            onOpenChat = { keyboard.opening(it, twoPanes) { opener.openChat(it) } },
                            onOpenMessage = { chatId, messageId ->
                                keyboard.opening(chatId, twoPanes) { opener.openMessage(chatId, messageId) }
                            },
                            twoPanes = twoPanes,
                            searchBackEnabled = !navigator.canNavigateBack(BACK_BEHAVIOR),
                        ) {
                            ChatListPane(
                                onOpenArchive = onOpenArchive,
                                onOpenChat = { keyboard.opening(it, twoPanes) { opener.openChat(it) } },
                                onOpenNewChat = onOpenNewChat,
                                onOpenStory = onOpenStory,
                                openChatId = shownChatId,
                                onEditFolder = onEditFolder,
                            )
                        }
                    }
                }
            },
            detailPane = {
                AnimatedPane(
                    boundsAnimationSpec = boundsSpec,
                    modifier = Modifier.paneContainer(twoPanes, last = !extraVisible),
                ) {
                    val chatId = shownChatId ?: lastChatId.takeUnless { detailVisible }
                    if (chatId != null) {
                        ChatPane(
                            chatId = chatId,
                            messageId = opener.messageIn(chatId),
                            onOpenMedia = onOpenMedia,
                            // The chat messages were forwarded to replaces this one, or is pushed with one pane.
                            onOpenChat = opener::openChat,
                            onOpenProfile = opener::openProfile,
                            onOpenChatInfo = opener::openChatInfo,
                            onBack = backUnless(listVisible && shownChatId != null, opener::closeChat),
                            modifier = Modifier.escapeToList(keyboard, enabled = listVisible),
                            focusComposer = keyboard.focusesComposerOf(chatId),
                            onOpenScheduledMessages = onOpenScheduledMessages,
                        )
                    } else {
                        NoChatSelected()
                    }
                }
            },
            extraPane = {
                AnimatedPane(
                    boundsAnimationSpec = boundsSpec,
                    modifier =
                        Modifier
                            .preferredWidth(extraWidth.width)
                            .extraPaneEdge(extraWidth)
                            .paneContainer(twoPanes, last = true),
                ) {
                    val extra = shownExtra ?: lastExtra.takeUnless { extraVisible }
                    if (extra != null) {
                        ChatsExtraPane(
                            content = extra,
                            navigation =
                                ExtraPaneNavigation(
                                    onClose = opener::closeExtra,
                                    onOpenChat = opener::openChat,
                                    onOpenMessage = opener::openMessage,
                                    onOpenMedia = onOpenMedia,
                                    onEditContact = onEditContact,
                                    onLeft = opener::closeChat,
                                ),
                        )
                    }
                }
            },
            defaultBackBehavior = BACK_BEHAVIOR,
            paneExpansionDragHandle = { state -> PaneDragHandle(state) },
            paneExpansionState = paneExpansionState,
        )
        val threeExpanded =
            listOf(ListDetailPaneScaffoldRole.List, ListDetailPaneScaffoldRole.Detail, ListDetailPaneScaffoldRole.Extra)
                .all { value[it] == PaneAdaptedValue.Expanded }
        if (threeExpanded) {
            ExtraPaneDragHandle(extraWidth, navigator.scaffoldDirective.horizontalPartitionSpacerSize)
        }
    }
}

/**
 * The scaffold's directive for the Chats tab (`docs/DESIGN_SYSTEM.md` §7): [calculatePaneScaffoldDirective]'s, which
 * keeps panes off a hinge, with a third pane only on extra-large windows (1600 dp and wider). Material gives large
 * windows (1200 dp) three already, which would leave the conversation too narrow beside a 412 dp list and page.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
internal fun chatsPaneScaffoldDirective(windowAdaptiveInfo: WindowAdaptiveInfo): PaneScaffoldDirective {
    val directive = calculatePaneScaffoldDirective(windowAdaptiveInfo)
    val extraLarge =
        windowAdaptiveInfo.windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXTRA_LARGE_LOWER_BOUND)
    return if (extraLarge) {
        directive
    } else {
        directive.copy(
            maxHorizontalPartitions = min(directive.maxHorizontalPartitions, 2),
        )
    }
}

/**
 * Shows [chatToShow], a chat another screen asked for, through [opener] once, then reports it shown ([onChatShown]).
 */
@Composable
private fun ShowChatEffect(
    chatToShow: ChatToShow?,
    opener: PaneOpener,
    onChatShown: () -> Unit,
) {
    val currentOnChatShown by rememberUpdatedState(onChatShown)
    LaunchedEffect(chatToShow, opener) {
        if (chatToShow == null) return@LaunchedEffect
        val messageId = chatToShow.messageId
        if (messageId == null) opener.openChat(chatToShow.chatId) else opener.openMessage(chatToShow.chatId, messageId)
        currentOnChatShown()
    }
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
 * rounded container; system back closes it while [searchBackEnabled]. Its results open through [onOpenChat] and
 * [onOpenMessage].
 */
@Suppress("LongParameterList") // Search, where its results open, the panes, back, and the list.
@Composable
private fun SearchOrList(
    search: GlobalSearchState,
    onOpenChat: (chatId: Long) -> Unit,
    onOpenMessage: (chatId: Long, messageId: Long) -> Unit,
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
                    onOpenChat = onOpenChat,
                    onOpenMessage = onOpenMessage,
                    shape = if (twoPanes) MaterialTheme.shapes.extraLarge else FullBleedShape,
                    backEnabled = searchBackEnabled,
                )
            }
        } else {
            list()
        }
    }
}

/** What the layout hands up when its panes cannot show something: one pane for a chat, two for a profile or info. */
private class PaneCallbacks(
    val onOpenChat: (chatId: Long, messageId: Long?) -> Unit,
    val onOpenProfile: (userId: Long) -> Unit,
    val onOpenChatInfo: (chatId: Long) -> Unit,
)

/** A [PaneOpener] for [navigator], following the window's panes and the latest [callbacks]. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun rememberPaneOpener(
    navigator: ThreePaneScaffoldNavigator<String>,
    scope: CoroutineScope,
    twoPanes: Boolean,
    threePanes: Boolean,
    callbacks: () -> PaneCallbacks,
): PaneOpener {
    // A message found by search, as [chat id, message id]; saved, so its chat still opens at it after process death.
    val message = rememberSaveable { mutableStateOf<LongArray?>(null) }
    val currentTwoPanes = rememberUpdatedState(twoPanes)
    val currentThreePanes = rememberUpdatedState(threePanes)
    val currentCallbacks = rememberUpdatedState(callbacks())
    return remember(navigator, scope) {
        PaneOpener(navigator, scope, message, currentTwoPanes, currentThreePanes, currentCallbacks)
    }
}

/**
 * Opens what the panes ask for in the panes the window has ([twoPanes], [threePanes]):
 * - a chat in the detail pane in place of the one shown, closing the extra pane, and a message found by search there
 *   too, at that message ([messageIn]); with one pane, pushed full screen;
 * - with three panes, a profile or an info page in the extra pane beside the conversation, in place of the one shown;
 *   with fewer, pushed above the layout.
 *
 * The navigator's history is the list, then at most a chat, then at most a page beside it. Each step changes it
 * before it suspends for its animation, so no frame shows a pane empty in between: the next animation takes over.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
private class PaneOpener(
    private val navigator: ThreePaneScaffoldNavigator<String>,
    private val scope: CoroutineScope,
    private val message: MutableState<LongArray?>,
    private val twoPanes: State<Boolean>,
    private val threePanes: State<Boolean>,
    private val callbacks: State<PaneCallbacks>,
) {
    fun openChat(chatId: Long) = open(chatId, messageId = null)

    fun openMessage(
        chatId: Long,
        messageId: Long,
    ) = open(chatId, messageId)

    private fun open(
        chatId: Long,
        messageId: Long?,
    ) {
        OpenChatTrace.begin(chatId)
        if (!twoPanes.value) {
            message.value = null
            callbacks.value.onOpenChat(chatId, messageId)
            return
        }
        message.value = messageId?.let { longArrayOf(chatId, it) }
        val current = navigator.currentDestination
        val shownChatId = ChatsPaneContent.of(current?.contentKey)?.chatId
        when {
            // The conversation shows already: at the message, if any, as its pane is keyed by it.
            shownChatId == chatId && current?.pane == ListDetailPaneScaffoldRole.Detail -> {
                Unit
            }

            shownChatId == chatId && current?.pane == ListDetailPaneScaffoldRole.Extra -> {
                show(pane = null)
            }

            else -> {
                closeChat()
                show(ListDetailPaneScaffoldRole.Detail, ChatsPaneContent.Chat(chatId).key)
            }
        }
    }

    fun openProfile(userId: Long) =
        openPage({ chatId -> ChatsPaneContent.Profile(chatId, userId) }) { callbacks.value.onOpenProfile(userId) }

    fun openChatInfo(infoChatId: Long) =
        openPage({ chatId -> ChatsPaneContent.ChatInfo(chatId, infoChatId) }) {
            callbacks.value.onOpenChatInfo(infoChatId)
        }

    /** Closes the extra pane, back to the conversation. */
    fun closeExtra() {
        if (navigator.currentDestination?.pane == ListDetailPaneScaffoldRole.Extra) show(pane = null)
    }

    /** The message chat [chatId]'s conversation opens at; null for its usual place. */
    fun messageIn(chatId: Long): Long? = message.value?.takeIf { it[0] == chatId }?.get(1)

    /**
     * Shows the page [content] makes for the chat in the detail pane, in place of the page shown; [orElse] where
     * the window has no third pane, or no chat shows.
     */
    private fun openPage(
        content: (chatId: Long) -> ChatsPaneContent,
        orElse: () -> Unit,
    ) {
        val current = navigator.currentDestination
        val chatId = ChatsPaneContent.of(current?.contentKey)?.chatId
        if (!threePanes.value || chatId == null) return orElse()
        val page = content(chatId)
        if (current?.contentKey == page.key) return
        if (current?.pane == ListDetailPaneScaffoldRole.Extra) show(pane = null)
        show(ListDetailPaneScaffoldRole.Extra, page.key)
    }

    /** Closes the conversation and what is beside it, back to the list. */
    fun closeChat() {
        repeat(MAX_DEPTH) {
            if (navigator.currentDestination?.pane != ListDetailPaneScaffoldRole.List &&
                navigator.canNavigateBack(BackNavigationBehavior.PopLatest)
            ) {
                show(pane = null)
            }
        }
    }

    /** Shows [key] in [pane]; without a pane, goes back one step. */
    private fun show(
        pane: ThreePaneScaffoldRole?,
        key: String? = null,
    ) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            if (pane ==
                null
            ) {
                navigator.navigateBack(BackNavigationBehavior.PopLatest)
            } else {
                navigator.navigateTo(pane, key)
            }
        }
    }

    private companion object {
        /** The history above the list: a chat and a page beside it. */
        const val MAX_DEPTH = 2
    }
}

/** Back from the conversation leaves the detail pane, whichever pane shows; from the extra pane, it closes that. */
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
