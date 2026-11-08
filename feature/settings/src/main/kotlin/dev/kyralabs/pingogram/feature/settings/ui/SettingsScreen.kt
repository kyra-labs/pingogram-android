package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.component.BottomFade
import dev.kyralabs.pingogram.core.ui.component.LocalExperimentalUi
import dev.kyralabs.pingogram.core.ui.component.LocalIsDemo
import dev.kyralabs.pingogram.core.ui.component.LocalRecentCallsLauncher
import dev.kyralabs.pingogram.core.ui.component.TabHeader
import dev.kyralabs.pingogram.core.ui.component.TabHeaderTitle
import dev.kyralabs.pingogram.core.ui.component.screenContentWindowInsets
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.preview.PingogramVariantPreviews
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.home.AccountSwitcher
import dev.kyralabs.pingogram.feature.settings.ui.home.ExperimentalAccountCard
import dev.kyralabs.pingogram.feature.settings.ui.home.ExperimentalHomeGroups
import dev.kyralabs.pingogram.feature.settings.ui.home.HomeDestination
import dev.kyralabs.pingogram.feature.settings.ui.home.SearchAction
import dev.kyralabs.pingogram.feature.settings.ui.home.SettingsHomeSummaries
import dev.kyralabs.pingogram.feature.settings.ui.home.SettingsHomeViewModel
import dev.kyralabs.pingogram.feature.settings.ui.home.SettingsSearchBar
import dev.kyralabs.pingogram.feature.settings.ui.home.SettingsSearchResults
import dev.kyralabs.pingogram.feature.settings.ui.home.rememberSettingsSearch
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData
import kotlinx.coroutines.launch

/**
 * The Settings tab: hoists [SettingsViewModel] and shows its errors in snackbars. A row calls [onOpenPage] with the
 * page it opens; a result of settings search that is a page above one of them, [onOpenSubPage]. [profileSaved] says
 * that Edit profile just saved the profile, shown in a snackbar once: [onProfileSavedShown] clears it. Beside a page
 * on an expanded window, [selection] says which page is open.
 */
@Composable
internal fun SettingsRoute(
    onOpenPage: (SettingsPage) -> Unit,
    modifier: Modifier = Modifier,
    onOpenSubPage: (SettingsSubPage) -> Unit = {},
    selection: SettingsPageSelection? = null,
    profileSaved: Boolean = false,
    onProfileSavedShown: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
    experimentalUiViewModel: ExperimentalUiViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val experimentalUi by experimentalUiViewModel.enabled.collectAsStateWithLifecycle()
    // Only the experimental look's home shows these values, so only it asks TDLib for them.
    val summaries =
        if (LocalExperimentalUi.current) {
            val homeViewModel: SettingsHomeViewModel = hiltViewModel()
            homeViewModel.summaries.collectAsStateWithLifecycle().value
        } else {
            SettingsHomeSummaries()
        }
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    val scope = rememberCoroutineScope()
    val currentOnProfileSavedShown by rememberUpdatedState(onProfileSavedShown)
    LaunchedEffect(profileSaved) {
        if (!profileSaved) return@LaunchedEffect
        // Cleared first, so it shows once; the snackbar outlives this effect, which clearing restarts.
        currentOnProfileSavedShown()
        scope.launch { snackbarHostState.showSnackbar(resources.getString(R.string.settings_profile_saved)) }
    }
    LaunchedEffect(experimentalUiViewModel) {
        experimentalUiViewModel.errors.collect { error ->
            launch { snackbarHostState.showSnackbar(errorMessage(resources, error, showErrorDetails)) }
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                // Snackbars queue up; later effects must not wait for them.
                is SettingsEffect.ShowError -> {
                    launch { snackbarHostState.showSnackbar(errorMessage(resources, effect.error, showErrorDetails)) }
                }
            }
        }
    }
    SettingsScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onOpenPage = onOpenPage,
        modifier = modifier,
        onOpenSubPage = onOpenSubPage,
        selection = selection,
        snackbarHostState = snackbarHostState,
        experimentalUi = experimentalUi,
        onExperimentalUiChange = experimentalUiViewModel::onCheckedChange,
        summaries = summaries,
    )
}

/**
 * Settings (`REQUIREMENTS.md` FR-S1; `docs/DESIGN_SYSTEM.md` §6): the tabs' large header ([TabHeader], as the Chats and
 * Contacts tabs have it) that collapses as the page scrolls, then the logged-in account's card ([AccountCard], which
 * opens Edit profile) with the device's other accounts and "Add account" below it ([OtherAccountsGroup]), and groups
 * of segmented rows ([SettingsGroup]) without headings, as their rows name them already: General (Appearance,
 * Notifications, Data and storage, Language), Privacy and security, About, and "Log out" on its own, which asks for
 * confirmation first (FR-A8).
 * In the demo ([LocalIsDemo]) the card says "Demo", the other accounts are left out, and "Leave demo" replaces "Log
 * out" (`docs/adr/0034-demo-mode-for-store-review.md`).
 * The page draws behind the floating navigation bar and ends clear of it ([screenContentWindowInsets]). As the list of
 * a list-detail layout, its page rows are selectable, and the [selection]'s open page reads as selected.
 * "Experimental UI" ([experimentalUi], once read; `docs/adr/0035-experimental-ui.md`) is a switch row with a "Beta"
 * label of its own group before "Log out" in the standard look, and the labs card under the account card in the
 * experimental one ([LocalExperimentalUi]).
 */
@Suppress("LongParameterList") // The state, its events, the list-detail's selection and the experimental look's parts.
@Composable
internal fun SettingsScreen(
    state: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
    onOpenPage: (SettingsPage) -> Unit,
    modifier: Modifier = Modifier,
    onOpenSubPage: (SettingsSubPage) -> Unit = {},
    selection: SettingsPageSelection? = null,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    experimentalUi: Boolean? = null,
    onExperimentalUiChange: (Boolean) -> Unit = {},
    summaries: SettingsHomeSummaries = SettingsHomeSummaries(),
) {
    val experimentalLook = LocalExperimentalUi.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val search = rememberSettingsSearch()
    val onOpen = homeOpener(onOpenPage, onOpenSubPage)
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        // The page's color, which the transparent top bar shares.
        containerColor = settingsPageColor(),
        // The tabs' large header, as Chats and Contacts have it (owner decision 2026-11-07); pages keep the medium bar.
        topBar = {
            if (experimentalLook && search.isOpen) {
                SettingsSearchBar(search.query, focus = search.focusOnShow, onClose = search::close)
            } else {
                TabHeader(
                    scrollBehavior = scrollBehavior,
                    title = { style -> TabHeaderTitle(stringResource(R.string.settings_title), style) },
                    actions = { if (experimentalLook) SearchAction(onClick = search::open) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // The floating navigation bar covers the bottom of top-level screens.
        contentWindowInsets = screenContentWindowInsets(),
    ) { innerPadding ->
        Box(Modifier.fillMaxSize()) {
            // Without headings between them the groups sit closer, as in the account menus.
            SettingsContent(innerPadding, groupSpacing = MaterialTheme.spacing.medium) {
                when {
                    experimentalLook && search.isOpen -> {
                        SettingsSearchResults(search.query.text.toString(), summaries, onOpen)
                    }

                    experimentalLook -> {
                        ExperimentalHome(
                            state,
                            onEvent,
                            onOpen,
                            selection,
                            experimentalUi,
                            onExperimentalUiChange,
                            summaries,
                        )
                    }

                    else -> {
                        StandardHome(state, onEvent, onOpenPage, selection, experimentalUi, onExperimentalUiChange)
                    }
                }
            }
            // The page's end fades into its color behind the floating navigation bar, as the Chats tab's list does.
            BottomFade(Modifier.align(Alignment.BottomCenter), color = settingsPageColor())
        }
    }
    if (state.isLogOutDialogOpen) {
        LogOutDialog(
            onConfirm = { onEvent(SettingsEvent.ConfirmLogOut) },
            onDismiss = { onEvent(SettingsEvent.DismissLogOut) },
            isDemo = LocalIsDemo.current,
        )
    }
}

/** Opens where an entry of the experimental home leads: a page, a page above one ([onOpenSubPage]), Recent calls. */
@Composable
private fun homeOpener(
    onOpenPage: (SettingsPage) -> Unit,
    onOpenSubPage: (SettingsSubPage) -> Unit,
): (HomeDestination) -> Unit {
    val onOpenRecentCalls = LocalRecentCallsLauncher.current
    return { destination ->
        when (destination) {
            is HomeDestination.Page -> onOpenPage(destination.page)
            HomeDestination.RecentCalls -> onOpenRecentCalls?.invoke()
            is HomeDestination.SubPage -> onOpenSubPage(destination.subPage)
        }
    }
}

@PingogramPreviews
@PingogramVariantPreviews
@Composable
private fun SettingsScreenPreview() {
    PingogramPreview { SettingsScreen(state = SettingsPreviewData.content, onEvent = {}, onOpenPage = {}) }
}

@Preview(name = "Expanded", widthDp = 840, heightDp = 891)
@Composable
private fun SettingsScreenExpandedPreview() {
    PingogramPreview { SettingsScreen(state = SettingsPreviewData.content, onEvent = {}, onOpenPage = {}) }
}

@PingogramPreviews
@Composable
private fun SettingsScreenOtherAccountsPreview() {
    PingogramPreview { SettingsScreen(state = SettingsPreviewData.withOtherAccounts, onEvent = {}, onOpenPage = {}) }
}

@PingogramPreviews
@Composable
private fun SettingsScreenNoAccountPreview() {
    PingogramPreview { SettingsScreen(state = SettingsUiState(), onEvent = {}, onOpenPage = {}) }
}

@PingogramPreviews
@Composable
private fun SettingsScreenLogOutDialogPreview() {
    PingogramPreview {
        SettingsScreen(
            state = SettingsPreviewData.content.copy(isLogOutDialogOpen = true),
            onEvent = {},
            onOpenPage = {},
        )
    }
}

@PingogramPreviews
@Composable
private fun SettingsScreenLoggingOutPreview() {
    PingogramPreview {
        SettingsScreen(state = SettingsPreviewData.content.copy(isLoggingOut = true), onEvent = {}, onOpenPage = {})
    }
}
