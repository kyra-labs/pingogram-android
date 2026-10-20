package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.component.screenContentWindowInsets
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData
import kotlinx.coroutines.launch
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The Settings tab: hoists [SettingsViewModel] and shows its errors in snackbars. A row calls [onOpenPage] with the
 * page it opens. [profileSaved] says that Edit profile just saved the profile, shown in a snackbar once:
 * [onProfileSavedShown] clears it.
 */
@Composable
internal fun SettingsRoute(
    onOpenPage: (SettingsPage) -> Unit,
    modifier: Modifier = Modifier,
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
        snackbarHostState = snackbarHostState,
    )
}

/**
 * Settings (`REQUIREMENTS.md` FR-S1; `docs/DESIGN_SYSTEM.md` §6): a [MediumFlexibleTopAppBar] that collapses as the
 * page scrolls, as the chat list's does, then the logged-in account's card ([AccountCard], which opens Edit profile)
 * and groups of segmented rows ([SettingsGroup]) without headings, as Google's Material 3 Expressive account menus
 * have them: General (Appearance, Notifications, Data and storage), Privacy and security, About, and "Log out" on its
 * own, which asks for confirmation first (FR-A8). The page draws behind the floating navigation bar and ends clear of it
 * ([screenContentWindowInsets]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    state: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
    onOpenPage: (SettingsPage) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        // The page's color, which the transparent top bar shares.
        containerColor = settingsPageColor(),
        topBar = { SettingsTopBar(scrollBehavior) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // The floating navigation bar covers the bottom of top-level screens.
        contentWindowInsets = screenContentWindowInsets(),
    ) { innerPadding ->
        // Without headings between them the groups sit closer, as in the account menus.
        SettingsContent(innerPadding, groupSpacing = MaterialTheme.spacing.medium) {
            state.account?.let { AccountCard(it, onEditProfile = { onOpenPage(SettingsPage.EditProfile) }) }
            PageGroups(onOpenPage)
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

/**
 * The groups of rows that open the other settings pages: General (Appearance, Notifications, Data and storage), Privacy
 * and security, About.
 */
@Composable
private fun PageGroups(onOpenPage: (SettingsPage) -> Unit) {
    SettingsGroup {
        item { shapes ->
            PageItem(
                title = stringResource(R.string.settings_appearance),
                summary = stringResource(R.string.settings_appearance_summary),
                icon = R.drawable.ic_palette,
                shapes = shapes,
                onClick = { onOpenPage(SettingsPage.Appearance) },
            )
        }
        item { shapes ->
            PageItem(
                title = stringResource(R.string.settings_notifications),
                summary = stringResource(R.string.settings_notifications_summary),
                icon = UiR.drawable.ic_notifications,
                shapes = shapes,
                onClick = { onOpenPage(SettingsPage.Notifications) },
            )
        }
        item { shapes ->
            PageItem(
                title = stringResource(R.string.settings_data_storage),
                summary = stringResource(R.string.settings_data_storage_summary),
                icon = R.drawable.ic_storage,
                shapes = shapes,
                onClick = { onOpenPage(SettingsPage.DataStorage) },
            )
        }
    }
    SettingsGroup {
        item { shapes ->
            PageItem(
                title = stringResource(R.string.settings_privacy_security),
                summary = stringResource(R.string.settings_privacy_security_summary),
                icon = R.drawable.ic_shield,
                shapes = shapes,
                onClick = { onOpenPage(SettingsPage.PrivacySecurity) },
            )
        }
    }
    SettingsGroup {
        item { shapes ->
            PageItem(
                title = stringResource(R.string.settings_about),
                summary = stringResource(R.string.settings_about_summary),
                icon = R.drawable.ic_info,
                shapes = shapes,
                onClick = { onOpenPage(SettingsPage.About) },
            )
        }
    }
}

/**
 * The top bar: "Settings" in a transparent [MediumFlexibleTopAppBar] on the page's color, as wide as the page below
 * it, so the title lines up with the groups on wide windows.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsTopBar(scrollBehavior: TopAppBarScrollBehavior) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = MaterialTheme.sizes.contentMaxWidth)) {
            MediumFlexibleTopAppBar(
                title = { SettingsTitle() },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                    ),
                scrollBehavior = scrollBehavior,
            )
        }
    }
}

/**
 * "Settings" in the emphasized variant of the style the bar sets: `headlineMedium` while expanded, `titleLarge`
 * collapsed (`docs/DESIGN_SYSTEM.md` §3.3). The bar composes the title once for each state.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsTitle() {
    val typography = MaterialTheme.typography
    val collapsed = LocalTextStyle.current.fontSize == typography.titleLarge.fontSize
    Text(
        text = stringResource(R.string.settings_title),
        modifier = Modifier.semantics { heading() },
        maxLines = 1,
        style = if (collapsed) typography.titleLargeEmphasized else typography.headlineMediumEmphasized,
    )
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
