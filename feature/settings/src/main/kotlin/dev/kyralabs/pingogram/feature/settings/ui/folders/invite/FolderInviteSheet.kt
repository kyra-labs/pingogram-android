package dev.kyralabs.pingogram.feature.settings.ui.folders.invite

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.folders.folderIconDrawable
import kotlinx.coroutines.launch
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The destination of a shared folder's link (`ChatFolderInviteRoute`): [FolderInviteSheet] for its
 * [FolderInviteViewModel], over the screen the link was opened from. Once the folder is added, the sheet slides away;
 * [onClose] leaves the destination once the sheet closed (added, dismissed, or "Cancel").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FolderInviteSheetRoute(
    onClose: () -> Unit,
    viewModel: FolderInviteViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState =
        rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val scope = rememberCoroutineScope()
    val currentOnClose by rememberUpdatedState(onClose)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                FolderInviteEffect.Added -> {
                    sheetState.hide()
                    currentOnClose()
                }
            }
        }
    }
    FolderInviteSheet(
        state = state,
        onEvent = viewModel::onEvent,
        onCancel = { scope.launch { sheetState.hide() }.invokeOnCompletion { currentOnClose() } },
        onDismiss = { currentOnClose() },
        sheetState = sheetState,
    )
}

/**
 * The sheet of a shared folder (`docs/DESIGN_SYSTEM.md` §6 "Shared folder sheet"): a `ModalBottomSheet` with
 * [FolderInviteContent], expanded at once and scrolling where it does not fit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FolderInviteSheet(
    state: FolderInviteUiState,
    onEvent: (FolderInviteEvent) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        FolderInviteContent(
            state = state,
            onEvent = onEvent,
            onCancel = onCancel,
            modifier = Modifier.verticalScroll(rememberScrollState()),
        )
    }
}

/**
 * What the sheet shows for [state], each step crossfading on the effects springs while the sheet resizes on the
 * default spatial spring: the delayed loading indicator, the folder with its chats, or why the link does not open.
 */
@Composable
internal fun FolderInviteContent(
    state: FolderInviteUiState,
    onEvent: (FolderInviteEvent) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motionScheme = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = state,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(bottom = MaterialTheme.spacing.large),
        transitionSpec = {
            (fadeIn(motionScheme.defaultEffectsSpec()) togetherWith fadeOut(motionScheme.fastEffectsSpec()))
                .using(SizeTransform(clip = false) { _, _ -> motionScheme.defaultSpatialSpec() })
        },
        // A new step crossfades; the same step changing (picks, adding, an error) does not.
        contentKey = { it::class },
        contentAlignment = Alignment.TopCenter,
        label = "folderInviteStep",
    ) { step ->
        Box(Modifier.fillMaxWidth().widthIn(max = MaterialTheme.sizes.contentMaxWidth), Alignment.TopCenter) {
            when (step) {
                FolderInviteUiState.Loading -> {
                    DelayedLoadingIndicator(Modifier.heightIn(min = MaterialTheme.sizes.illustrationMaxSize))
                }

                is FolderInviteUiState.Content -> {
                    FolderInviteDetails(step, onEvent, onCancel)
                }

                is FolderInviteUiState.Failed -> {
                    FolderInviteFailed(step, onEvent, onCancel)
                }
            }
        }
    }
}

/**
 * The folder: its icon on a `Cookie9Sided` badge, what adding it does as the title, what happens to the chats, the new
 * chats to pick and the chats the user is in, then why adding failed, the progress, "Cancel" and "Add folder" / "Join
 * chats".
 */
@Composable
private fun FolderInviteDetails(
    state: FolderInviteUiState.Content,
    onEvent: (FolderInviteEvent) -> Unit,
    onCancel: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.medium)) {
        FolderInviteHeader(state)
        if (state.newChats.isNotEmpty()) {
            FolderInviteChats(
                title =
                    pluralStringResource(
                        R.plurals.folder_invite_new_chats,
                        state.newChats.size,
                        state.newChats.size,
                    ),
                chats = state.newChats,
                onToggle = { onEvent(FolderInviteEvent.TogglePicked(it)) },
                enabled = !state.isAdding,
            )
        }
        if (state.joinedChats.isNotEmpty()) {
            FolderInviteChats(
                title = stringResource(R.string.folder_invite_joined_chats),
                chats = state.joinedChats,
                onToggle = null,
                enabled = false,
            )
        }
        Column(
            Modifier.padding(horizontal = spacing.large),
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            state.addError?.let { AddError(it) }
            if (state.isAdding) {
                val adding = stringResource(R.string.folder_invite_adding)
                LinearWavyProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = adding })
            }
            FolderInviteActions(
                primary = if (state.hasSomethingToAdd) addLabel(state) else null,
                enabled = state.canAdd,
                onPrimary = { onEvent(FolderInviteEvent.Add) },
                secondary =
                    if (state.hasSomethingToAdd) R.string.folder_invite_cancel else R.string.folder_invite_close,
                onSecondary = onCancel,
            )
        }
    }
}

/** The badge, the title (a heading) and what adding does, centred. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FolderInviteHeader(state: FolderInviteUiState.Content) {
    val colors = MaterialTheme.colorScheme
    val badgeSize = MaterialTheme.sizes.headlineBadge
    val name = state.title.bidiIsolated()
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.spacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        Box(
            Modifier
                .size(badgeSize)
                .background(colors.primaryContainer, MaterialShapes.Cookie9Sided.toShape()),
            Alignment.Center,
        ) {
            Icon(
                painter = painterResource(folderIconDrawable(state.iconName)),
                contentDescription = null,
                modifier = Modifier.size(badgeSize / 2),
                tint = colors.onPrimaryContainer,
            )
        }
        Text(
            text =
                when {
                    !state.isAdded -> stringResource(R.string.folder_invite_title_add, name)
                    state.newChats.isNotEmpty() -> stringResource(R.string.folder_invite_title_new_chats, name)
                    else -> stringResource(R.string.folder_invite_title_added, name)
                }.bidiIsolated(),
            modifier = Modifier.semantics { heading() },
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLargeEmphasized,
        )
        Text(
            text =
                stringResource(
                    when {
                        !state.isAdded -> R.string.folder_invite_text_add
                        state.newChats.isNotEmpty() -> R.string.folder_invite_text_new_chats
                        else -> R.string.folder_invite_text_added
                    },
                ),
            textAlign = TextAlign.Center,
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** Why adding failed, in `error`, said by screen readers as it appears. */
@Composable
private fun AddError(error: DomainError) {
    val text = errorMessage(LocalResources.current, error, isDebuggable())
    if (text.isEmpty()) return
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
    )
}

/** Why the link does not open: a `Cookie4Sided` badge on `errorContainer`, the error, "Close" and "Try again". */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FolderInviteFailed(
    state: FolderInviteUiState.Failed,
    onEvent: (FolderInviteEvent) -> Unit,
    onClose: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val badgeSize = MaterialTheme.sizes.headlineBadge
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.spacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
    ) {
        Box(
            Modifier
                .size(badgeSize)
                .background(colors.errorContainer, MaterialShapes.Cookie4Sided.toShape()),
            Alignment.Center,
        ) {
            Icon(
                painter = painterResource(UiR.drawable.ic_link),
                contentDescription = null,
                modifier = Modifier.size(badgeSize / 2),
                tint = colors.onErrorContainer,
            )
        }
        Column(
            Modifier.semantics(mergeDescendants = true) {},
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            Text(
                text = stringResource(R.string.folder_invite_failed_title),
                modifier = Modifier.semantics { heading() },
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineSmallEmphasized,
            )
            Text(
                text = errorMessage(LocalResources.current, state.error, isDebuggable()),
                textAlign = TextAlign.Center,
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (state.canRetry) {
            FolderInviteActions(
                primary = R.string.folder_invite_try_again,
                enabled = true,
                onPrimary = { onEvent(FolderInviteEvent.Retry) },
                secondary = R.string.folder_invite_close,
                onSecondary = onClose,
            )
        } else {
            FolderInviteActions(primary = R.string.folder_invite_close, enabled = true, onPrimary = onClose)
        }
    }
}

/** "Add folder", or "Join chats" for a folder the user has. */
private fun addLabel(state: FolderInviteUiState.Content): Int =
    if (state.isAdded) R.string.folder_invite_join_chats else R.string.folder_invite_add
