package dev.kyralabs.pingogram.feature.settings.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.SnackbarHostState
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
import dev.kyralabs.pingogram.core.domain.model.NotificationScope
import dev.kyralabs.pingogram.core.ui.component.EmptyState
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The exceptions page of [scope]: hoists its [NotificationExceptionsViewModel] and shows its errors in snackbars.
 * [onBack] leaves the page.
 */
@Composable
internal fun NotificationExceptionsRoute(
    scope: NotificationScope,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotificationExceptionsViewModel =
        hiltViewModel<NotificationExceptionsViewModel, NotificationExceptionsViewModel.Factory>(
            key = "notification-exceptions-$scope",
        ) { it.create(scope) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                // Snackbars queue up; later effects must not wait for them.
                is NotificationExceptionsEffect.ShowError -> {
                    launch { snackbarHostState.showSnackbar(errorMessage(resources, effect.error, showErrorDetails)) }
                }
            }
        }
    }
    NotificationExceptionsScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}

/**
 * The exceptions of a kind of chats (`REQUIREMENTS.md` FR-S1): the chats whose notifications differ from its defaults,
 * each with its avatar, title and whether it notifies, in the rows of one segmented group under a count. A row opens
 * the chat's mute options ([MuteOptionsSheet]); its remove button makes it use the default again. Without any, an
 * empty state says what shows here.
 */
@Composable
internal fun NotificationExceptionsScreen(
    state: NotificationExceptionsUiState,
    onEvent: (NotificationExceptionsEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    listState: LazyListState = rememberLazyListState(),
) {
    SettingsPageFrame(
        title = stringResource(state.scope.exceptionsTitleRes()),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) { innerPadding ->
        SettingsBody(innerPadding) { contentPadding ->
            when (val exceptions = state.exceptions) {
                ExceptionsState.Loading -> {
                    SettingsLoading(
                        description = stringResource(R.string.settings_exceptions_loading),
                        modifier = Modifier.padding(contentPadding),
                    )
                }

                is ExceptionsState.Error -> {
                    SettingsError(
                        error = exceptions.error,
                        onRetry = { onEvent(NotificationExceptionsEvent.Retry) },
                        modifier = Modifier.padding(contentPadding),
                    )
                }

                ExceptionsState.Empty -> {
                    ExceptionsEmpty(Modifier.padding(contentPadding))
                }

                is ExceptionsState.Content -> {
                    ExceptionsList(exceptions, onEvent, listState, contentPadding)
                }
            }
        }
    }
    state.muteOptions?.let { MuteOptionsSheet(item = it, onEvent = onEvent) }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExceptionsEmpty(modifier: Modifier = Modifier) {
    EmptyState(
        illustration = MaterialShapes.Cookie9Sided,
        icon = UiR.drawable.ic_notifications,
        title = stringResource(R.string.settings_exceptions_empty_title),
        body = stringResource(R.string.settings_exceptions_empty_body),
        modifier = modifier,
    )
}

@StringRes
private fun NotificationScope.exceptionsTitleRes(): Int =
    when (this) {
        NotificationScope.PrivateChats -> R.string.settings_exceptions_private_chats
        NotificationScope.GroupChats -> R.string.settings_exceptions_groups
        NotificationScope.ChannelChats -> R.string.settings_exceptions_channels
    }

@PingogramPreviews
@Composable
private fun NotificationExceptionsScreenPreview() {
    PingogramPreview {
        NotificationExceptionsScreen(state = SettingsPreviewData.notificationExceptions, onEvent = {}, onBack = {})
    }
}

@PingogramPreviews
@Composable
private fun NotificationExceptionsScreenChangingPreview() {
    val items = SettingsPreviewData.notificationExceptionItems
    PingogramPreview {
        NotificationExceptionsScreen(
            state =
                SettingsPreviewData.notificationExceptions.copy(
                    exceptions =
                        ExceptionsState.Content(
                            (listOf(items.first().copy(isChanging = true)) + items.drop(1)).toImmutableList(),
                        ),
                ),
            onEvent = {},
            onBack = {},
        )
    }
}

@PingogramPreviews
@Composable
private fun NotificationExceptionsScreenEmptyPreview() {
    PingogramPreview {
        NotificationExceptionsScreen(
            state = NotificationExceptionsUiState(NotificationScope.GroupChats, ExceptionsState.Empty),
            onEvent = {},
            onBack = {},
        )
    }
}

@PingogramPreviews
@Composable
private fun NotificationExceptionsScreenLoadingPreview() {
    PingogramPreview {
        NotificationExceptionsScreen(
            state = NotificationExceptionsUiState(NotificationScope.GroupChats),
            onEvent = {},
            onBack = {},
        )
    }
}

@PingogramPreviews
@Composable
private fun NotificationExceptionsScreenErrorPreview() {
    PingogramPreview {
        NotificationExceptionsScreen(
            state =
                NotificationExceptionsUiState(NotificationScope.GroupChats, ExceptionsState.Error(DomainError.Network)),
            onEvent = {},
            onBack = {},
        )
    }
}
