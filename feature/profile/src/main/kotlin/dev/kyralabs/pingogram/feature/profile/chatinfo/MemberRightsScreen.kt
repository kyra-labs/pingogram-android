package dev.kyralabs.pingogram.feature.profile.chatinfo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.component.EmptyState
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.profile.R
import dev.kyralabs.pingogram.feature.profile.ui.profileRowColors
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The rights page of a member: hoists its [MemberRightsViewModel]; once saved it closes through [onSaved], errors are
 * snackbars.
 */
@Composable
internal fun MemberRightsRoute(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MemberRightsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val showDetails = isDebuggable()
    val currentOnSaved by rememberUpdatedState(onSaved)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                MemberRightsEffect.Saved -> {
                    currentOnSaved()
                }

                is MemberRightsEffect.ShowError -> {
                    snackbarHostState.showSnackbar(
                        errorMessage(resources, effect.error, showDetails),
                    )
                }
            }
        }
    }
    MemberRightsScreen(state, viewModel::onEvent, onBack, modifier, snackbarHostState)
}

/**
 * The rights page (`docs/DESIGN_SYSTEM.md` §6 "Group and channel info"), a settings-style page: the member (avatar and
 * name in a segment of their own), then the rights or permissions as a group of switch rows ([RightsGroup]), those the
 * user may not change disabled; a `LinearWavyProgressIndicator` while Telegram answers, then "Dismiss admin" or "Lift
 * restrictions" as a `TextButton` in `error` at the start where they apply and a default `Button` ("Save", "Promote" or
 * "Restrict") at the end, both with the press morph.
 */
@Composable
internal fun MemberRightsScreen(
    state: MemberRightsUiState,
    onEvent: (MemberRightsEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val title =
        when (state) {
            is MemberRightsUiState.Content -> stringResource(state.titleRes())
            else -> stringResource(R.string.chat_info_rights_title)
        }
    InfoPageScaffold(
        title = title,
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) { innerPadding ->
        when (state) {
            MemberRightsUiState.Loading -> {
                PageLoading(innerPadding = innerPadding)
            }

            is MemberRightsUiState.Error -> {
                InfoPageBody(innerPadding) {
                    PageError(state.error) { onEvent(MemberRightsEvent.Retry) }
                }
            }

            is MemberRightsUiState.Content -> {
                InfoPageContent(innerPadding) { RightsContent(state, onEvent) }
            }
        }
    }
}

@Composable
private fun RightsContent(
    state: MemberRightsUiState.Content,
    onEvent: (MemberRightsEvent) -> Unit,
) {
    MemberHeader(state)
    RightsGroup(
        title =
            stringResource(
                if (state.restrict) R.string.chat_info_restrictions_header else R.string.chat_info_rights_header,
            ),
        items = state.switches,
        onToggle = { key, on -> onEvent(MemberRightsEvent.Toggle(key, on)) },
    )
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
        if (state.isSaving) SavingIndicator()
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (state.canRevoke) {
                TextButton(
                    onClick = { onEvent(MemberRightsEvent.Revoke) },
                    shapes = ButtonDefaults.shapes(),
                    enabled = !state.isSaving,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(
                        stringResource(
                            if (state.restrict) {
                                R.string.chat_info_member_unrestrict
                            } else {
                                R.string.chat_info_member_dismiss
                            },
                        ),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { onEvent(MemberRightsEvent.Save) },
                shapes = ButtonDefaults.shapes(),
                enabled = !state.isSaving,
            ) {
                val label =
                    when {
                        !state.isNew -> R.string.chat_info_save
                        state.restrict -> R.string.chat_info_member_restrict
                        else -> R.string.chat_info_member_promote
                    }
                Text(stringResource(label), style = MaterialTheme.typography.labelLargeEmphasized)
            }
        }
    }
}

/** The member whose rights the page sets: their avatar and name in a segment of their own. */
@Composable
private fun MemberHeader(state: MemberRightsUiState.Content) {
    val name = state.name.resolve()
    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(0, 1),
        colors = profileRowColors(),
        leadingContent = {
            Avatar(
                photo = state.photo,
                name = name,
                id = state.avatarId,
                size = MaterialTheme.sizes.avatarSmall,
                placeholderIcon = state.placeholderIcon,
            )
        },
    ) {
        Text(name, style = MaterialTheme.typography.bodyLarge.userContent)
    }
}

/** An indeterminate wavy line while Telegram answers, which screen readers hear. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SavingIndicator(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.chat_info_saving)
    LinearWavyProgressIndicator(modifier.fillMaxWidth().semantics { contentDescription = description })
}

/** A page's delayed loading indicator in the middle of its body. */
@Composable
internal fun PageLoading(innerPadding: PaddingValues) {
    InfoPageBody(innerPadding) {
        val description = stringResource(R.string.chat_info_loading)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            DelayedLoadingIndicator(Modifier.semantics { contentDescription = description })
        }
    }
}

/** Why a page cannot show: an [EmptyState] on `Cookie4Sided` in `errorContainer`, with "Try again" ([onRetry]). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PageError(
    error: DomainError,
    onRetry: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    EmptyState(
        illustration = MaterialShapes.Cookie4Sided,
        icon = UiR.drawable.ic_error,
        title = stringResource(R.string.chat_info_page_error),
        body = errorText(error),
        modifier = Modifier.padding(bottom = MaterialTheme.spacing.large),
        containerColor = colors.errorContainer,
        iconColor = colors.onErrorContainer,
        action = {
            Button(onClick = onRetry, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.profile_retry), style = MaterialTheme.typography.labelLargeEmphasized)
            }
        },
    )
}
