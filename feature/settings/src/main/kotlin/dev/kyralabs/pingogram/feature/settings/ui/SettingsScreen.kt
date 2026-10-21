package dev.kyralabs.pingogram.feature.settings.ui

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.component.screenContentWindowInsets
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData
import kotlinx.coroutines.launch

/**
 * The Settings tab: hoists [SettingsViewModel] and shows its errors in snackbars. A row calls [onOpenPage] with the
 * page it opens. [profileSaved] says that Edit profile just saved the profile, shown in a snackbar once:
 * [onProfileSavedShown] clears it. Beside a page on an expanded window, [selection] says which page is open.
 */
@Composable
internal fun SettingsRoute(
    onOpenPage: (SettingsPage) -> Unit,
    modifier: Modifier = Modifier,
    selection: SettingsPageSelection? = null,
    profileSaved: Boolean = false,
    onProfileSavedShown: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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
        selection = selection,
        snackbarHostState = snackbarHostState,
    )
}

/**
 * Settings (`REQUIREMENTS.md` FR-S1; `docs/DESIGN_SYSTEM.md` §6): a medium flexible top bar ([SettingsTopBar]) that
 * collapses as the page scrolls, then the logged-in account's card ([AccountCard], which opens Edit profile)
 * and groups of segmented rows ([SettingsGroup]) without headings, as Google's Material 3 Expressive account menus
 * have them: General (Appearance, Notifications, Data and storage, Language), Privacy and security, About, and
 * "Log out" on its own, which asks for confirmation first (FR-A8). The page draws behind the floating navigation bar
 * and ends clear of it ([screenContentWindowInsets]). As the list of a list-detail layout, its page rows are
 * selectable, and the [selection]'s open page reads as selected.
 */
@Composable
internal fun SettingsScreen(
    state: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
    onOpenPage: (SettingsPage) -> Unit,
    modifier: Modifier = Modifier,
    selection: SettingsPageSelection? = null,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        // The page's color, which the transparent top bar shares.
        containerColor = settingsPageColor(),
        topBar = { SettingsTopBar(title = stringResource(R.string.settings_title), scrollBehavior = scrollBehavior) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // The floating navigation bar covers the bottom of top-level screens.
        contentWindowInsets = screenContentWindowInsets(),
    ) { innerPadding ->
        // Without headings between them the groups sit closer, as in the account menus.
        SettingsContent(innerPadding, groupSpacing = MaterialTheme.spacing.medium) {
            state.account?.let {
                AccountCard(
                    account = it,
                    onEditProfile = { onOpenPage(SettingsPage.EditProfile) },
                    selected = selection?.let { selection -> selection.openPage == SettingsPage.EditProfile },
                )
            }
            PageGroups(onOpenPage, selection)
            SettingsGroup {
                item { shapes ->
                    LogOutItem(
                        isLoggingOut = state.isLoggingOut,
                        shapes = shapes,
                        onClick = { onEvent(SettingsEvent.LogOut) },
                    )
                }
            }
        }
    }
    if (state.isLogOutDialogOpen) {
        LogOutDialog(
            onConfirm = { onEvent(SettingsEvent.ConfirmLogOut) },
            onDismiss = { onEvent(SettingsEvent.DismissLogOut) },
        )
    }
}

@PingogramPreviews
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
