package dev.kyralabs.pingogram.feature.settings.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.MuteChange
import dev.kyralabs.pingogram.core.domain.model.NotificationScope
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.component.EmptyState
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import java.text.NumberFormat
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

/** The rows under their count, keyed by chat, sliding on the motion scheme's springs as rows leave. */
@Composable
private fun ExceptionsList(
    state: ExceptionsState.Content,
    onEvent: (NotificationExceptionsEvent) -> Unit,
    listState: LazyListState,
    contentPadding: PaddingValues,
) {
    val layoutDirection = LocalLayoutDirection.current
    val spacing = MaterialTheme.spacing
    val style =
        SettingsListRowStyle(
            MaterialTheme.sizes.contentMaxWidth,
            spacing.windowMarginCompact,
            MaterialTheme.motionScheme,
        )
    val numbers = NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0])
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding =
            PaddingValues(
                start = contentPadding.calculateStartPadding(layoutDirection),
                end = contentPadding.calculateEndPadding(layoutDirection),
                top = contentPadding.calculateTopPadding() + spacing.small,
                bottom = contentPadding.calculateBottomPadding() + spacing.extraLarge,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        // The gap between segmented rows; the heading and the end keep their own padding besides.
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        item(key = HEADER, contentType = HEADER) {
            SettingsSectionHeader(
                title =
                    pluralStringResource(
                        R.plurals.settings_exceptions_header,
                        state.items.size,
                        numbers.format(state.items.size),
                    ),
                modifier = Modifier.settingsListRow(this, style),
            )
        }
        itemsIndexed(state.items, key = { _, item -> item.chatId }, contentType = { _, _ -> ROW }) { index, item ->
            ExceptionRow(
                item = item,
                shapes = ListItemDefaults.segmentedShapes(index, state.items.size),
                onClick = { onEvent(NotificationExceptionsEvent.OpenMuteOptions(item.chatId)) },
                onRemove = { onEvent(NotificationExceptionsEvent.Change(item.chatId, MuteChange.UseDefault)) },
                modifier = Modifier.settingsListRow(this, style),
            )
        }
    }
}

/**
 * A chat of the list, a row of its segmented group ([shapes]): its avatar, title and whether it notifies, and a button
 * that removes the exception, or progress while a change is saved. The row opens the chat's mute options.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExceptionRow(
    item: NotificationExceptionItem,
    shapes: ListItemShapes,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        modifier = modifier,
        enabled = !item.isChanging,
        leadingContent = {
            Avatar(
                photo = item.photo,
                name = item.title,
                id = item.chatId,
                size = MaterialTheme.sizes.avatarSmall,
            )
        },
        trailingContent = {
            if (item.isChanging) {
                val changing = stringResource(R.string.settings_exception_changing, item.title)
                LoadingIndicator(Modifier.semantics { contentDescription = changing })
            } else {
                IconButton(onClick = onRemove) {
                    Icon(
                        painter = painterResource(UiR.drawable.ic_close),
                        contentDescription = stringResource(R.string.settings_exception_remove, item.title),
                    )
                }
            }
        },
        supportingContent = { Text(item.status.resolve()) },
    ) {
        Text(item.title)
    }
}

@StringRes
private fun NotificationScope.exceptionsTitleRes(): Int =
    when (this) {
        NotificationScope.PrivateChats -> R.string.settings_exceptions_private_chats
        NotificationScope.GroupChats -> R.string.settings_exceptions_groups
        NotificationScope.ChannelChats -> R.string.settings_exceptions_channels
    }

private const val HEADER = "header"
private const val ROW = "row"

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
