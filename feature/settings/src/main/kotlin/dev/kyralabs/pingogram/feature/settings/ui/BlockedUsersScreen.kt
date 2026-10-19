package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.component.EmptyState
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData
import kotlinx.coroutines.launch

/**
 * The Blocked users page: hoists [BlockedUsersViewModel] and shows its results in snackbars: who was unblocked, with
 * "Undo", or why something failed. [onBack] leaves the page.
 */
@Composable
internal fun BlockedUsersRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BlockedUsersViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            // Snackbars queue up; later effects must not wait for them.
            launch {
                when (effect) {
                    is BlockedUsersEffect.Unblocked -> {
                        val result =
                            snackbarHostState.showSnackbar(
                                message =
                                    resources.getString(
                                        R.string.settings_unblocked,
                                        effect.name.resolve(resources),
                                    ),
                                actionLabel = resources.getString(R.string.settings_unblock_undo),
                                duration = SnackbarDuration.Long,
                            )
                        if (result == SnackbarResult.ActionPerformed) {
                            viewModel.onEvent(BlockedUsersEvent.Undo(effect.sender))
                        }
                    }

                    is BlockedUsersEffect.ShowError -> {
                        snackbarHostState.showSnackbar(errorMessage(resources, effect.error, showErrorDetails))
                    }
                }
            }
        }
    }
    BlockedUsersScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}

/**
 * Blocked users (`REQUIREMENTS.md` FR-S1): the blocked users and chats, each with its avatar, name and username (or
 * phone number) and a button that unblocks it, in the rows of one segmented group under a count, loading more as the
 * end comes near. With nobody blocked, an empty state says what blocking does; blocking someone happens on their
 * profile, later.
 */
@Composable
internal fun BlockedUsersScreen(
    state: BlockedUsersUiState,
    onEvent: (BlockedUsersEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    listState: LazyListState = rememberLazyListState(),
) {
    SettingsPageFrame(
        title = stringResource(R.string.settings_blocked_users),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) { innerPadding ->
        SettingsBody(innerPadding) { contentPadding ->
            when (state) {
                BlockedUsersUiState.Loading -> {
                    SettingsLoading(
                        description = stringResource(R.string.settings_blocked_users_loading),
                        modifier = Modifier.padding(contentPadding),
                    )
                }

                is BlockedUsersUiState.Error -> {
                    SettingsError(
                        error = state.error,
                        onRetry = { onEvent(BlockedUsersEvent.Retry) },
                        modifier = Modifier.padding(contentPadding),
                    )
                }

                BlockedUsersUiState.Empty -> {
                    BlockedUsersEmpty(Modifier.padding(contentPadding))
                }

                is BlockedUsersUiState.Content -> {
                    BlockedUsersList(state, onEvent, listState, contentPadding)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BlockedUsersEmpty(modifier: Modifier = Modifier) {
    EmptyState(
        illustration = MaterialShapes.Cookie9Sided,
        icon = R.drawable.ic_block,
        title = stringResource(R.string.settings_blocked_users_empty_title),
        body = stringResource(R.string.settings_blocked_users_empty_body),
        modifier = modifier,
    )
}

@PingogramPreviews
@Composable
private fun BlockedUsersScreenPreview() {
    PingogramPreview { BlockedUsersScreen(state = SettingsPreviewData.blockedUsers, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun BlockedUsersScreenLoadingMorePreview() {
    PingogramPreview {
        BlockedUsersScreen(
            state = SettingsPreviewData.blockedUsers.copy(more = MoreBlockedSenders.Loading),
            onEvent = {},
            onBack = {},
        )
    }
}

@PingogramPreviews
@Composable
private fun BlockedUsersScreenEmptyPreview() {
    PingogramPreview { BlockedUsersScreen(state = BlockedUsersUiState.Empty, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun BlockedUsersScreenLoadingPreview() {
    PingogramPreview { BlockedUsersScreen(state = BlockedUsersUiState.Loading, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun BlockedUsersScreenErrorPreview() {
    PingogramPreview {
        BlockedUsersScreen(state = BlockedUsersUiState.Error(DomainError.Network), onEvent = {}, onBack = {})
    }
}
