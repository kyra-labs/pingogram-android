package dev.kyralabs.pingogram.feature.profile.chatinfo.requests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.profile.R
import dev.kyralabs.pingogram.feature.profile.chatinfo.ConfirmationDialog
import dev.kyralabs.pingogram.feature.profile.chatinfo.InfoPageBody
import dev.kyralabs.pingogram.feature.profile.chatinfo.InfoPageContent
import dev.kyralabs.pingogram.feature.profile.chatinfo.InfoPageScaffold
import dev.kyralabs.pingogram.feature.profile.chatinfo.PageError
import dev.kyralabs.pingogram.feature.profile.chatinfo.PageLoading
import dev.kyralabs.pingogram.feature.profile.chatinfo.SavingIndicator
import dev.kyralabs.pingogram.feature.profile.ui.ProfileGroup
import dev.kyralabs.pingogram.feature.profile.ui.ProfileRow
import dev.kyralabs.pingogram.feature.profile.ui.profileRowColors
import kotlinx.coroutines.launch
import dev.kyralabs.pingogram.core.ui.R as UiR

/** The join requests page: hoists [JoinRequestsViewModel], snackbars for joins and errors, profiles on a tap. */
@Composable
internal fun JoinRequestsRoute(
    onBack: () -> Unit,
    onOpenProfile: (userId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: JoinRequestsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val showDetails = isDebuggable()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is JoinRequestsEffect.OpenProfile -> {
                    onOpenProfile(effect.userId)
                }

                is JoinRequestsEffect.Approved -> {
                    val name = effect.name.resolve(resources)
                    launch { snackbarHostState.showSnackbar(resources.getString(R.string.join_requests_added, name)) }
                }

                is JoinRequestsEffect.ShowError -> {
                    launch { snackbarHostState.showSnackbar(errorMessage(resources, effect.error, showDetails)) }
                }
            }
        }
    }
    JoinRequestsScreen(state, viewModel::onEvent, onBack, modifier, snackbarHostState)
}

/**
 * The requests to join (`docs/DESIGN_SYSTEM.md` §6, "Join requests"), a settings-style page: "Add all" and "Dismiss
 * all" (`TextButton`s, the second in `error`, asking first) while several wait; then the requests as segmented rows
 * (the 40 dp avatar, the name as user content, the bio or the date, an "Add" `FilledTonalButton` and a dismiss
 * `IconButton` at the end, both named with the person); "Load more" below the last while more wait; a footer says
 * where requests come from.
 */
@Composable
internal fun JoinRequestsScreen(
    state: JoinRequestsUiState,
    onEvent: (JoinRequestsEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    InfoPageScaffold(
        title = stringResource(R.string.join_requests),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) { innerPadding ->
        when (state) {
            JoinRequestsUiState.Loading -> {
                PageLoading(innerPadding)
            }

            is JoinRequestsUiState.Error -> {
                InfoPageBody(innerPadding) { PageError(state.error) { onEvent(JoinRequestsEvent.Retry) } }
            }

            is JoinRequestsUiState.Content -> {
                InfoPageContent(innerPadding) { RequestsContent(state, onEvent) }
            }
        }
    }
    if ((state as? JoinRequestsUiState.Content)?.dismissAllDialog == true) {
        ConfirmationDialog(
            title = stringResource(R.string.join_requests_dismiss_all_title),
            text = stringResource(R.string.join_requests_dismiss_all_text),
            confirm = stringResource(R.string.join_requests_dismiss_all_confirm),
            onConfirm = { onEvent(JoinRequestsEvent.ConfirmDismissAll) },
            onDismiss = { onEvent(JoinRequestsEvent.CancelDismissAll) },
        )
    }
}

@Composable
private fun RequestsContent(
    state: JoinRequestsUiState.Content,
    onEvent: (JoinRequestsEvent) -> Unit,
) {
    if (state.isProcessingAll) SavingIndicator()
    if (state.requests.size > 1) AllButtons(state, onEvent)
    val rows =
        state.requests.map { item ->
            ProfileRow(item.userId.toString()) { shapes ->
                RequestRow(item, shapes, enabled = item.userId !in state.busy && !state.isProcessingAll, onEvent)
            }
        }
    if (rows.isEmpty()) {
        Text(
            text = stringResource(R.string.join_requests_none),
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
    } else {
        ProfileGroup(rows)
    }
    if (state.hasMore) MoreRow(state, onEvent)
    Text(
        text =
            stringResource(
                if (state.isChannel) R.string.join_requests_footer_channel else R.string.join_requests_footer_group,
            ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun AllButtons(
    state: JoinRequestsUiState.Content,
    onEvent: (JoinRequestsEvent) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(
            onClick = { onEvent(JoinRequestsEvent.DismissAll) },
            enabled = !state.isProcessingAll,
            shapes = ButtonDefaults.shapes(),
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Text(stringResource(R.string.join_requests_dismiss_all))
        }
        TextButton(
            onClick = { onEvent(JoinRequestsEvent.ApproveAll) },
            enabled = !state.isProcessingAll,
            shapes = ButtonDefaults.shapes(),
        ) {
            Text(stringResource(R.string.join_requests_add_all))
        }
    }
}

/** A request: who, their bio or the date, then "Add" and dismiss; a tap on the row opens their profile. */
@Composable
private fun RequestRow(
    item: JoinRequestItem,
    shapes: ListItemShapes,
    enabled: Boolean,
    onEvent: (JoinRequestsEvent) -> Unit,
) {
    val name = item.name.resolve()
    SegmentedListItem(
        onClick = { onEvent(JoinRequestsEvent.Open(item.userId)) },
        shapes = shapes,
        colors = profileRowColors(),
        leadingContent = {
            Avatar(photo = item.photo, name = name, id = item.userId, size = MaterialTheme.sizes.avatarSmall)
        },
        supportingContent = {
            Text(
                text = item.bio.ifBlank { stringResource(R.string.join_requests_date, item.date) },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.userContent,
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val add = stringResource(R.string.join_requests_add_named, name)
                FilledTonalButton(
                    onClick = { onEvent(JoinRequestsEvent.Approve(item.userId)) },
                    enabled = enabled,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.semantics { contentDescription = add },
                ) {
                    Text(stringResource(R.string.join_requests_add))
                }
                IconButton(
                    onClick = { onEvent(JoinRequestsEvent.Dismiss(item.userId)) },
                    enabled = enabled,
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(
                        painter = painterResource(UiR.drawable.ic_close),
                        contentDescription = stringResource(R.string.join_requests_dismiss_named, name),
                    )
                }
            }
        },
    ) {
        Text(
            name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge.userContent,
        )
    }
}

@Composable
private fun MoreRow(
    state: JoinRequestsUiState.Content,
    onEvent: (JoinRequestsEvent) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        if (state.pageError != null) {
            Text(
                text = stringResource(R.string.join_requests_more_error),
                modifier = Modifier.align(Alignment.CenterVertically).weight(1f),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        TextButton(
            onClick = { onEvent(JoinRequestsEvent.LoadMore) },
            enabled = !state.loadingMore,
            shapes = ButtonDefaults.shapes(),
        ) {
            Text(
                stringResource(
                    if (state.pageError !=
                        null
                    ) {
                        R.string.join_requests_retry
                    } else {
                        R.string.join_requests_more
                    },
                ),
            )
        }
    }
}
