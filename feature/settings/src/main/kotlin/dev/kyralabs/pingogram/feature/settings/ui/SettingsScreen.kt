package dev.kyralabs.pingogram.feature.settings.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItem
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
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData
import kotlinx.coroutines.launch

/**
 * The Settings tab: hoists [SettingsViewModel] and shows its errors in snackbars. A row calls [onOpenPage] with the
 * page it opens.
 */
@Composable
internal fun SettingsRoute(
    onOpenPage: (SettingsPage) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
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
 * page scrolls, as the chat list's does, then the logged-in account ([AccountHeader]), the rows that open the other
 * settings pages, and "Log out", which asks for confirmation first (FR-A8). The page draws behind the floating
 * navigation bar and ends clear of it ([screenContentWindowInsets]).
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
        // The top bar's container; the page sits on a sheet below it (ContentSheet).
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = { SettingsTopBar(scrollBehavior) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // The floating navigation bar covers the bottom of top-level screens.
        contentWindowInsets = screenContentWindowInsets(),
    ) { innerPadding ->
        SettingsContent(innerPadding) {
            state.account?.let { AccountHeader(it) }
            SettingsSection(stringResource(R.string.settings_section_general)) {
                PageItem(
                    title = stringResource(R.string.settings_appearance),
                    summary = stringResource(R.string.settings_appearance_summary),
                    icon = R.drawable.ic_palette,
                    onClick = { onOpenPage(SettingsPage.Appearance) },
                )
                PageItem(
                    title = stringResource(R.string.settings_data_storage),
                    summary = stringResource(R.string.settings_data_storage_summary),
                    icon = R.drawable.ic_storage,
                    onClick = { onOpenPage(SettingsPage.DataStorage) },
                )
                PageItem(
                    title = stringResource(R.string.settings_about),
                    summary = stringResource(R.string.settings_about_summary),
                    icon = R.drawable.ic_info,
                    onClick = { onOpenPage(SettingsPage.About) },
                )
            }
            SettingsSection(stringResource(R.string.settings_section_account)) {
                LogOutItem(isLoggingOut = state.isLoggingOut, onClick = { onEvent(SettingsEvent.LogOut) })
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
 * The top bar: "Settings" in a [MediumFlexibleTopAppBar] on the screen's `surfaceContainer`, as wide as the page
 * below it, so the title lines up with the sections on wide windows.
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

/** A row that opens another settings page. */
@Composable
private fun PageItem(
    title: String,
    summary: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
) {
    ListItem(
        onClick = onClick,
        leadingContent = { SettingsItemIcon(icon) },
        supportingContent = { Text(summary) },
        colors = settingsItemColors(),
    ) {
        Text(title)
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
