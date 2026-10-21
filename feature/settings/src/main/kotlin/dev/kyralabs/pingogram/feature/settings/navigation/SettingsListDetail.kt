package dev.kyralabs.pingogram.feature.settings.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntRect
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import dev.kyralabs.pingogram.core.ui.component.EmptyState
import dev.kyralabs.pingogram.core.ui.component.FollowScaffoldDirective
import dev.kyralabs.pingogram.core.ui.component.HideNavigationBar
import dev.kyralabs.pingogram.core.ui.component.ListPaneExpansionAnchors
import dev.kyralabs.pingogram.core.ui.component.PaneDragHandle
import dev.kyralabs.pingogram.core.ui.component.paneContainer
import dev.kyralabs.pingogram.core.ui.component.showInDetailPane
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.SharedAxisDistance
import dev.kyralabs.pingogram.core.ui.theme.predictiveBackPopEnter
import dev.kyralabs.pingogram.core.ui.theme.predictiveBackPopExit
import dev.kyralabs.pingogram.core.ui.theme.sharedAxisXEnter
import dev.kyralabs.pingogram.core.ui.theme.sharedAxisXExit
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.SettingsPage
import dev.kyralabs.pingogram.feature.settings.ui.SettingsPageSelection
import dev.kyralabs.pingogram.feature.settings.ui.SettingsRoute
import dev.kyralabs.pingogram.feature.settings.ui.settingsPageColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.reflect.KClass
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The Settings tab as Material's list-detail canonical layout (`docs/DESIGN_SYSTEM.md` §7, audit G18): a
 * [NavigableListDetailPaneScaffold] with Settings home ([SettingsRoute]) as the list and the page its navigator shows
 * ([SettingsPagePane]), or [NoSettingsPageSelected], as the detail. It works as the Chats tab's (`ChatsListDetail`,
 * `:app`), with the same pane parts from `core:ui`.
 *
 * - **Panes**: two on expanded and wider windows, one on compact and medium ones ([calculatePaneScaffoldDirective]).
 * - **Opening a page**: with two panes, a row shows its page in the detail pane, in place of the one shown, and reads
 *   as selected ([SettingsPageSelection]). With one pane, [onOpen] pushes the page's route full screen, as before.
 * - **Pages above a page** (Exceptions, Blocked users, 2-step verification and its flows, licenses…): with two panes
 *   they open in the detail pane's own back stack, a `NavHost` of the settings pages ([settingsPages]) that starts at
 *   the page Settings home opened, with the shared axis on X; back returns through them, then closes the page.
 * - **Back**: predictive. The detail pane's `NavHost` pops what was pushed above the page first; then the navigator
 *   ([BACK_BEHAVIOR]) closes the page, back to [NoSettingsPageSelected] beside the list, or to the list alone. Beside
 *   the list the page has no back button ([LocalSettingsBackButton]), as the conversation beside the chat list.
 * - **Resizing**: the navigator keeps the page. Narrower, it shows alone, as pushed, with its back button, and the
 *   floating navigation bar steps aside; wider again, both panes. The navigator's history and the detail pane's back
 *   stack are saved, so the page and what was pushed above it survive rotation and process death.
 * - **Results**: Edit profile in the detail pane closes it once it saved the profile and calls [onProfileSaved], so
 *   Settings home says so ([profileSaved], cleared with [onProfileSavedShown]) as after a pushed Edit profile.
 * - **Containers and motion**: each pane its own rounded container on the window's `surfaceContainer`, a drag handle
 *   between them, panes changing their bounds on the slow spatial spring (§5).
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun SettingsListDetail(
    onOpen: (route: Any) -> Unit,
    profileSaved: Boolean,
    onProfileSavedShown: () -> Unit,
    onProfileSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Pages by name: a key the navigator saves, which still reads after an update that removed a page (as none).
    val navigator =
        rememberListDetailPaneScaffoldNavigator<String>(
            scaffoldDirective = calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2()),
        )
    FollowScaffoldDirective(navigator)
    val scope = rememberCoroutineScope()
    val shownPage =
        navigator.currentDestination
            ?.takeIf { it.pane == ListDetailPaneScaffoldRole.Detail }
            ?.contentKey
            ?.let(::settingsPage)
    val twoPanes = navigator.scaffoldDirective.maxHorizontalPartitions > 1
    val listVisible = navigator.scaffoldValue[ListDetailPaneScaffoldRole.List] != PaneAdaptedValue.Hidden
    val detailVisible = navigator.scaffoldValue[ListDetailPaneScaffoldRole.Detail] != PaneAdaptedValue.Hidden
    // A page shows alone in the floating navigation bar's window, like a pushed one.
    HideNavigationBar(hidden = !listVisible)
    val openPage = rememberPageOpener(navigator, scope, twoPanes, onOpen)
    val closePage: () -> Unit =
        remember(navigator, scope) { { scope.launch { navigator.navigateBack(BACK_BEHAVIOR) } } }
    var lastPage by remember { mutableStateOf<SettingsPage?>(null) }
    SideEffect { if (shownPage != null) lastPage = shownPage }
    val boundsSpec = MaterialTheme.motionScheme.slowSpatialSpec<IntRect>()
    val paneExpansionState =
        rememberPaneExpansionState(keyProvider = navigator.scaffoldValue, anchors = ListPaneExpansionAnchors)
    NavigableListDetailPaneScaffold(
        navigator = navigator,
        listPane = {
            AnimatedPane(boundsAnimationSpec = boundsSpec, modifier = Modifier.paneContainer(twoPanes, last = false)) {
                SettingsRoute(
                    onOpenPage = openPage,
                    selection = if (twoPanes) SettingsPageSelection(shownPage) else null,
                    profileSaved = profileSaved,
                    onProfileSavedShown = onProfileSavedShown,
                )
            }
        },
        detailPane = {
            AnimatedPane(boundsAnimationSpec = boundsSpec, modifier = Modifier.paneContainer(twoPanes, last = true)) {
                DetailContent(
                    // The page last shown stays while its pane slides out, so the pane does not turn empty.
                    page = shownPage ?: lastPage.takeUnless { detailVisible },
                    // Beside the list there is nothing to go back to; a pane sliding out keeps its button.
                    backButton = !listVisible || shownPage == null,
                    onClose = closePage,
                    onProfileSaved = {
                        onProfileSaved()
                        closePage()
                    },
                )
            }
        },
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainer),
        defaultBackBehavior = BACK_BEHAVIOR,
        paneExpansionDragHandle = { state -> PaneDragHandle(state) },
        paneExpansionState = paneExpansionState,
    )
}

/**
 * Opens a page: with [twoPanes], in [navigator]'s detail pane, in place of the page shown; with one pane, pushed full
 * screen by [onOpen] with the page's route.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun rememberPageOpener(
    navigator: ThreePaneScaffoldNavigator<String>,
    scope: CoroutineScope,
    twoPanes: Boolean,
    onOpen: (route: Any) -> Unit,
): (SettingsPage) -> Unit {
    val currentTwoPanes by rememberUpdatedState(twoPanes)
    val currentOnOpen by rememberUpdatedState(onOpen)
    return remember(navigator, scope) {
        { page -> if (currentTwoPanes) navigator.showInDetailPane(page.name, scope) else currentOnOpen(page.route) }
    }
}

/** The detail pane's content: [page] ([SettingsPagePane]), each page with its own back stack, or none open. */
@Composable
private fun DetailContent(
    page: SettingsPage?,
    backButton: Boolean,
    onClose: () -> Unit,
    onProfileSaved: () -> Unit,
) {
    if (page != null) {
        // Each page its own back stack and view models, gone with it.
        key(page.name) { SettingsPagePane(page, backButton, onClose, onProfileSaved) }
    } else {
        NoSettingsPageSelected()
    }
}

/**
 * [page] in the detail pane, with the pages it opens above it: a `NavHost` of its own ([settingsPages]) that starts at
 * [page], whose pages slide in and out on the shared axis on X and leave with the predictive back gesture, as pushed
 * settings pages do. Back from [page] itself calls [onClose]; it shows its back button only with [backButton].
 *
 * Its view models live in a store of the pane's own ([rememberViewModelStoreOwner]): they survive configuration
 * changes, and are cleared with the pane when the page closes, while Settings home stays.
 */
@Composable
private fun SettingsPagePane(
    page: SettingsPage,
    backButton: Boolean,
    onClose: () -> Unit,
    onProfileSaved: () -> Unit,
) {
    val viewModelStoreOwner = rememberViewModelStoreOwner()
    CompositionLocalProvider(
        LocalViewModelStoreOwner provides viewModelStoreOwner,
        LocalSettingsPageWithoutBack provides if (backButton) null else page.route::class,
    ) {
        val navController = rememberNavController()
        val motionScheme = MaterialTheme.motionScheme
        val axisDistance = with(LocalDensity.current) { SharedAxisDistance.roundToPx() }
        NavHost(
            navController = navController,
            startDestination = page.route,
            enterTransition = { sharedAxisXEnter(motionScheme, axisDistance, forward = true) },
            exitTransition = { sharedAxisXExit(motionScheme, axisDistance, forward = true) },
            popEnterTransition = { sharedAxisXEnter(motionScheme, axisDistance, forward = false) },
            popExitTransition = { sharedAxisXExit(motionScheme, axisDistance, forward = false) },
            predictivePopEnterTransition = { predictiveBackPopEnter() },
            predictivePopExitTransition = { predictiveBackPopExit(motionScheme) },
        ) {
            settingsPages(
                onOpen = { route -> navController.navigate(route) { launchSingleTop = true } },
                onBack = {
                    if (navController.previousBackStackEntry !=
                        null
                    ) {
                        navController.popBackStack()
                    } else {
                        onClose()
                    }
                },
                onProfileSaved = onProfileSaved,
                onTwoStepVerificationDone = { navController.popBackStackAfterTwoStepVerification(it) },
            )
        }
    }
}

/**
 * The route of the page the detail pane starts at when that page shows beside Settings home, so it shows no back
 * button ([settingsPages]); null everywhere else.
 */
internal val LocalSettingsPageWithoutBack = staticCompositionLocalOf<KClass<*>?> { null }

/**
 * The detail pane while no page is open: an empty state (`docs/DESIGN_SYSTEM.md` §6) on the color of a settings page,
 * as the chat list's `NoChatSelected` has beside the chats.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun NoSettingsPageSelected(modifier: Modifier = Modifier) {
    Scaffold(modifier = modifier, containerColor = settingsPageColor()) { innerPadding ->
        EmptyState(
            illustration = MaterialShapes.Cookie9Sided,
            icon = UiR.drawable.ic_settings,
            title = stringResource(R.string.settings_no_page_selected_title),
            body = stringResource(R.string.settings_no_page_selected_body),
            modifier = Modifier.padding(innerPadding),
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/** The page of [name], or null for one this version does not know, e.g. from a history saved before an update. */
private fun settingsPage(name: String): SettingsPage? = SettingsPage.entries.firstOrNull { it.name == name }

/** Back from a page leaves the detail pane, whichever pane shows. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
private val BACK_BEHAVIOR = BackNavigationBehavior.PopUntilCurrentDestinationChange

@PingogramPreviews
@Composable
private fun NoSettingsPageSelectedPreview() {
    PingogramPreview { NoSettingsPageSelected() }
}
